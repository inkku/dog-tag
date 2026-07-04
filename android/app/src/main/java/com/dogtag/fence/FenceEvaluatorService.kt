package com.dogtag.fence

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dogtag.DogTagApplication
import com.dogtag.ble.BleTagReading
import com.dogtag.ble.TagScanner
import com.dogtag.data.model.AlertAction
import com.dogtag.data.model.AlertTrigger
import com.dogtag.data.model.Dog
import com.dogtag.data.model.Fence
import com.dogtag.data.model.LocationSample
import com.dogtag.data.model.LocationSource
import com.dogtag.data.model.Tag
import com.dogtag.data.model.TagType
import com.dogtag.notifications.FOREGROUND_SERVICE_CHANNEL_ID
import com.dogtag.notifications.showFenceAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service: scans for the phone's known BLE tags continuously and
 * evaluates them against configured fences in real time (no round-trip to the
 * backend needed for this fast path - see [BleFenceEvaluator]). Falls back to
 * asking the backend to ring a tag over FMDN, and posts a best-effort
 * LocationSample so the map/history has *something* for BLE-only dogs.
 */
class FenceEvaluatorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null
    private var scanJob: Job? = null

    private val repository get() = (application as DogTagApplication).repository

    @Volatile private var dogs: List<Dog> = emptyList()
    @Volatile private var tags: List<Tag> = emptyList()
    @Volatile private var fences: List<Fence> = emptyList()
    @Volatile private var rulesByFence: Map<Int, List<com.dogtag.data.model.AlertRule>> = emptyMap()
    @Volatile private var phoneLat: Double? = null
    @Volatile private var phoneLon: Double? = null

    // Debounce: don't re-fire the same (dogId, fenceId, status) more than once per cooldown window.
    private val lastFired = mutableMapOf<Triple<Int, Int, FenceStatus>, Long>()
    private val cooldownMs = 5 * 60 * 1000L

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        startLocationUpdates()
        refreshJob = scope.launch { refreshLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (scanJob == null) {
            scanJob = scope.launch { scanLoop() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun buildForegroundNotification() =
        NotificationCompat.Builder(this, FOREGROUND_SERVICE_CHANNEL_ID)
            .setContentTitle("Dog Tag")
            .setContentText("Monitoring virtual fences")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationManager = getSystemService(LocationManager::class.java) ?: return
        val listener = android.location.LocationListener { location ->
            phoneLat = location.latitude
            phoneLon = location.longitude
        }
        runCatching {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                phoneLat = it.latitude
                phoneLon = it.longitude
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5_000L, 5f, listener)
        }
    }

    private suspend fun refreshLoop() {
        while (true) {
            runCatching {
                dogs = repository.listDogs()
                tags = repository.listTags()
                fences = repository.listFences()
                rulesByFence = fences.mapNotNull { it.id }.associateWith { repository.listRules(it) }
            }
            delay(60_000L)
        }
    }

    private suspend fun scanLoop() {
        val scanner = TagScanner(applicationContext)
        while (true) {
            val knownMacs = tags.filter { it.type == TagType.BLE && it.macAddress != null }
                .mapNotNull { it.macAddress }
                .toSet()
            if (knownMacs.isEmpty()) {
                delay(10_000L)
                continue
            }
            runCatching {
                scanner.scanKnown(knownMacs).collect { reading -> onReading(reading) }
            }
            delay(5_000L) // scan crashed/stopped (e.g. Bluetooth toggled off) - back off and retry
        }
    }

    private suspend fun onReading(reading: BleTagReading) {
        val tag = tags.find { it.macAddress == reading.macAddress } ?: return
        val dogId = tag.dogId ?: return
        val dog = dogs.find { it.id == dogId } ?: return
        val lat = phoneLat
        val lon = phoneLon ?: return
        if (lat == null) return

        val applicableFences = fences.filter { it.dogId == dogId || it.dogId == null }
        for (fence in applicableFences) {
            if (fence.id == null) continue
            val result = BleFenceEvaluator.evaluate(lat, lon, reading.distanceM, fence)
            handleFenceResult(dog, fence, result)
        }

        tag.id?.let { tagId ->
            runCatching {
                repository.submitLocation(
                    LocationSample(
                        tagId = tagId,
                        lat = lat,
                        lon = lon,
                        accuracyM = reading.distanceM,
                        source = LocationSource.BLE_PROXIMITY,
                    )
                )
            }
        }
    }

    private suspend fun handleFenceResult(dog: Dog, fence: Fence, result: FenceCheckResult) {
        val dogId = dog.id ?: return
        val fenceId = fence.id ?: return
        val trigger = when (result.status) {
            FenceStatus.OUTSIDE -> AlertTrigger.EXIT
            FenceStatus.APPROACHING_EDGE -> AlertTrigger.APPROACH
            else -> return // INSIDE or UNCERTAIN: no action
        }

        val key = Triple(dogId, fenceId, result.status)
        val now = System.currentTimeMillis()
        val last = lastFired[key]
        if (last != null && now - last < cooldownMs) return
        lastFired[key] = now

        val rules = rulesByFence[fenceId].orEmpty().filter { it.enabled && it.trigger == trigger }
        for (rule in rules) {
            when (rule.action) {
                AlertAction.NOTIFY_OWNER -> {
                    val verb = if (trigger == AlertTrigger.EXIT) "left" else "is approaching the edge of"
                    showFenceAlert(
                        applicationContext,
                        notificationId = fenceId * 31 + dogId,
                        title = "Dog Tag alert",
                        message = "${dog.name} $verb fence '${fence.name}'",
                    )
                }
                AlertAction.RING_TAG -> {
                    val fmdnTag = tags.find { it.dogId == dogId && it.type == TagType.FMDN }
                    fmdnTag?.id?.let { tagId -> runCatching { repository.ringTag(tagId) } }
                }
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 42
    }
}

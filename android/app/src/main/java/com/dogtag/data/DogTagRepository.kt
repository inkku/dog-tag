package com.dogtag.data

import com.dogtag.data.model.AlertRule
import com.dogtag.data.model.Dog
import com.dogtag.data.model.Fence
import com.dogtag.data.model.LocationSample
import com.dogtag.data.model.Tag
import kotlinx.coroutines.flow.first

/**
 * Thin wrapper that rebuilds the Retrofit client whenever the configured
 * backend URL changes, so screens don't each have to care about that.
 */
class DogTagRepository(private val settings: SettingsRepository) {
    private var cachedBaseUrl: String? = null
    private var cachedApi: BackendApi? = null

    private suspend fun api(): BackendApi {
        val baseUrl = settings.backendBaseUrl.first()
        if (baseUrl != cachedBaseUrl || cachedApi == null) {
            cachedApi = NetworkModule.buildApi(baseUrl)
            cachedBaseUrl = baseUrl
        }
        return cachedApi!!
    }

    suspend fun listDogs(): List<Dog> = api().listDogs()
    suspend fun createDog(dog: Dog): Dog = api().createDog(dog)

    suspend fun listTags(): List<Tag> = api().listTags()
    suspend fun createTag(tag: Tag): Tag = api().createTag(tag)
    suspend fun updateTag(tagId: Int, tag: Tag): Tag = api().updateTag(tagId, tag)
    suspend fun ringTag(tagId: Int) = api().ringTag(tagId)

    suspend fun listFences(): List<Fence> = api().listFences()
    suspend fun createFence(fence: Fence): Fence = api().createFence(fence)
    suspend fun addRule(fenceId: Int, rule: AlertRule): AlertRule = api().addRule(fenceId, rule)
    suspend fun listRules(fenceId: Int): List<AlertRule> = api().listRules(fenceId)

    suspend fun submitLocation(sample: LocationSample): LocationSample = api().submitLocation(sample)
    suspend fun latestLocations(): Map<Int, LocationSample> = api().latestLocations()
}

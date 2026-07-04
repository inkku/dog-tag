package com.dogtag.data

import com.dogtag.data.model.AlertRule
import com.dogtag.data.model.Dog
import com.dogtag.data.model.Fence
import com.dogtag.data.model.LocationSample
import com.dogtag.data.model.Tag
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface BackendApi {
    @GET("dogs")
    suspend fun listDogs(): List<Dog>

    @POST("dogs")
    suspend fun createDog(@Body dog: Dog): Dog

    @GET("tags")
    suspend fun listTags(): List<Tag>

    @POST("tags")
    suspend fun createTag(@Body tag: Tag): Tag

    @PUT("tags/{id}")
    suspend fun updateTag(@Path("id") tagId: Int, @Body tag: Tag): Tag

    @POST("tags/{id}/ring")
    suspend fun ringTag(@Path("id") tagId: Int)

    @GET("fences")
    suspend fun listFences(): List<Fence>

    @POST("fences")
    suspend fun createFence(@Body fence: Fence): Fence

    @POST("fences/{id}/rules")
    suspend fun addRule(@Path("id") fenceId: Int, @Body rule: AlertRule): AlertRule

    @GET("fences/{id}/rules")
    suspend fun listRules(@Path("id") fenceId: Int): List<AlertRule>

    @POST("locations")
    suspend fun submitLocation(@Body sample: LocationSample): LocationSample

    @GET("locations/latest")
    suspend fun latestLocations(): Map<Int, LocationSample>
}

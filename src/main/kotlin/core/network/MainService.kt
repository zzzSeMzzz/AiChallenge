package core.network

import core.data.YaGptRequest
import core.data.YaGptResponse
import retrofit2.http.Body
import retrofit2.http.POST


interface MainService {
    @POST("foundationModels/v1/completion")
    suspend fun send(
        @Body request: YaGptRequest
    ): YaGptResponse

}
package com.lucas.ecorota.data.remote

import com.lucas.ecorota.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface OrsApi {
    @POST("v2/directions/{perfil}")
    suspend fun rota(
        @Path("perfil") perfil: String,
        @Body corpo: RotaRequest
    ): RotaResponse
}

// Coordenadas no formato [longitude, latitude]
data class RotaRequest(val coordinates: List<List<Double>>)

data class RotaResponse(val routes: List<RouteDto>)
data class RouteDto(val summary: SummaryDto)
data class SummaryDto(val distance: Double, val duration: Double) // metros, segundos

object OrsClient {
    private val http = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Authorization", BuildConfig.ORS_API_KEY)
                .build()
            chain.proceed(req)
        }
        .build()

    val api: OrsApi = Retrofit.Builder()
        .baseUrl("https://api.openrouteservice.org/")
        .client(http)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OrsApi::class.java)
}

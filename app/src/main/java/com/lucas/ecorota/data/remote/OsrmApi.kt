package com.lucas.ecorota.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

// Servidor público de rotas do OpenStreetMap (FOSSGIS), sem chave.
// Uso leve, para desenvolvimento e apresentação.
interface OsrmApi {
    @GET("{perfil}/route/v1/driving/{coordenadas}?overview=false")
    suspend fun rota(
        @Path("perfil") perfil: String,
        @Path("coordenadas", encoded = true) coordenadas: String // "lon,lat;lon,lat"
    ): OsrmResponse
}

data class OsrmResponse(val code: String, val routes: List<OsrmRouteDto>)
data class OsrmRouteDto(val distance: Double, val duration: Double) // metros, segundos

// Os servidores do OpenStreetMap pedem um User-Agent que identifique o app
internal val httpOsm: OkHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val req = chain.request().newBuilder()
            .header("User-Agent", "EcoRota/1.0 (projeto academico)")
            .build()
        chain.proceed(req)
    }
    .build()

object OsrmClient {
    val api: OsrmApi = Retrofit.Builder()
        .baseUrl("https://routing.openstreetmap.de/")
        .client(httpOsm)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OsrmApi::class.java)
}

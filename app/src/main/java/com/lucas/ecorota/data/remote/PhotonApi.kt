package com.lucas.ecorota.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Outra busca de endereços do OpenStreetMap (Komoot), sem chave.
// Tolera erros de digitação ("Varone" encontra "Varoni"), mas é menos
// precisa que o Nominatim; por isso é usada só como segunda tentativa.
interface PhotonApi {
    @GET("api/?limit=5")
    suspend fun buscar(@Query("q") texto: String): PhotonResponse
}

data class PhotonResponse(val features: List<PhotonLugarDto>)
data class PhotonLugarDto(val geometry: PhotonGeometriaDto, val properties: PhotonPropriedadesDto)
data class PhotonGeometriaDto(val coordinates: List<Double>) // [longitude, latitude]

data class PhotonPropriedadesDto(
    val name: String?,
    val street: String?,
    val housenumber: String?,
    val district: String?,           // bairro
    val city: String?,
    val countrycode: String?
)

object PhotonClient {
    val api: PhotonApi = Retrofit.Builder()
        .baseUrl("https://photon.komoot.io/")
        .client(httpOsm)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(PhotonApi::class.java)
}

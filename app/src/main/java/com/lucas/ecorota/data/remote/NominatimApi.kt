package com.lucas.ecorota.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Busca de endereços do OpenStreetMap, sem chave.
// Política de uso: no máximo 1 requisição por segundo.
interface NominatimApi {
    @GET("search?format=json&limit=1&countrycodes=br&addressdetails=1")
    suspend fun buscar(@Query("q") texto: String): List<NominatimLugarDto>

    // Caminho inverso: coordenadas -> endereço (usado com a posição do GPS)
    @GET("reverse?format=json&addressdetails=1&zoom=18")
    suspend fun reverso(@Query("lat") lat: Double, @Query("lon") lon: Double): NominatimLugarDto
}

// Os campos vêm nulos quando o reverso não encontra nada ({"error": "Unable to geocode"})
data class NominatimLugarDto(
    val lat: String?,
    val lon: String?,
    @SerializedName("display_name") val nomeCompleto: String?,
    val name: String?,               // nome do lugar (praça, parque...), vazio para endereços
    val address: NominatimEnderecoDto?
)

data class NominatimEnderecoDto(
    val road: String?,
    @SerializedName("house_number") val numero: String?,
    val suburb: String?,             // bairro
    val city: String?,
    val town: String?,
    val village: String?
)

object NominatimClient {
    val api: NominatimApi = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .client(httpOsm)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NominatimApi::class.java)
}

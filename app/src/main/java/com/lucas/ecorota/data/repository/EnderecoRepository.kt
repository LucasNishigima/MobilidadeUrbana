package com.lucas.ecorota.data.repository

import android.os.SystemClock
import com.lucas.ecorota.data.remote.NominatimApi
import com.lucas.ecorota.data.remote.NominatimClient
import com.lucas.ecorota.data.remote.NominatimLugarDto
import com.lucas.ecorota.data.remote.PhotonApi
import com.lucas.ecorota.data.remote.PhotonClient
import com.lucas.ecorota.data.remote.PhotonLugarDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer

data class Local(val nome: String, val lon: Double, val lat: Double) {
    // Formato [longitude, latitude] usado pelo RotaRepository
    val coordenadas get() = listOf(lon, lat)
}

class EnderecoRepository(
    private val nominatim: NominatimApi = NominatimClient.api,
    private val photon: PhotonApi = PhotonClient.api
) {

    // Tenta primeiro o Nominatim (mais preciso). Se não achar, tenta o Photon,
    // que tolera erros de digitação. Retorna null quando nenhum dos dois acha.
    suspend fun buscar(texto: String): Local? =
        buscarNominatim(texto) ?: buscarPhoton(texto)

    // Endereço aproximado de uma coordenada do GPS, só para exibir.
    // As coordenadas do Local são sempre as do GPS, que são exatas.
    suspend fun descrever(lat: Double, lon: Double): Local {
        val nome = runCatching { limitarNominatim { nominatim.reverso(lat, lon) } }
            .getOrNull()
            ?.takeIf { it.nomeCompleto != null }
            ?.let(::formatar)
        return Local(nome = nome ?: "Minha localização", lon = lon, lat = lat)
    }

    private suspend fun buscarNominatim(texto: String): Local? =
        limitarNominatim { nominatim.buscar(texto) }
            .firstOrNull { it.lat != null && it.lon != null }
            ?.let { Local(nome = formatar(it), lon = it.lon!!.toDouble(), lat = it.lat!!.toDouble()) }

    private suspend fun buscarPhoton(texto: String): Local? {
        val brasil = photon.buscar(texto).features.filter { it.properties.countrycode == "BR" }
        val textoNormal = normalizar(texto)
        val naCidadeDigitada = brasil.firstOrNull { lugar ->
            lugar.properties.city?.let { textoNormal.contains(normalizar(it)) } == true
        }
        // O Photon é tolerante demais e às vezes devolve outra cidade
        // ("Faculdade de Tecnologia, Araçatuba" -> Ourinhos). Se o usuário
        // escreveu a cidade depois da vírgula, só aceita resultado dessa cidade.
        val escolhido = naCidadeDigitada ?: brasil.firstOrNull().takeIf { "," !in texto }

        return escolhido?.let {
            Local(
                nome = formatar(it),
                lon = it.geometry.coordinates[0],
                lat = it.geometry.coordinates[1]
            )
        }
    }

    // "Araçatuba" -> "aracatuba", para comparar ignorando acentos e maiúsculas
    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()

    // O Nominatim aceita no máximo 1 requisição por segundo
    private val mutex = Mutex()
    private var ultimaChamada = 0L

    private suspend fun <T> limitarNominatim(chamada: suspend () -> T): T = mutex.withLock {
        val espera = 1000 - (SystemClock.elapsedRealtime() - ultimaChamada)
        if (espera > 0) delay(espera)
        try {
            chamada()
        } finally {
            ultimaChamada = SystemClock.elapsedRealtime()
        }
    }

    // "Avenida Paulista, 1000 · Morro dos Ingleses, São Paulo"
    // "Praça da Sé · Glicério, São Paulo"
    private fun formatar(lugar: NominatimLugarDto): String {
        val end = lugar.address
        val principal = lugar.name?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(end?.road, end?.numero).joinToString(", ").takeIf { it.isNotBlank() }
            // Sem rua nem nome: usa o começo do nome completo
            ?: return lugar.nomeCompleto.orEmpty().split(", ").take(3).joinToString(", ")

        val cidade = end?.city ?: end?.town ?: end?.village
        return juntar(principal, end?.suburb, cidade)
    }

    private fun formatar(lugar: PhotonLugarDto): String {
        val p = lugar.properties
        val principal = p.name?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(p.street, p.housenumber).joinToString(", ")
        return juntar(principal, p.district, p.city)
    }

    private fun juntar(principal: String, bairro: String?, cidade: String?): String {
        val regiao = listOfNotNull(bairro, cidade).joinToString(", ")
        return if (regiao.isBlank()) principal else "$principal · $regiao"
    }
}

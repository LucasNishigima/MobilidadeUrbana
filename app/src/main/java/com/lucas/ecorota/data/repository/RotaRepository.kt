package com.lucas.ecorota.data.repository

import com.lucas.ecorota.data.remote.OsrmApi
import com.lucas.ecorota.data.remote.OsrmClient
import com.lucas.ecorota.util.CalculadoraCO2
import com.lucas.ecorota.util.Coordenada
import com.lucas.ecorota.util.Modal
import com.lucas.ecorota.util.OpcaoRota
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// Usa o OSRM (sem chave). Para voltar à OpenRouteService quando a chave
// da HeiGIT funcionar, troque a chamada por OrsClient.api e modal.perfilOrs.
class RotaRepository(private val api: OsrmApi = OsrmClient.api) {

    suspend fun comparar(origem: List<Double>, destino: List<Double>): List<OpcaoRota> =
        coroutineScope {
            // Formato "lon,lat;lon,lat"
            val coordenadas = "${origem[0]},${origem[1]};${destino[0]},${destino[1]}"

            // Uma chamada por perfil, em paralelo (carro e ônibus compartilham)
            val perfis = Modal.entries.map { it.perfilOsrm }.distinct()
            val rotas = perfis
                .map { perfil -> perfil to async { api.rota(perfil, coordenadas).routes.first() } }
                .associate { (perfil, req) -> perfil to req.await() }

            val co2Carro = CalculadoraCO2.emissao(
                Modal.CARRO, rotas.getValue(Modal.CARRO.perfilOsrm).distance / 1000
            )

            Modal.entries.map { modal ->
                val r = rotas.getValue(modal.perfilOsrm)
                val km = r.distance / 1000
                val co2 = CalculadoraCO2.emissao(modal, km)
                // Ônibus: estimativa de 40% mais lento que carro por causa das paradas
                val fatorTempo = if (modal == Modal.ONIBUS) 1.4 else 1.0
                OpcaoRota(
                    modal = modal,
                    distanciaKm = km,
                    minutos = (r.duration * fatorTempo / 60).toInt(),
                    co2Kg = co2,
                    economiaKg = CalculadoraCO2.economia(co2Carro, co2),
                    trajeto = r.geometry?.coordinates
                        ?.map { Coordenada(lat = it[1], lon = it[0]) }
                        .orEmpty()
                )
            }
        }
}

package com.lucas.ecorota.util

// Nenhum dos serviços tem ônibus: o ônibus usa a rota de carro
enum class Modal(
    val nome: String,
    val perfilOrs: String,
    val perfilOsrm: String,
    val kgCo2PorKm: Double
) {
    CARRO("Carro", "driving-car", "routed-car", 0.12),
    ONIBUS("Ônibus", "driving-car", "routed-car", 0.08),
    BICICLETA("Bicicleta", "cycling-regular", "routed-bike", 0.0),
    A_PE("A pé", "foot-walking", "routed-foot", 0.0)
}

data class OpcaoRota(
    val modal: Modal,
    val distanciaKm: Double,
    val minutos: Int,
    val co2Kg: Double,
    val economiaKg: Double // CO₂ evitado em relação a fazer o trajeto de carro
)

object CalculadoraCO2 {
    fun emissao(modal: Modal, distanciaKm: Double) =
        modal.kgCo2PorKm * distanciaKm

    // Compara com a rota de carro, que pode ter outra distância (ex.: bike corta caminho)
    fun economia(co2Carro: Double, co2Modal: Double) =
        (co2Carro - co2Modal).coerceAtLeast(0.0)

    // Menor emissão; no empate (bike e a pé emitem zero), a mais rápida
    fun maisSustentavel(opcoes: List<OpcaoRota>): OpcaoRota? =
        opcoes.minWithOrNull(compareBy<OpcaoRota> { it.co2Kg }.thenBy { it.minutos })
}

package com.lucas.ecorota.ui.comparacao

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.ecorota.data.repository.EnderecoRepository
import com.lucas.ecorota.data.repository.Local
import com.lucas.ecorota.data.repository.LocalizacaoRepository
import com.lucas.ecorota.data.repository.RotaRepository
import com.lucas.ecorota.util.OpcaoRota
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ComparacaoState {
    data object Inicial : ComparacaoState
    data object Carregando : ComparacaoState
    data class Sucesso(
        val origem: Local,
        val destino: Local,
        val opcoes: List<OpcaoRota>
    ) : ComparacaoState
    data class Erro(val mensagem: String) : ComparacaoState
}

// Origem vinda do GPS: quando `local` existe, ela é usada no lugar do texto digitado
data class OrigemGpsState(
    val buscando: Boolean = false,
    val local: Local? = null,
    val erro: String? = null
)

class ComparacaoViewModel(
    private val rotas: RotaRepository = RotaRepository(),
    private val enderecos: EnderecoRepository = EnderecoRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<ComparacaoState>(ComparacaoState.Inicial)
    val state = _state.asStateFlow()

    private val _origemGps = MutableStateFlow(OrigemGpsState())
    val origemGps = _origemGps.asStateFlow()

    fun usarMinhaLocalizacao(context: Context) {
        viewModelScope.launch {
            _origemGps.value = OrigemGpsState(buscando = true)
            _origemGps.value = try {
                val posicao = LocalizacaoRepository(context).obterAtual()
                if (posicao == null) {
                    OrigemGpsState(erro = "Não foi possível obter sua localização. Verifique se o GPS está ligado.")
                } else {
                    OrigemGpsState(local = enderecos.descrever(posicao.latitude, posicao.longitude))
                }
            } catch (e: Exception) {
                OrigemGpsState(erro = e.message ?: "Erro ao obter a localização")
            }
        }
    }

    fun permissaoLocalizacaoNegada() {
        _origemGps.value = OrigemGpsState(erro = "Permissão de localização negada")
    }

    // Chamado quando o usuário digita na origem: volta a usar o texto
    fun limparOrigemGps() {
        _origemGps.value = OrigemGpsState()
    }

    fun comparar(origemTexto: String, destinoTexto: String) {
        viewModelScope.launch {
            _state.value = ComparacaoState.Carregando
            _state.value = try {
                val origem = _origemGps.value.local ?: enderecos.buscar(origemTexto)
                val destino = enderecos.buscar(destinoTexto)

                when {
                    origem == null -> ComparacaoState.Erro("Origem não encontrada: \"$origemTexto\"")
                    destino == null -> ComparacaoState.Erro("Destino não encontrado: \"$destinoTexto\"")
                    else -> ComparacaoState.Sucesso(
                        origem = origem,
                        destino = destino,
                        opcoes = rotas.comparar(origem.coordenadas, destino.coordenadas)
                    )
                }
            } catch (e: Exception) {
                ComparacaoState.Erro(e.message ?: "Erro desconhecido")
            }
        }
    }
}

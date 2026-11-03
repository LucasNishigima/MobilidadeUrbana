package com.lucas.ecorota.ui.comparacao

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucas.ecorota.data.repository.Local
import com.lucas.ecorota.data.repository.LocalizacaoRepository
import com.lucas.ecorota.ui.theme.EcoRotaTheme
import com.lucas.ecorota.util.CalculadoraCO2
import com.lucas.ecorota.util.Modal
import com.lucas.ecorota.util.OpcaoRota

@Composable
fun ComparacaoScreen(
    modifier: Modifier = Modifier,
    vm: ComparacaoViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val origemGps by vm.origemGps.collectAsState()
    val context = LocalContext.current

    // Pede a permissão de localização na primeira vez; depois vai direto ao GPS
    val pedirPermissao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        if (resultado.values.any { it }) vm.usarMinhaLocalizacao(context)
        else vm.permissaoLocalizacaoNegada()
    }

    ComparacaoConteudo(
        state = state,
        origemGps = origemGps,
        onComparar = vm::comparar,
        onUsarLocalizacao = {
            if (LocalizacaoRepository(context).temPermissao()) {
                vm.usarMinhaLocalizacao(context)
            } else {
                pedirPermissao.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        },
        onOrigemEditada = vm::limparOrigemGps,
        modifier = modifier
    )
}

// Separado do ViewModel para poder ser visto no Preview com dados de exemplo
@Composable
fun ComparacaoConteudo(
    state: ComparacaoState,
    origemGps: OrigemGpsState,
    onComparar: (origem: String, destino: String) -> Unit,
    onUsarLocalizacao: () -> Unit,
    onOrigemEditada: () -> Unit,
    modifier: Modifier = Modifier
) {
    var origem by rememberSaveable { mutableStateOf("") }
    var destino by rememberSaveable { mutableStateOf("") }
    val foco = LocalFocusManager.current

    // Quando o GPS responde, preenche a origem com o endereço encontrado
    LaunchedEffect(origemGps.local) {
        origemGps.local?.let { origem = it.nome }
    }

    val podeComparar = origem.isNotBlank() && destino.isNotBlank() &&
        state != ComparacaoState.Carregando && !origemGps.buscando

    fun comparar() {
        foco.clearFocus()
        onComparar(origem.trim(), destino.trim())
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = origem,
            onValueChange = {
                origem = it
                // Digitou na origem: deixa de usar o GPS e volta a buscar pelo texto
                if (origemGps.local != null) onOrigemEditada()
            },
            label = { Text("Origem") },
            placeholder = { Text("Ex.: Praça da Sé, São Paulo") },
            supportingText = when {
                origemGps.local != null -> {
                    { Text("📍 Usando a posição do GPS") }
                }
                origemGps.erro != null -> {
                    { Text(origemGps.erro, color = MaterialTheme.colorScheme.error) }
                }
                else -> null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            onClick = onUsarLocalizacao,
            enabled = !origemGps.buscando
        ) {
            Text(if (origemGps.buscando) "Buscando sua localização…" else "📍 Usar minha localização")
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = destino,
            onValueChange = { destino = it },
            label = { Text("Destino") },
            placeholder = { Text("Ex.: Av. Paulista 1000, São Paulo") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (podeComparar) comparar() }),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { comparar() },
            enabled = podeComparar,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Comparar rotas") }

        Spacer(Modifier.height(16.dp))

        when (state) {
            ComparacaoState.Inicial -> Text("Digite a origem e o destino para comparar")
            ComparacaoState.Carregando -> CircularProgressIndicator()
            is ComparacaoState.Erro -> Text("Erro: ${state.mensagem}", color = Color.Red)
            is ComparacaoState.Sucesso -> {
                // Mostra o endereço que foi encontrado, para o usuário conferir
                Text("De: ${state.origem.nome}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Para: ${state.destino.nome}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))

                val melhor = CalculadoraCO2.maisSustentavel(state.opcoes)
                if (melhor != null && melhor.economiaKg > 0) {
                    Text(
                        "🌱 Indo ${comoIr(melhor.modal)} você evita %.2f kg de CO₂"
                            .format(melhor.economiaKg),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                }

                LazyColumn {
                    items(state.opcoes) { op ->
                        CardOpcao(op, destaque = op == melhor)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardOpcao(op: OpcaoRota, destaque: Boolean) {
    val cores = if (destaque) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(colors = cores, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            if (destaque) {
                Text(
                    "Opção mais sustentável",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(op.modal.nome, style = MaterialTheme.typography.titleMedium)
            Text("%.1f km · %d min".format(op.distanciaKm, op.minutos))
            Text("CO₂: %.2f kg".format(op.co2Kg))
            when {
                op.modal == Modal.CARRO -> Text(
                    "Referência para comparação",
                    style = MaterialTheme.typography.bodySmall
                )
                op.economiaKg > 0 -> Text(
                    "🌱 Evita %.2f kg de CO₂ em relação ao carro".format(op.economiaKg),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun comoIr(modal: Modal) = when (modal) {
    Modal.CARRO -> "de carro"
    Modal.ONIBUS -> "de ônibus"
    Modal.BICICLETA -> "de bicicleta"
    Modal.A_PE -> "a pé"
}

@Preview(showBackground = true)
@Composable
fun ComparacaoPreview() {
    EcoRotaTheme {
        ComparacaoConteudo(
            state = ComparacaoState.Sucesso(
                origem = Local("Praça da Sé · Glicério, São Paulo", -46.6331, -23.5504),
                destino = Local("Avenida Paulista, 1000 · Morro dos Ingleses, São Paulo", -46.6519, -23.5649),
                opcoes = listOf(
                    OpcaoRota(Modal.CARRO, 3.3, 6, 0.40, 0.0),
                    OpcaoRota(Modal.ONIBUS, 3.3, 9, 0.27, 0.13),
                    OpcaoRota(Modal.BICICLETA, 3.1, 15, 0.0, 0.40),
                    OpcaoRota(Modal.A_PE, 3.1, 40, 0.0, 0.40)
                )
            ),
            origemGps = OrigemGpsState(),
            onComparar = { _, _ -> },
            onUsarLocalizacao = {},
            onOrigemEditada = {}
        )
    }
}

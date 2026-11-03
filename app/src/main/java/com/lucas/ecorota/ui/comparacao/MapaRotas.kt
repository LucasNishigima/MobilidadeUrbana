package com.lucas.ecorota.ui.comparacao

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lucas.ecorota.data.repository.Local
import com.lucas.ecorota.util.Modal
import com.lucas.ecorota.util.OpcaoRota
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

// Cor de cada meio de transporte, usada no mapa e nos cards
fun corDoModal(modal: Modal): Color = when (modal) {
    Modal.CARRO -> Color(0xFFD32F2F)
    Modal.ONIBUS -> Color(0xFFF57C00)
    Modal.BICICLETA -> Color(0xFF2E7D32)
    Modal.A_PE -> Color(0xFF1565C0)
}

// Mapa do OpenStreetMap (osmdroid) com as rotas: a selecionada na cor
// do meio de transporte, as outras em cinza por baixo
@Composable
fun MapaRotas(
    origem: Local,
    destino: Local,
    opcoes: List<OpcaoRota>,
    selecionado: Modal,
    modifier: Modifier = Modifier
) {
    // O Preview do Android Studio não consegue desenhar o MapView
    if (LocalInspectionMode.current) {
        Box(modifier.background(Color.LightGray), contentAlignment = Alignment.Center) {
            Text("Mapa")
        }
        return
    }

    val context = LocalContext.current
    val mapa = remember {
        configurarOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            // Deixa arrastar o mapa sem rolar a tela junto
            setOnTouchListener { v, _ ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }

    // Pausa e libera o mapa junto com o ciclo de vida da tela
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> mapa.onResume()
                Lifecycle.Event.ON_PAUSE -> mapa.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapa.onDetach()
        }
    }

    AndroidView(factory = { mapa }, modifier = modifier) {
        desenhar(it, origem, destino, opcoes, selecionado)
    }
}

private fun configurarOsmdroid(context: Context) {
    Configuration.getInstance().apply {
        // Os servidores de mapa do OpenStreetMap exigem um User-Agent do app
        userAgentValue = "EcoRota/1.0 (projeto academico)"
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(osmdroidBasePath, "tiles")
    }
}

private fun desenhar(
    mapa: MapView,
    origem: Local,
    destino: Local,
    opcoes: List<OpcaoRota>,
    selecionado: Modal
) {
    mapa.overlays.clear()

    // A selecionada por último, para ficar por cima das outras
    opcoes
        .filter { it.trajeto.isNotEmpty() }
        .sortedBy { it.modal == selecionado }
        .forEach { op ->
            val destaque = op.modal == selecionado
            mapa.overlays.add(Polyline(mapa).apply {
                setPoints(op.trajeto.map { GeoPoint(it.lat, it.lon) })
                outlinePaint.color =
                    if (destaque) corDoModal(op.modal).toArgb() else Color(0x99787878).toArgb()
                outlinePaint.strokeWidth = if (destaque) 12f else 6f
                outlinePaint.strokeCap = Paint.Cap.ROUND
            })
        }

    mapa.overlays.add(marcador(mapa, origem, "Origem"))
    mapa.overlays.add(marcador(mapa, destino, "Destino"))
    // Crédito obrigatório pelos termos de uso do OpenStreetMap
    mapa.overlays.add(CopyrightOverlay(mapa.context))
    mapa.invalidate()

    // Enquadra só quando o resultado muda, não ao trocar a rota selecionada
    val chave = origem to destino
    if (mapa.tag != chave) {
        mapa.tag = chave
        val pontos = opcoes.flatMap { op -> op.trajeto.map { GeoPoint(it.lat, it.lon) } } +
            GeoPoint(origem.lat, origem.lon) + GeoPoint(destino.lat, destino.lon)
        enquadrar(mapa, BoundingBox.fromGeoPointsSafe(pontos))
    }
}

private fun marcador(mapa: MapView, local: Local, titulo: String) = Marker(mapa).apply {
    position = GeoPoint(local.lat, local.lon)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    title = titulo
    snippet = local.nome
}

private fun enquadrar(mapa: MapView, caixa: BoundingBox) {
    val borda = 80
    // Na primeira vez o mapa ainda não tem tamanho; espera ele ser desenhado
    if (mapa.width > 0 && mapa.height > 0) {
        mapa.zoomToBoundingBox(caixa, false, borda)
    } else {
        mapa.addOnFirstLayoutListener { _, _, _, _, _ ->
            mapa.zoomToBoundingBox(caixa, false, borda)
        }
    }
}

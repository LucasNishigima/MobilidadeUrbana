package com.lucas.ecorota.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocalizacaoRepository(context: Context) {

    private val appContext = context.applicationContext
    private val cliente = LocationServices.getFusedLocationProviderClient(appContext)

    fun temPermissao(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED }

    // Pede uma posição nova ao GPS; se demorar ou falhar, usa a última conhecida.
    // Retorna null quando não há permissão ou o GPS está desligado.
    @SuppressLint("MissingPermission")
    suspend fun obterAtual(): Location? {
        if (!temPermissao()) return null

        val cancelamento = CancellationTokenSource()
        val atual = withTimeoutOrNull(15_000) {
            cliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelamento.token)
                .aguardar(cancelamento)
        }
        return atual ?: cliente.lastLocation.aguardar()
    }
}

// Converte a Task do Google Play Services em uma função suspend
private suspend fun <T> Task<T>.aguardar(cancelamento: CancellationTokenSource? = null): T? =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        cont.invokeOnCancellation { cancelamento?.cancel() }
    }

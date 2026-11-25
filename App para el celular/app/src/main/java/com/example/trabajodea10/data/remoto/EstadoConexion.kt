package com.example.trabajodea10.data.remoto

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Entrega 8 (RNF-REN-02): estado de la conexión para avisarlo en pantalla.
 * Solo informa: lo que la emprendedora guarda sin señal ya queda en el celular
 * (TramaStore) y en la cola de Sincronizador, que lo sube al volver la red.
 */
object EstadoConexion {
    private const val TAG = "EstadoConexion"

    /** true si el celular tiene una red con internet. Empieza en true para no alarmar al abrir. */
    var conectado by mutableStateOf(true)
        private set

    private val redes = mutableSetOf<Network>()

    fun inicializar(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching {
            val actual = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            conectado = actual?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    synchronized(redes) { redes.add(network) }
                    conectado = true
                }

                override fun onLost(network: Network) {
                    val quedan = synchronized(redes) { redes.remove(network); redes.isNotEmpty() }
                    conectado = quedan
                }
            })
        }.onFailure { Log.w(TAG, "No se pudo leer el estado de la conexión: ${it.message}") }
    }
}

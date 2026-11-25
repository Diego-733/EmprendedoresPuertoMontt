package com.example.trabajodea10

import android.app.Application
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.remoto.ClienteSupabase
import com.example.trabajodea10.data.remoto.Sincronizador

/**
 * Punto de arranque de la aplicación: prepara la sesión guardada de Supabase,
 * la cola de cambios por subir y los datos guardados en el celular.
 * (Las credenciales NO están en el código: vienen de supabase.properties.)
 */
class AplicacionTrama : Application() {
    override fun onCreate() {
        super.onCreate()
        ClienteSupabase.inicializar(this)
        // Entrega 8: aviso en pantalla cuando no hay conexión (RNF-REN-02).
        com.example.trabajodea10.data.remoto.EstadoConexion.inicializar(this)
        Sincronizador.inicializar(this)
        TramaStore.inicializar(this)
        // Entrega 6: vitrina (navegación anónima guardada) y mapas OpenStreetMap.
        com.example.trabajodea10.data.VitrinaStore.inicializar(this)
        com.example.trabajodea10.ui.comprador.configurarMapas(this)
        // Entrega 7: ferias de la emprendedora, seguidores y avisos (canal, Firebase si está configurado).
        com.example.trabajodea10.data.FeriasStore.inicializar(this)
        // Si quedaron cambios sin subir (por ejemplo, sin señal en la feria), se intentan ahora.
        Sincronizador.subirEnSegundoPlano()
    }
}

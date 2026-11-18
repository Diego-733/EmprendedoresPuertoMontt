package com.example.trabajodea10.ui.comprador

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/*
 * Mapas con OpenStreetMap (osmdroid): no necesitan clave de API ni Google Play
 * Services. Se usan en la ficha de feria (RF-MAP-02), el mapa de próximas ferias
 * (RF-MAP-03) y el punto de venta o retiro de la tienda (RF-MAP-04).
 */

/** Centro por defecto: Puerto Montt. */
val CENTRO_PUERTO_MONTT = GeoPoint(-41.4693, -72.9424)

/** Configuración mínima de osmdroid (se llama una vez al iniciar la app). */
fun configurarMapas(contexto: Context) {
    val c = Configuration.getInstance()
    c.load(contexto, contexto.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    c.userAgentValue = contexto.packageName
    // Caché de mosaicos en la carpeta privada de la app (sin permisos de almacenamiento).
    val base = java.io.File(contexto.cacheDir, "osmdroid")
    c.osmdroidBasePath = base
    c.osmdroidTileCache = java.io.File(base, "tiles")
}

/** Punto que se dibuja en el mapa. */
data class PuntoMapa(
    val id: String,
    val lat: Double,
    val lng: Double,
    val titulo: String,
    val detalle: String = "",
    val esPersona: Boolean = false
)

/**
 * Mapa de solo lectura o para marcar un punto.
 * - [puntos]: marcadores; al tocar uno se llama [onTocarPunto].
 * - [onTocarMapa]: si no es null, tocar el mapa entrega la coordenada (para marcar un lugar).
 * - [centro]/[zoom]: se aplican cada vez que cambian.
 */
@Composable
fun MapaOsm(
    puntos: List<PuntoMapa>,
    modifier: Modifier = Modifier,
    centro: GeoPoint? = null,
    zoom: Double = 14.0,
    onTocarPunto: ((PuntoMapa) -> Unit)? = null,
    onTocarMapa: ((Double, Double) -> Unit)? = null
) {
    val contexto = LocalContext.current
    // Último centro aplicado: no se mueve el mapa en cada redibujo, solo si cambia el destino.
    val ultimoCentro = remember { arrayOfNulls<String>(1) }
    val mapa = remember {
        MapView(contexto).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setTilesScaledToDpi(true)
            setMinZoomLevel(4.0)
            setMaxZoomLevel(19.0)
            controller.setZoom(zoom)
            controller.setCenter(centro ?: CENTRO_PUERTO_MONTT)
        }
    }

    DisposableEffect(mapa) {
        mapa.onResume()
        onDispose {
            mapa.onPause()
            mapa.onDetach()
        }
    }

    AndroidView(
        factory = { mapa },
        modifier = modifier,
        update = { vista ->
            vista.overlays.clear()
            if (onTocarMapa != null) {
                vista.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        if (p != null) onTocarMapa(p.latitude, p.longitude)
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                }))
            }
            puntos.forEach { punto ->
                val marcador = Marker(vista).apply {
                    position = GeoPoint(punto.lat, punto.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = punto.titulo
                    snippet = punto.detalle
                    setOnMarkerClickListener { m, _ ->
                        if (onTocarPunto != null && !punto.esPersona) onTocarPunto(punto) else m.showInfoWindow()
                        true
                    }
                }
                vista.overlays.add(marcador)
            }
            val destino = centro ?: puntos.firstOrNull()?.let { GeoPoint(it.lat, it.lng) }
            val clave = destino?.let { "${it.latitude},${it.longitude},$zoom" }
            if (destino != null && clave != ultimoCentro[0]) {
                vista.controller.setZoom(zoom)
                vista.controller.setCenter(destino)
                ultimoCentro[0] = clave
            }
            vista.invalidate()
        }
    )
}

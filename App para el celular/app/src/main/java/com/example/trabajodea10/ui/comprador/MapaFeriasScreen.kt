package com.example.trabajodea10.ui.comprador

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.trabajodea10.data.ReglasVitrina
import com.example.trabajodea10.data.VitrinaStore
import com.example.trabajodea10.ui.componentes.BarraSuperior
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.componentes.Tarjeta
import com.example.trabajodea10.ui.componentes.Vacio
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.theme.GrisChip
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.TextoSecundario
import com.example.trabajodea10.ui.theme.VerdeContenedor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.util.GeoPoint
import kotlin.coroutines.resume

/*
 * MAPA DE PRÓXIMAS FERIAS (RF-MAP-03, RNF-PRI-04; checklist A12–A15).
 * - El permiso de ubicación se pide AL ABRIR ESTA PANTALLA (nunca al iniciar la app),
 *   explicando antes para qué se usa.
 * - Con permiso: el mapa se centra en la persona y las ferias van de la más cercana
 *   a la más lejana. Sin permiso: el mapa sigue funcionando y se ordenan por fecha.
 * - La ubicación solo se usa en el celular: no se envía ni se guarda en el servidor.
 */

private enum class EstadoPermiso { PREGUNTAR, CONCEDIDO, RECHAZADO }

private val PERMISOS_UBICACION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun tienePermisoUbicacion(contexto: Context): Boolean = PERMISOS_UBICACION.any {
    ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun PantallaMapaFerias(nav: ControladorNavegacion) {
    val contexto = LocalContext.current
    var permiso by remember { mutableStateOf(if (tienePermisoUbicacion(contexto)) EstadoPermiso.CONCEDIDO else EstadoPermiso.PREGUNTAR) }
    var ubicacion by remember { mutableStateOf<Location?>(null) }
    var buscandoUbicacion by remember { mutableStateOf(false) }

    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
        permiso = if (resultado.values.any { it }) EstadoPermiso.CONCEDIDO else EstadoPermiso.RECHAZADO
    }

    LaunchedEffect(Unit) { VitrinaStore.cargar() }
    LaunchedEffect(permiso) {
        if (permiso == EstadoPermiso.CONCEDIDO) {
            buscandoUbicacion = true
            ubicacion = obtenerUbicacion(contexto)
            buscandoUbicacion = false
        }
    }

    val proximas = ReglasVitrina.feriasProximas(VitrinaStore.ferias.toList())
    val lat = ubicacion?.latitude
    val lng = ubicacion?.longitude
    val ordenadas = ReglasVitrina.ordenarFerias(proximas, lat, lng)
    val puntos = ordenadas.filter { it.tieneUbicacion }.map {
        PuntoMapa(it.id, it.lat!!, it.lng!!, it.nombre, ReglasVitrina.rangoFechas(it))
    } + listOfNotNull(ubicacion?.let { PuntoMapa("yo", it.latitude, it.longitude, "Tú estás aquí", esPersona = true) })

    Column(Modifier.fillMaxSize()) {
        BarraSuperior(
            titulo = "Mapa de ferias",
            subtitulo = when {
                ubicacion != null -> "Ordenadas de la más cercana a la más lejana"
                buscandoUbicacion -> "Buscando tu ubicación…"
                else -> "Ordenadas por fecha"
            }
        )
        MapaOsm(
            puntos = puntos,
            modifier = Modifier.fillMaxWidth().height(300.dp),
            centro = if (lat != null && lng != null) GeoPoint(lat, lng) else CENTRO_PUERTO_MONTT,
            zoom = if (ubicacion != null) 12.0 else 11.0,
            onTocarPunto = { p -> nav.irA(Ruta.FeriaPublica(p.id)) }
        )
        if (permiso == EstadoPermiso.RECHAZADO) {
            Surface(color = GrisChip, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TextoSecundario)
                    Spacer(Modifier.width(8.dp))
                    Text("Sin tu ubicación: las ferias se ordenan por fecha.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { permiso = EstadoPermiso.PREGUNTAR }) { Text("Usar ubicación") }
                }
            }
        } else if (permiso == EstadoPermiso.CONCEDIDO && ubicacion == null && !buscandoUbicacion) {
            Surface(color = GrisChip, modifier = Modifier.fillMaxWidth()) {
                Text("No pudimos obtener tu ubicación (¿GPS apagado?). Se ordenan por fecha.",
                    modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (ordenadas.isEmpty()) {
                item {
                    Vacio(
                        titulo = if (VitrinaStore.cargando) "Cargando ferias…" else "No hay ferias próximas",
                        mensaje = VitrinaStore.error?.let { "Sin conexión: $it" } ?: "Cuando se publiquen, aparecerán aquí y en el mapa."
                    )
                }
            }
            items(ordenadas, key = { it.id }) { f ->
                val distancia = if (lat != null && lng != null && f.tieneUbicacion)
                    ReglasVitrina.textoDistancia(ReglasVitrina.distanciaMetros(lat, lng, f.lat!!, f.lng!!)) else null
                Tarjeta(onClick = { nav.irA(Ruta.FeriaPublica(f.id)) }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.nombre, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (distancia != null) {
                                Surface(color = VerdeContenedor, shape = RoundedCornerShape(50)) {
                                    Text(distancia, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontWeight = FontWeight.Bold, color = NegroApple)
                                }
                            }
                        }
                        DatosFeria(f.copy(descripcion = ""))
                    }
                }
            }
        }
    }

    // Se explica para qué se usa ANTES de que Android muestre su aviso de permiso.
    if (permiso == EstadoPermiso.PREGUNTAR) {
        AlertDialog(
            onDismissRequest = { permiso = EstadoPermiso.RECHAZADO },
            icon = { Icon(Icons.Filled.MyLocation, contentDescription = null) },
            title = { Text("¿Ver las ferias más cercanas?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Usamos tu ubicación solo en este celular para centrar el mapa y ordenar las ferias por cercanía. " +
                    "No se guarda ni se envía a nadie. Si prefieres no compartirla, el mapa funciona igual y las ferias se ordenan por fecha.")
            },
            confirmButton = { TextButton(onClick = { pedirPermiso.launch(PERMISOS_UBICACION) }) { Text("Usar mi ubicación", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { permiso = EstadoPermiso.RECHAZADO }) { Text("Ahora no") } }
        )
    }
}

/** Última ubicación conocida o una lectura nueva (máximo 10 s). Null si no se pudo. */
@SuppressLint("MissingPermission")
private suspend fun obtenerUbicacion(contexto: Context): Location? {
    if (!tienePermisoUbicacion(contexto)) return null
    val lm = contexto.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val proveedores = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    val conocida = proveedores.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    // Una lectura de menos de 10 minutos basta para ordenar ferias.
    if (conocida != null && System.currentTimeMillis() - conocida.time < 10 * 60 * 1000) return conocida
    val proveedor = proveedores.firstOrNull { it == LocationManager.NETWORK_PROVIDER }
        ?: proveedores.firstOrNull { it == LocationManager.GPS_PROVIDER }
        ?: return conocida
    val nueva = withTimeoutOrNull(10_000) {
        suspendCancellableCoroutine<Location?> { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancelar = android.os.CancellationSignal()
                cont.invokeOnCancellation { cancelar.cancel() }
                lm.getCurrentLocation(proveedor, cancelar, ContextCompat.getMainExecutor(contexto)) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } else {
                val oyente = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location)
                    }
                    @Deprecated("Requerido en Android 7 a 10")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                cont.invokeOnCancellation { lm.removeUpdates(oyente) }
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(proveedor, oyente, Looper.getMainLooper())
            }
        }
    }
    return nueva ?: conocida
}

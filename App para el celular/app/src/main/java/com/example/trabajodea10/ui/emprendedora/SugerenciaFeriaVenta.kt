package com.example.trabajodea10.ui.emprendedora

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.trabajodea10.data.FeriasStore
import com.example.trabajodea10.data.MotivoSugerencia
import com.example.trabajodea10.data.ReglasFerias
import com.example.trabajodea10.data.ReglasVitrina
import com.example.trabajodea10.data.UbicacionMomento
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.Tarjeta
import com.example.trabajodea10.ui.theme.TextoSecundario

/**
 * Sugiere la feria del día al registrar una venta (RF-VEN-05) y, si la emprendedora
 * autorizó la ubicación, la feria en curso donde está (RF-MAP-05). Solo ferias donde
 * participa (confirmada o agregada). No elige nada sola: un toque en «Usar esta feria».
 * La ubicación se usa en el momento y no se guarda ni se envía. Vender no depende de esto.
 */
@Composable
fun SugerenciaFeriaVenta(seleccionada: String?, onUsar: (String) -> Unit) {
    val contexto = LocalContext.current
    var ubicacion by remember { mutableStateOf<UbicacionMomento?>(null) }
    var conPermiso by remember { mutableStateOf(tienePermisoUbicacion(contexto)) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        conPermiso = r.values.any { it }
        if (conPermiso) {
            ubicacion = ultimaUbicacion(contexto)
            aviso = if (ubicacion == null) "No hay una ubicación reciente del celular. Se sugiere por la fecha." else null
        } else {
            aviso = "Sin permiso de ubicación: la sugerencia es solo por la fecha. Puedes vender igual."
        }
    }

    LaunchedEffect(Unit) {
        if (FeriasStore.misFerias.isEmpty()) FeriasStore.cargarMisFerias()
        if (conPermiso) ubicacion = ultimaUbicacion(contexto)
    }

    val sugerencia = FeriasStore.sugerencia(ubicacion) ?: return
    val hoyConPunto = FeriasStore.misFerias.any {
        it.estadoInvitacion.participa && ReglasFerias.ocurreHoy(it, ReglasFerias.fechaSantiago()) && it.lat != null
    }

    Tarjeta {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                when (sugerencia.motivo) {
                    MotivoSugerencia.UBICACION -> "Estás en «${sugerencia.feria.nombre}»" +
                        (sugerencia.distanciaMetros?.let { " (a ${ReglasVitrina.textoDistancia(it)})" } ?: "")
                    MotivoSugerencia.FECHA -> "Hoy participas en «${sugerencia.feria.nombre}»"
                },
                fontWeight = FontWeight.Bold
            )
            Text("¿Fue en esta feria? Es una sugerencia: elige otro lugar si no.", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            if (seleccionada == sugerencia.feria.feriaId) {
                Text("✓ Lugar elegido: ${sugerencia.feria.nombre}", fontWeight = FontWeight.SemiBold)
            } else {
                BotonPrimario(texto = "Usar esta feria", onClick = { onUsar(sugerencia.feria.feriaId) })
            }
            if (!conPermiso && hoyConPunto && sugerencia.motivo == MotivoSugerencia.FECHA) {
                TextButton(onClick = {
                    pedir.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }) { Text("Confirmar con mi ubicación (opcional)") }
            }
            aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextoSecundario) }
        }
    }
}

private fun tienePermisoUbicacion(c: Context): Boolean =
    ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Última ubicación conocida (sin servicios de Google). Su edad y precisión deciden si sirve. */
@SuppressLint("MissingPermission")
private fun ultimaUbicacion(c: Context): UbicacionMomento? {
    if (!tienePermisoUbicacion(c)) return null
    val lm = c.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val mejor = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
        .maxByOrNull { it.elapsedRealtimeNanos } ?: return null
    val edadMs = (SystemClock.elapsedRealtimeNanos() - mejor.elapsedRealtimeNanos) / 1_000_000
    return UbicacionMomento(mejor.latitude, mejor.longitude, if (mejor.hasAccuracy()) mejor.accuracy else 5000f, edadMs)
}

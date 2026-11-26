package com.example.trabajodea10.ui.emprendedora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.trabajodea10.data.GuiaProducto
import com.example.trabajodea10.ui.componentes.BotonMicrofono
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.componentes.dictadoDisponible
import com.example.trabajodea10.ui.componentes.rememberDictadoPorVoz
import com.example.trabajodea10.ui.theme.Corteza
import com.example.trabajodea10.ui.theme.Linea
import com.example.trabajodea10.ui.theme.Musgo
import com.example.trabajodea10.ui.theme.MusgoClaro
import com.example.trabajodea10.ui.theme.MusgoOscuro
import com.example.trabajodea10.ui.theme.Neblina
import com.example.trabajodea10.ui.theme.TintaSuave
import com.example.trabajodea10.voz.BotonEscuchar
import com.example.trabajodea10.voz.LectorVoz
import androidx.compose.runtime.LaunchedEffect

/**
 * Tarjeta de la guía dentro del alta de producto. Va en el contenido que se desplaza (no flota):
 * así no tapa el teclado, la foto ni los botones «Atrás» y «Siguiente».
 */
@Composable
fun TarjetaGuiaPaso(
    paso: Int, totalPasos: Int, nombrePaso: String, estado: String?, abrirEjemplo: Boolean, onSalir: () -> Unit,
    /** Resumen del producto para leerlo en voz alta en la revisión (opcional). */
    resumenParaLeer: String? = null
) {
    val ayuda = GuiaProducto.AYUDAS.getOrNull(paso) ?: return
    var verEjemplo by rememberSaveable(paso) { mutableStateOf(abrirEjemplo) }
    var verAyuda by rememberSaveable(paso) { mutableStateOf(abrirEjemplo) }
    val contexto = LocalContext.current
    val locucion = GuiaProducto.locucion(paso, resumenParaLeer)
    // Lectura automática al llegar a cada paso: solo si la persona la activó en Ajustes
    // y no hay TalkBack (para no hablar encima del lector de pantalla).
    LaunchedEffect(paso) {
        if (LectorVoz.debeLeerSolo(contexto)) LectorVoz.hablar("guia-paso", locucion)
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MusgoClaro, border = BorderStroke(2.dp, Musgo), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🧭 Guía · Paso ${paso + 1} de $totalPasos: $nombrePaso", fontWeight = FontWeight.Bold,
                color = MusgoOscuro, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(ayuda.indicacion, style = MaterialTheme.typography.bodyLarge)
            BotonEscuchar(id = "guia-paso", texto = locucion, etiqueta = "Escuchar este paso")
            if (!estado.isNullOrBlank()) {
                Text(estado, fontWeight = FontWeight.Bold, color = MusgoOscuro, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            // Los detalles quedan detrás de dos botones, para que la indicación se lea primero.
            TextButton(onClick = { verEjemplo = !verEjemplo }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (verEjemplo) "Ocultar el ejemplo" else "Ver un ejemplo", fontWeight = FontWeight.Bold, color = Musgo)
            }
            if (verEjemplo) {
                if (paso == 0) EjemploFotoEncuadre() else Text(ayuda.ejemplo ?: "", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = { verAyuda = !verAyuda }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (verAyuda) "Ocultar la ayuda" else "Necesito ayuda", fontWeight = FontWeight.Bold, color = Musgo)
            }
            if (verAyuda) Text(ayuda.ayuda, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onSalir, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Salir de la guía", color = TintaSuave)
            }
        }
    }
}

/** Ilustración liviana de cómo encuadrar; dice que es un ejemplo para que nadie crea que es su foto. */
@Composable
fun EjemploFotoEncuadre() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .background(Color(0xFFF4F1EA), RoundedCornerShape(12.dp))
                .semantics { contentDescription = "Ejemplo de encuadre: el producto al centro, ocupando casi toda la foto, sobre un fondo liso y con luz de frente" }
        ) {
            val w = size.width; val h = size.height
            drawRect(Color(0xFFE6E0D3), topLeft = Offset(0f, h * 0.74f), size = Size(w, h * 0.26f))
            val guia = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            listOf(w / 3, 2 * w / 3).forEach { x -> drawLine(Musgo.copy(alpha = .45f), Offset(x, 0f), Offset(x, h), 2f, pathEffect = guia) }
            listOf(h / 3, 2 * h / 3).forEach { y -> drawLine(Musgo.copy(alpha = .45f), Offset(0f, y), Offset(w, y), 2f, pathEffect = guia) }
            drawOval(Color.Black.copy(alpha = .12f), topLeft = Offset(w * 0.31f, h * 0.74f), size = Size(w * 0.38f, h * 0.1f))
            drawRoundRect(Corteza, topLeft = Offset(w * 0.36f, h * 0.25f), size = Size(w * 0.28f, h * 0.53f), cornerRadius = CornerRadius(18f, 18f))
            drawRoundRect(Color(0xFFF4F1EA), topLeft = Offset(w * 0.40f, h * 0.38f), size = Size(w * 0.20f, h * 0.19f), cornerRadius = CornerRadius(6f, 6f))
            drawCircle(Color(0xFFF2C14E), radius = h * 0.08f, center = Offset(w * 0.86f, h * 0.18f))
            drawRoundRect(Musgo, size = Size(w, h), cornerRadius = CornerRadius(24f, 24f), style = Stroke(width = 4f, pathEffect = guia))
        }
        Text("Ejemplo: no es la foto de tu tienda. Producto al centro, fondo liso y luz de frente.",
            fontWeight = FontWeight.Bold, color = TintaSuave, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * «¿Qué quieres hacer?»: frase escrita o dictada, o un toque en una opción. Si la frase es ambigua,
 * muestra dos opciones claras. Solo ofrece lo que la cuenta puede hacer.
 */
@Composable
fun DialogoGuia(ventaDisponible: Boolean, onIr: (GuiaProducto.Intencion) -> Unit, onCerrar: () -> Unit) {
    val contexto = LocalContext.current
    val disponibles = remember(ventaDisponible) {
        GuiaProducto.Intencion.entries.filter { it != GuiaProducto.Intencion.VENTA || ventaDisponible }.toSet()
    }
    var frase by rememberSaveable { mutableStateOf("") }
    var opciones by remember { mutableStateOf<List<GuiaProducto.Intencion>>(emptyList()) }
    var aviso by rememberSaveable { mutableStateOf("") }
    fun responder(texto: String) {
        if (texto.isBlank()) { aviso = "Escribe lo que necesitas o toca una de las opciones."; return }
        when (val r = GuiaProducto.interpretar(texto, disponibles)) {
            is GuiaProducto.Respuesta.Ir -> onIr(r.intencion)
            is GuiaProducto.Respuesta.Elegir -> { opciones = r.opciones; aviso = "" }
        }
    }
    val hayDictado = remember { dictadoDisponible(contexto) }
    val dictar = rememberDictadoPorVoz(
        indicacion = "Di qué quieres hacer",
        onSinTexto = { aviso = "Dictado cancelado. Puedes escribirlo o tocar una opción." }
    ) { texto -> frase = texto; responder(texto) }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Guíame paso a paso", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = frase, onValueChange = { frase = it; opciones = emptyList(); aviso = "" },
                    label = { Text("¿Qué quieres hacer?") }, placeholder = { Text("Ej: quiero subir un producto") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { responder(frase) }),
                    trailingIcon = if (hayDictado) ({ BotonMicrofono(onClick = dictar, descripcion = "Decir qué quieres hacer") }) else null
                )
                if (aviso.isNotBlank()) Text(aviso, color = TintaSuave, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                BotonPrimario(texto = "Ir", onClick = { responder(frase) })
                if (opciones.isNotEmpty()) {
                    Text("¿Qué quieres hacer?", fontWeight = FontWeight.Bold)
                    opciones.forEach { o -> BotonSecundario(texto = o.titulo, onClick = { onIr(o) }) }
                } else {
                    Surface(color = Neblina, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Linea)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("O toca una opción:", style = MaterialTheme.typography.bodyMedium)
                            BotonSecundario(texto = "Subir un producto nuevo", onClick = { onIr(GuiaProducto.Intencion.PRODUCTO) })
                            BotonSecundario(texto = "¿Cómo agrego una foto?", onClick = { onIr(GuiaProducto.Intencion.FOTO) })
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCerrar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cerrar") } }
    )
}

package com.example.trabajodea10.voz

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * «🔊 Escuchar» / «⏹ Detener» (48 dp). Lee [texto] con la voz del celular. Si el celular no tiene
 * voz en español, el botón desaparece y todo sigue funcionando con el texto en pantalla.
 * Al salir de la pantalla, lo que este botón estaba leyendo se detiene.
 */
@Composable
fun BotonEscuchar(
    id: String, texto: String, modifier: Modifier = Modifier, etiqueta: String = "Escuchar",
    /** false en listas que se desplazan: la pantalla decide cuándo callar. */
    detenerAlSalir: Boolean = true
) {
    if (LectorVoz.disponible == false || texto.isBlank()) return
    val leyendoEste = LectorVoz.leyendo == id
    DisposableEffect(id) { onDispose { if (detenerAlSalir && LectorVoz.leyendo == id) LectorVoz.detener() } }
    TextButton(
        onClick = { if (leyendoEste) LectorVoz.detener() else LectorVoz.hablar(id, texto) },
        modifier = modifier.heightIn(min = 48.dp).semantics {
            contentDescription = if (leyendoEste) "Detener la lectura" else "$etiqueta en voz alta"
        }
    ) {
        Text(if (leyendoEste) "⏹ Detener" else "🔊 $etiqueta", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

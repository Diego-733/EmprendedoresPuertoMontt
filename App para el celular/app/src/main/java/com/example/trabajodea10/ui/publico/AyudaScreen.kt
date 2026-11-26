package com.example.trabajodea10.ui.publico

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.trabajodea10.ui.componentes.BarraSuperior
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.componentes.BotonesTamanoTexto
import com.example.trabajodea10.ui.componentes.Tarjeta
import com.example.trabajodea10.ui.theme.VerdeOk

/**
 * PANTALLA PARA VISITANTES: "Ayuda".
 * Antes ofrecía llamar o escribir a un número fijo de «soporte» cuyo dueño no se conocía:
 * se retiró. La plataforma no tiene un contacto oficial configurado, así que la ayuda
 * orienta a escribir a cada tienda desde su ficha (con su propio WhatsApp o su formulario).
 * También incluye los botones para agrandar el texto.
 */
@Composable
fun PantallaVisAyuda(onAsistente: () -> Unit = {}) {

    Column(modifier = Modifier.fillMaxSize()) {
        BarraSuperior(
            titulo = "Ayuda",
            subtitulo = "Estamos para ayudarte",
            acciones = { BotonesTamanoTexto() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 16.dp, vertical = 8.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "¿Necesitas una mano?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            // Asistente 2.0: busca productos, tiendas y ferias, y explica cómo comprar o vender.
            androidx.compose.material3.Button(onClick = onAsistente, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Preguntarle al asistente")
            }
            Tarjeta {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Quieres comprar o preguntar por un producto?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Abre la tienda en «Vitrina» y usa «Escribir por WhatsApp» o «Enviar un mensaje». Tu consulta le llega directo a esa emprendedora.",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("¿Buscas una feria?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("En «Ferias» y «Mapa ferias» ves las fechas, el lugar y cómo llegar.", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Recordatorio de accesibilidad: cómo agrandar el texto.
            Tarjeta {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "¿El texto te parece chico?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Usa estos botones para agrandarlo o achicarlo.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BotonesTamanoTexto()
                }
            }

            Text(
                text = "Red Trama · Ferias artesanales y emprendedoras",
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
package com.example.trabajodea10.ui.asistente

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.trabajodea10.data.GuiaProducto
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.asistente.TemasAyuda.Destino
import com.example.trabajodea10.data.asistente.TemasAyuda.Tema
import com.example.trabajodea10.ui.componentes.BarraSuperior
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.componentes.TituloSeccion
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.voz.BotonEscuchar

/**
 * Centro de ayuda por tareas. Usa los mismos temas que «Enséñame» del Asistente Alerce (un solo
 * contenido, sin manuales duplicados): pasos cortos, «Escuchar», un botón que abre la pantalla real
 * y «Guíame paso a paso» donde existe la guía.
 */
@Composable
fun PantallaCentroAyuda(nav: ControladorNavegacion) {
    val grupos = listOf(
        "Productos" to listOf(Tema.AGREGAR_PRODUCTO, Tema.AGREGAR_FOTO, Tema.CAMBIAR_PRECIO, Tema.CAMBIAR_CANTIDAD, Tema.OCULTAR_PRODUCTO, Tema.BORRAR_PRODUCTO),
        "Ventas" to listOf(Tema.REGISTRAR_VENTA),
        "Ferias" to listOf(Tema.PARTICIPAR_FERIA),
        "Mi tienda" to listOf(Tema.COMPARTIR_TIENDA, Tema.CAMBIAR_WHATSAPP, Tema.CAMBIAR_MIS_DATOS, Tema.VER_MENSAJES),
        "Si algo quedó pendiente" to listOf(Tema.SABER_SI_SE_GUARDO),
        "Mi cuenta y la app" to listOf(Tema.TEXTO_Y_VOZ, Tema.MODO_SENCILLO, Tema.RECUPERAR_ACCESO)
    )
    var abierto by rememberSaveable { mutableStateOf<String?>(null) }

    fun ir(d: Destino) {
        when (d) {
            Destino.MIS_PRODUCTOS -> nav.irA(Ruta.EmpCatalogo)
            Destino.NUEVO_PRODUCTO -> nav.irA(Ruta.ProductoDetalle(TramaStore.crearProductoBorrador()))
            Destino.VENTA -> nav.irA(Ruta.EmpVenta)
            Destino.FERIAS -> nav.irA(Ruta.EmpFerias)
            Destino.TIENDA -> nav.irA(Ruta.EmpTienda)
            Destino.COMPARTIR -> nav.irA(Ruta.EmpTiendaQR(TramaStore.tienda.id))
            Destino.MENSAJES -> nav.irA(Ruta.BandejaContactos)
            Destino.HISTORIAL -> nav.irA(Ruta.HistorialVentas)
            Destino.INICIO -> nav.seleccionarTab(Ruta.EmpInicio)
            Destino.AJUSTES -> nav.irA(Ruta.Ajustes)
            Destino.AYUDA -> Unit
        }
    }

    fun guia(foto: Boolean) {
        val intencion = if (foto) GuiaProducto.Intencion.FOTO else GuiaProducto.Intencion.PRODUCTO
        val id = TramaStore.crearProductoBorrador()
        TramaStore.borrador?.let { TramaStore.actualizarBorrador(it.producto, intencion.paso ?: 0) }
        GuiaProducto.iniciar(intencion)
        nav.irA(Ruta.ProductoDetalle(id))
    }

    Column(Modifier.fillMaxSize()) {
        BarraSuperior(titulo = "Centro de ayuda", subtitulo = "Elige lo que quieres hacer", onVolver = { nav.volver() })
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                BotonPrimario(texto = "Preguntarle al Asistente Alerce", onClick = { nav.irA(Ruta.Asistente) })
            }
            grupos.forEach { (titulo, temas) ->
                item(key = "t_$titulo") { TituloSeccion(texto = titulo) }
                items(temas, key = { it.name }) { tema ->
                    val abiertoEste = abierto == tema.name
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .clickable { abierto = if (abiertoEste) null else tema.name }
                                    .semantics { role = Role.Button; stateDescription = if (abiertoEste) "abierto" else "cerrado" }
                            ) {
                                Text(tema.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f).semantics { heading() })
                                Text(if (abiertoEste) "▲" else "▼", color = MaterialTheme.colorScheme.primary)
                            }
                            if (abiertoEste) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                                    tema.pasos.forEachIndexed { i, p ->
                                        Row {
                                            Text("${i + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.widthIn(min = 24.dp))
                                            Text(p, style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                    BotonEscuchar(id = "ayuda-${tema.name}", texto = tema.paraLeer())
                                    if (tema.conGuia) BotonPrimario(texto = "🧭 Guíame paso a paso", onClick = { guia(tema == Tema.AGREGAR_FOTO) })
                                    if (tema.destino != Destino.AYUDA) BotonSecundario(texto = tema.etiquetaDestino, onClick = { ir(tema.destino) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

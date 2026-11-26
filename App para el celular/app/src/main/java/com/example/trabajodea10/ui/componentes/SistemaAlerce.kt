package com.example.trabajodea10.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trabajodea10.ui.theme.AmbarSuave
import com.example.trabajodea10.ui.theme.AmbarTexto
import com.example.trabajodea10.ui.theme.BlancoPuro
import com.example.trabajodea10.ui.theme.Bosque
import com.example.trabajodea10.ui.theme.Brote
import com.example.trabajodea10.ui.theme.EstadoAcaboFondo
import com.example.trabajodea10.ui.theme.EstadoAcaboTexto
import com.example.trabajodea10.ui.theme.EstadoOkFondo
import com.example.trabajodea10.ui.theme.EstadoOkTexto
import com.example.trabajodea10.ui.theme.EstadoPedidoFondo
import com.example.trabajodea10.ui.theme.EstadoPedidoTexto
import com.example.trabajodea10.ui.theme.GrisChip
import com.example.trabajodea10.ui.theme.Musgo
import com.example.trabajodea10.ui.theme.MusgoClaro
import com.example.trabajodea10.ui.theme.MusgoOscuro
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.SobreBosqueSuave
import com.example.trabajodea10.ui.theme.TextoSecundario

/*
 * Sistema visual «nueva generación» de Alerce (rediseño v2).
 *
 * - Cabecera de marca: panel de bosque con degradado, para las pantallas de inicio de cada rol.
 * - Mosaicos y filas de gestión: acciones con ícono en un cuadro de color (tono), título y detalle.
 * - Tonos: el color solo refuerza; cada estado lleva siempre su texto.
 * Todos los elementos tocables miden al menos 48 dp y crecen con la letra del celular.
 */

/** Tono de un ícono, insignia o aviso. Fondo claro + texto oscuro con contraste ≥ 7:1. */
enum class Tono(val fondo: Color, val texto: Color) {
    MUSGO(MusgoClaro, MusgoOscuro),
    AMBAR(AmbarSuave, AmbarTexto),
    CORTEZA(EstadoAcaboFondo, EstadoAcaboTexto),
    AZUL(EstadoPedidoFondo, EstadoPedidoTexto),
    OK(EstadoOkFondo, EstadoOkTexto),
    NEUTRO(GrisChip, NegroApple)
}

/**
 * Panel de marca para el inicio de cada rol: degradado de bosque, título grande en blanco,
 * acciones a la derecha y un espacio para cifras o un buscador.
 */
@Composable
fun CabeceraMarca(
    titulo: String,
    modifier: Modifier = Modifier,
    sobretitulo: String? = null,
    subtitulo: String? = null,
    margen: androidx.compose.ui.unit.Dp = 12.dp,
    onVolver: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {},
    contenido: (@Composable ColumnScope.() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = margen, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Bosque, Brote), start = Offset(0f, 0f), end = Offset(1400f, 900f)))
            // Dos círculos tenues: textura de marca sin imágenes extra.
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.06f), radius = size.minDimension * 0.75f, center = Offset(size.width * 1.02f, -size.height * 0.05f))
                drawCircle(Color.White.copy(alpha = 0.05f), radius = size.minDimension * 0.42f, center = Offset(size.width * 0.86f, size.height * 1.05f))
            }
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onVolver != null) {
                    IconButton(onClick = onVolver, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = BlancoPuro)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                    if (sobretitulo != null) Text(
                        sobretitulo.uppercase(), color = SobreBosqueSuave, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        titulo, color = BlancoPuro, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (subtitulo != null) Text(subtitulo, color = SobreBosqueSuave, fontSize = 15.sp, lineHeight = 20.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, content = acciones)
            }
            if (contenido != null) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.padding(end = 8.dp), content = contenido)
            }
        }
    }
}

/** Botón de ícono para la cabecera de marca (blanco sobre bosque, 48 dp). */
@Composable
fun AccionCabecera(icono: ImageVector, descripcion: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icono, contentDescription = descripcion, tint = BlancoPuro) }
    }
}

/** Cifra real dentro de la cabecera de marca (por ejemplo, «12 · Emprendedoras activas»). */
@Composable
fun CifraCabecera(valor: String, etiqueta: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.13f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Text(valor, color = BlancoPuro, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(etiqueta, color = SobreBosqueSuave, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Cuadro de ícono con tono (44 dp). */
@Composable
fun IconoTono(icono: ImageVector, tono: Tono, tamano: Int = 44) {
    Box(
        Modifier.size(tamano.dp).clip(RoundedCornerShape((tamano / 3.4f).dp)).background(tono.fondo),
        contentAlignment = Alignment.Center
    ) { Icon(icono, contentDescription = null, tint = tono.texto, modifier = Modifier.size((tamano * 0.52f).dp)) }
}

/** Insignia con número o texto corto (por ejemplo, pendientes). */
@Composable
fun Insignia(texto: String, tono: Tono = Tono.AMBAR) {
    Surface(shape = RoundedCornerShape(50), color = tono.fondo) {
        Text(
            texto, color = tono.texto, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Mosaico de acción: tarjeta con ícono de color, título y detalle. Pensado para grillas de 2. */
@Composable
fun MosaicoAccion(
    titulo: String,
    icono: ImageVector,
    tono: Tono,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detalle: String? = null,
    insignia: String? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp), color = BlancoPuro, shadowElevation = 2.dp,
        modifier = modifier
            .heightIn(min = 124.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = titulo, onClick = onClick)
            .semantics(mergeDescendants = true) {}
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconoTono(icono, tono)
                Spacer(Modifier.weight(1f))
                if (insignia != null) Insignia(insignia)
            }
            Column {
                Text(titulo, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 22.sp, color = NegroApple)
                if (detalle != null) Text(detalle, fontSize = 14.sp, lineHeight = 19.sp, color = TextoSecundario)
            }
        }
    }
}

/** Fila de gestión dentro de una lista agrupada: ícono, título, detalle, insignia y flecha. */
@Composable
fun FilaGestion(
    titulo: String,
    icono: ImageVector,
    tono: Tono,
    onClick: () -> Unit,
    detalle: String? = null,
    insignia: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(role = Role.Button, onClickLabel = titulo, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconoTono(icono, tono)
        Spacer(Modifier.width(14.dp))
        // Con letra grande la insignia va debajo del texto, para no aplastar el título.
        val grande = letraGrande()
        Column(Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = NegroApple)
            if (detalle != null) Text(detalle, fontSize = 14.sp, lineHeight = 19.sp, color = TextoSecundario)
            if (insignia != null && grande) { Spacer(Modifier.height(4.dp)); Insignia(insignia) }
        }
        if (insignia != null && !grande) { Spacer(Modifier.width(8.dp)); Insignia(insignia) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextoSecundario)
    }
}

/** Grupo de filas en una tarjeta blanca con separadores finos. */
@Composable
fun GrupoFilas(modifier: Modifier = Modifier, filas: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = BlancoPuro, shadowElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp), content = filas)
    }
}

/** Encabezado de sección con sobretítulo en mayúsculas (jerarquía editorial). */
@Composable
fun EncabezadoSeccion(
    titulo: String,
    modifier: Modifier = Modifier,
    sobretitulo: String? = null,
    accion: String? = null,
    onAccion: (() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (sobretitulo != null) Text(
                sobretitulo.uppercase(), color = Musgo, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
            )
            Text(titulo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = NegroApple, modifier = Modifier.semantics { heading() })
        }
        if (accion != null && onAccion != null) {
            TextButton(onClick = onAccion, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("$accion ›", color = Musgo, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

/** Aviso con franja de color, texto y una acción con nombre. */
@Composable
fun AvisoAccion(
    texto: String,
    tono: Tono,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    accion: String? = null,
    onAccion: (() -> Unit)? = null
) {
    Surface(shape = RoundedCornerShape(18.dp), color = tono.fondo, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().heightIn(min = 56.dp).background(tono.texto.copy(alpha = 0.55f)))
            Row(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icono != null) {
                    Icon(icono, contentDescription = null, tint = tono.texto, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(texto, color = tono.texto, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
                if (accion != null && onAccion != null) {
                    TextButton(onClick = onAccion, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(accion, color = tono.texto, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/** Bloque de fecha (día grande y mes abreviado), para ferias. */
@Composable
fun BloqueFecha(dia: String, mes: String, modifier: Modifier = Modifier) {
    Column(
        modifier.widthIn(min = 58.dp).clip(RoundedCornerShape(16.dp)).background(Bosque).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(dia, color = BlancoPuro, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 24.sp)
        Text(mes.uppercase(), color = SobreBosqueSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

/** Píldora de estado con texto (el color solo refuerza). */
@Composable
fun PildoraEstado(texto: String, tono: Tono, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = tono.fondo, modifier = modifier) {
        Text(texto, color = tono.texto, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

/** Cargando con texto (nunca un indicador mudo). */
@Composable
fun EstadoCargando(texto: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(color = Musgo, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(texto, color = TextoSecundario, fontSize = 15.sp)
    }
}

/** true con la letra del celular al 130 % o más: las filas de cifras y mosaicos se apilan. */
@Composable
fun letraGrande(): Boolean = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f

/** Cifras de la cabecera: en fila con letra normal; una debajo de otra (valor y etiqueta en línea) con letra grande. */
@Composable
fun FilaCifras(vararg cifras: Pair<String, String>) {
    if (letraGrande()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cifras.forEach { (valor, etiqueta) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.13f))
                        .padding(horizontal = 14.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(valor, color = BlancoPuro, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(12.dp))
                    Text(etiqueta, color = SobreBosqueSuave, fontSize = 15.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cifras.forEach { (valor, etiqueta) -> CifraCabecera(valor, etiqueta, Modifier.weight(1f)) }
        }
    }
}

/** Dos elementos lado a lado (mosaicos); con letra grande, uno debajo del otro a todo el ancho. */
@Composable
fun ParAdaptable(primero: @Composable (Modifier) -> Unit, segundo: @Composable (Modifier) -> Unit) {
    if (letraGrande()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            primero(Modifier.fillMaxWidth()); segundo(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            primero(Modifier.weight(1f)); segundo(Modifier.weight(1f))
        }
    }
}

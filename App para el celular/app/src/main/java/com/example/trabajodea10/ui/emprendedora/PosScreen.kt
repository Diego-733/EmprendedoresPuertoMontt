package com.example.trabajodea10.ui.emprendedora

import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.formatearMoneda
import com.example.trabajodea10.ui.componentes.BarraSuperior
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.ContadorCantidad
import com.example.trabajodea10.ui.componentes.FotoProducto
import com.example.trabajodea10.ui.componentes.Tarjeta
import com.example.trabajodea10.ui.componentes.TituloSeccion
import com.example.trabajodea10.ui.componentes.Vacio
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.theme.Berenjena
import com.example.trabajodea10.ui.theme.LineaSuave
import com.example.trabajodea10.ui.theme.TextoSecundario

@Composable
fun PantallaVenta(
    nav: ControladorNavegacion,
    avisar: (String) -> Unit
) {
    // Rediseño v2 (punto de venta para la feria): una sola pantalla con la grilla de productos para tocar
    // lo vendido, el carrito arriba y «Continuar al pago» fijo abajo. La lógica es la de siempre:
    // DialogoElegirOpcion agrega con las mismas validaciones y el pago sigue en PantallaPagoVenta.
    val carrito = TramaStore.carritoVenta
    val total = carrito.sumOf { it.precio * it.cantidad }
    val vendibles = TramaStore.productosDeMiTienda().filter { it.vendidaEn == null && it.nombre.isNotBlank() }
    var elegir by remember { mutableStateOf<com.example.trabajodea10.data.Producto?>(null) }
    elegir?.let { p -> DialogoElegirOpcion(producto = p, onCerrar = { elegir = null }, onAgregado = { elegir = null; avisar(it) }) }
    val columnas = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) 1 else 2

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                com.example.trabajodea10.ui.componentes.CabeceraMarca(
                    sobretitulo = TramaStore.tienda.nombre,
                    titulo = "Venta",
                    subtitulo = if (carrito.isEmpty()) "Toca lo que vendiste" else "${carrito.sumOf { it.cantidad }} artículos en el carrito",
                    margen = 0.dp,
                    onVolver = if (nav.pila.size > 1) ({ nav.volver() }) else null,
                    acciones = {
                        com.example.trabajodea10.ui.componentes.AccionCabecera(Icons.Filled.History, "Historial de ventas") { nav.irA(Ruta.HistorialVentas) }
                    }
                ) {
                    if (carrito.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("Total", color = com.example.trabajodea10.ui.theme.SobreBosqueSuave, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(formatearMoneda(total), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            if (carrito.isNotEmpty()) item {
                com.example.trabajodea10.ui.componentes.EncabezadoSeccion("En el carrito", sobretitulo = "Esta venta")
                Spacer(Modifier.height(8.dp))
                Tarjeta {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        carrito.forEachIndexed { index, item ->
                            if (index > 0) androidx.compose.material3.HorizontalDivider(color = LineaSuave)
                            // Dos líneas: qué es (foto, nombre, precio) y cuánto (subtotal, cantidad, quitar).
                            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FotoProducto(
                                    fotoUrl = item.fotoUrl ?: TramaStore.productoPorId(item.productoId)?.fotoUrl,
                                    colorRespaldo = TramaStore.productoPorId(item.productoId)?.colorFoto ?: 0xFF8E6B94,
                                    inicial = item.nombre, modifier = Modifier.size(48.dp), forma = RoundedCornerShape(12.dp), tamanoInicial = 16.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = item.nombre, fontWeight = FontWeight.Bold, maxLines = 2)
                                    Text(
                                        text = "${item.detalle} · ${formatearMoneda(item.precio)} c/u" + if (item.encargo) " · encargo" else "",
                                        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                Text(text = "Subtotal ${formatearMoneda(item.precio * item.cantidad)}",
                                    fontWeight = FontWeight.ExtraBold, color = Berenjena, modifier = Modifier.weight(1f))
                                ContadorCantidad(
                                    cantidad = item.cantidad,
                                    onCambio = { nueva ->
                                        if (nueva <= 0) {
                                            carrito.remove(item)
                                        } else {
                                            // No se puede vender más de lo que queda en esa opción.
                                            val p = TramaStore.productoPorId(item.productoId)
                                            val problema = p?.let { com.example.trabajodea10.data.ReglasCatalogo.validarLinea(it, it.variantes.firstOrNull { v -> v.id == item.varianteId }, nueva, item.encargo) }
                                                ?: "El producto ya no está en tu catálogo."
                                            if (problema != null && nueva > item.cantidad) avisar(problema)
                                            else {
                                                val idx = carrito.indexOf(item)
                                                if (idx >= 0) carrito[idx] = item.copy(cantidad = nueva)
                                            }
                                        }
                                    }
                                )
                                IconButton(onClick = { carrito.removeAll { it === item } }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Quitar ${item.nombre} del carrito", tint = TextoSecundario, modifier = Modifier.size(20.dp))
                                }
                            }
                            }
                        }
                    }
                }
            }

            item {
                com.example.trabajodea10.ui.componentes.EncabezadoSeccion(
                    if (carrito.isEmpty()) "Elige qué vendiste" else "Agregar otro producto", sobretitulo = "Tus productos")
            }
            if (vendibles.isEmpty()) item {
                Vacio(
                    titulo = "Aún no tienes productos para vender",
                    mensaje = "Agrega productos desde «Mis productos» para armar la venta.",
                    icono = { Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = TextoSecundario) }
                )
                BotonPrimario(texto = "Ir al catálogo", onClick = { nav.seleccionarTab(Ruta.EmpCatalogo) })
            }
            items(vendibles.chunked(columnas), key = { fila -> "v_" + fila.joinToString("_") { it.id } }) { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    fila.forEach { p -> MosaicoProductoVenta(p, Modifier.weight(1f)) { elegir = p } }
                    if (fila.size < columnas) Spacer(Modifier.weight(1f))
                }
            }
        }

        // Pago fijo abajo: siempre a la vista con el total, aunque la lista sea larga.
        if (carrito.isNotEmpty()) {
            androidx.compose.material3.Surface(color = Color.White, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    BotonPrimario(
                        texto = "Continuar al pago · ${formatearMoneda(total)}",
                        onClick = {
                            val problema = TramaStore.validarVenta(carrito.toList())
                            if (problema != null) avisar(problema) else nav.irA(Ruta.PagoVenta)
                        }
                    )
                }
            }
        }
    }
}

/** Mosaico de un producto para tocar al vender: foto, nombre, precio y cuántos quedan. */
@Composable
private fun MosaicoProductoVenta(p: com.example.trabajodea10.data.Producto, modifier: Modifier, onClick: () -> Unit) {
    val agotado = p.agotado
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 2.dp,
        modifier = modifier.clip(RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Agregar ${p.nombre} a la venta", role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
    ) {
        Column {
            FotoProducto(
                fotoUrl = p.fotoUrl, colorRespaldo = p.colorFoto, inicial = p.nombre,
                modifier = Modifier.fillMaxWidth().height(96.dp), forma = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), tamanoInicial = 22.sp
            )
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.nombre, fontWeight = FontWeight.Bold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(formatearMoneda(p.precio), fontWeight = FontWeight.ExtraBold, color = com.example.trabajodea10.ui.theme.Corteza)
                Text(
                    when { agotado -> "Se acabó"; p.modalidad == com.example.trabajodea10.data.Modalidad.A_PEDIDO -> "A pedido"; p.manejaStock -> "Quedan ${p.stockTotal}"; else -> "Disponible" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (agotado) com.example.trabajodea10.ui.theme.EstadoAcaboTexto else TextoSecundario
                )
            }
        }
    }
}

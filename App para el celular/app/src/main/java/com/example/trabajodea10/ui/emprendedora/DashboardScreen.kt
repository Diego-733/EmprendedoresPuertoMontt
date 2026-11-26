package com.example.trabajodea10.ui.emprendedora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trabajodea10.data.EstadoInvitacion7
import com.example.trabajodea10.data.FeriasStore
import com.example.trabajodea10.data.ReglasFerias
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.fechaLegible
import com.example.trabajodea10.data.formatearMoneda
import com.example.trabajodea10.ui.componentes.AccionCabecera
import com.example.trabajodea10.ui.componentes.AvisoAccion
import com.example.trabajodea10.ui.componentes.BloqueFecha
import com.example.trabajodea10.ui.componentes.CabeceraMarca
import com.example.trabajodea10.ui.componentes.CifraCabecera
import com.example.trabajodea10.ui.componentes.EncabezadoSeccion
import com.example.trabajodea10.ui.componentes.FilaGestion
import com.example.trabajodea10.ui.componentes.GrupoFilas
import com.example.trabajodea10.ui.componentes.IconoTono
import com.example.trabajodea10.ui.componentes.MosaicoAccion
import com.example.trabajodea10.ui.componentes.PildoraEstado
import com.example.trabajodea10.ui.componentes.Tono
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.theme.BlancoPuro
import com.example.trabajodea10.ui.theme.Bosque
import com.example.trabajodea10.ui.theme.FondoLimpio
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.TextoSecundario
import com.example.trabajodea10.ui.theme.VerdeOk

/**
 * Inicio de la emprendedora (rediseño v2). Pensado para la feria, con el celular en una mano:
 *  1. Cabecera de marca con su tienda, tres cifras reales y «Registrar una venta» a la vista.
 *  2. Lo que espera respuesta (invitaciones) y lo que conviene revisar (se acabó / quedan pocos).
 *  3. Accesos rápidos en mosaico: añadir, mis productos, ferias, compartir.
 *  4. Asistente y ayuda; próxima feria con su fecha; últimas ventas.
 * Se quitó la cifra «Seguidores»: salía de datos de ejemplo, no del servidor.
 */
@Composable
fun PantallaEmprendedoraInicio(
    nav: ControladorNavegacion,
    avisar: (String) -> Unit
) {
    val tienda = TramaStore.tienda
    val alertas = TramaStore.alertasStock()
    // Nombre del perfil de la cuenta con sesión (antes salía siempre «Camila», de los datos de ejemplo).
    val saludo = com.example.trabajodea10.data.SesionManager.saludo

    LaunchedEffect(Unit) { FeriasStore.cargarMisFerias() }
    val hoy = ReglasFerias.fechaSantiago()
    val porResponder = FeriasStore.misFerias.filter {
        it.estadoInvitacion == EstadoInvitacion7.PENDIENTE &&
            ReglasFerias.feriaActiva(it.estado, it.fechaInicio, it.fechaFin, hoy)
    }
    val proxima = ReglasFerias.calendario(FeriasStore.misFerias.toList(), hoy)
        .flatMap { it.second }.firstOrNull { it.estadoInvitacion.participa }
    // Modo sencillo (opcional, en Ajustes): botones grandes con lo esencial. No quita nada:
    // la barra de abajo, Ajustes y «Ver el inicio completo» siguen llevando a todo lo demás.
    var verCompleto by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val sencillo = TramaStore.prefs.modoSencillo && !verCompleto

    Column(Modifier.fillMaxSize().background(FondoLimpio).verticalScroll(rememberScrollState())) {
        CabeceraMarca(
            sobretitulo = tienda.nombre,
            titulo = saludo,
            subtitulo = if (sencillo) "¿Qué quieres hacer hoy?" else "Así va tu tienda",
            acciones = {
                AccionCabecera(Icons.Filled.Settings, "Ajustes y mi cuenta") { nav.irA(Ruta.Ajustes) }
            }
        ) {
            if (!sencillo) {
                com.example.trabajodea10.ui.componentes.FilaCifras(
                    formatearMoneda(TramaStore.ventasDelMes()) to "Vendido este mes",
                    TramaStore.productosPublicados().toString() to "A la vista",
                    TramaStore.stockTotal().toString() to "Unidades"
                )
                Spacer(Modifier.height(14.dp))
                // La tarea principal, en blanco sobre el bosque: se encuentra sin buscar.
                Button(
                    onClick = { nav.seleccionarTab(Ruta.EmpVenta) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlancoPuro, contentColor = Bosque)
                ) {
                    Icon(Icons.Filled.PointOfSale, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Registrar una venta", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 0. Cuenta desactivada por la administración (lo informa el servidor; la regla real está allá).
            if (com.example.trabajodea10.data.SesionManager.cuentaDesactivada) {
                AvisoAccion(
                    texto = "La administración desactivó tu tienda: no se muestra en la vitrina y no puedes hacer cambios. Lo que guardaste se conserva.",
                    tono = Tono.CORTEZA, icono = Icons.Filled.Warning
                )
            }
            // 1. Lo que espera respuesta
            if (porResponder.isNotEmpty()) {
                AvisoAccion(
                    texto = if (porResponder.size == 1) "Te invitaron a «${porResponder.first().nombre}». Falta tu respuesta."
                    else "Tienes ${porResponder.size} invitaciones a ferias por responder.",
                    tono = Tono.AMBAR, icono = Icons.Filled.EventAvailable,
                    accion = "Responder", onAccion = { nav.seleccionarTab(Ruta.EmpFerias) }
                )
            }

            if (sencillo) {
                InicioSencillo(nav, onVerCompleto = { verCompleto = true })
                return@Column
            }

            // 2. Lo que conviene revisar
            if (alertas.isNotEmpty()) {
                val acabados = alertas.filter { it.agotado }
                AvisoAccion(
                    texto = if (acabados.isNotEmpty()) "Se acabó: " + acabados.take(3).joinToString(", ") { it.nombre } + (if (acabados.size > 3) "…" else "")
                    else "Quedan pocos: " + alertas.take(3).joinToString(", ") { it.nombre + " (" + it.stockTotal + ")" },
                    tono = if (acabados.isNotEmpty()) Tono.CORTEZA else Tono.AMBAR,
                    icono = Icons.Filled.Warning,
                    accion = "Revisar", onAccion = { nav.seleccionarTab(Ruta.EmpCatalogo) }
                )
            }

            // 3. Accesos rápidos
            EncabezadoSeccion("Accesos rápidos", sobretitulo = "Tu tienda")
            com.example.trabajodea10.ui.componentes.ParAdaptable(
                { m -> MosaicoAccion("Añadir producto", Icons.Filled.AddCircle, Tono.MUSGO,
                    { nav.irA(Ruta.ProductoDetalle(TramaStore.crearProductoBorrador())) }, m, detalle = "Foto, nombre y precio") },
                { m -> MosaicoAccion("Mis productos", Icons.Filled.Inventory2, Tono.AMBAR,
                    { nav.seleccionarTab(Ruta.EmpCatalogo) }, m, detalle = "Cuántos tengo y quién los ve",
                    insignia = alertas.size.takeIf { it > 0 }?.toString()) }
            )
            com.example.trabajodea10.ui.componentes.ParAdaptable(
                { m -> MosaicoAccion("Mis ferias", Icons.Filled.EventAvailable, Tono.AZUL,
                    { nav.seleccionarTab(Ruta.EmpFerias) }, m, detalle = "Invitaciones y fechas",
                    insignia = porResponder.size.takeIf { it > 0 }?.toString()) },
                { m -> MosaicoAccion("Compartir tienda", Icons.Filled.QrCode2, Tono.CORTEZA,
                    { nav.irA(Ruta.EmpTiendaQR(tienda.id)) }, m, detalle = "Código QR y enlace") }
            )

            // 4. Ayuda: el asistente nunca reemplaza los botones de siempre.
            GrupoFilas {
                FilaGestion("Asistente Alerce", Icons.Filled.RecordVoiceOver, Tono.MUSGO, { nav.irA(Ruta.Asistente) },
                    detalle = "Dime qué necesitas: te enseño o te ayudo a hacerlo")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 74.dp, end = 16.dp))
                FilaGestion("Centro de ayuda", Icons.AutoMirrored.Filled.HelpOutline, Tono.NEUTRO, { nav.irA(Ruta.CentroAyuda) },
                    detalle = "Paso a paso, con lectura en voz alta")
            }

            // 5. Próxima feria confirmada (del servidor)
            EncabezadoSeccion("Próxima feria", sobretitulo = "Agenda", accion = "Mis ferias", onAccion = { nav.seleccionarTab(Ruta.EmpFerias) })
            if (proxima == null) {
                AvisoAccion("No tienes ferias confirmadas. Cuando confirmes una invitación, aparecerá aquí con su fecha y lugar.", Tono.NEUTRO)
            } else {
                GrupoFilas {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClickLabel = "Ver mis ferias") { nav.seleccionarTab(Ruta.EmpFerias) }
                            .padding(16.dp).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val partes = proxima.fechaInicio?.take(10)?.split("-")
                        val meses = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
                        BloqueFecha(partes?.getOrNull(2)?.trimStart('0') ?: "?", partes?.getOrNull(1)?.toIntOrNull()?.let { meses.getOrNull(it - 1) } ?: "fecha")
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(proxima.nombre, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = NegroApple)
                            Text(listOf(proxima.horario, proxima.ubicacion).filter { it.isNotBlank() }.joinToString(" · "),
                                fontSize = 14.sp, color = TextoSecundario, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            PildoraEstado(proxima.estadoInvitacion.etiqueta, Tono.OK)
                        }
                    }
                }
            }

            // 6. Últimas ventas
            EncabezadoSeccion("Últimas ventas", sobretitulo = "Movimiento", accion = "Historial", onAccion = { nav.irA(Ruta.HistorialVentas) })
            val ultimas = TramaStore.ultimasVentas(3)
            if (ultimas.isEmpty()) {
                AvisoAccion("Aún no registras ventas este mes.", Tono.NEUTRO)
            } else {
                GrupoFilas {
                    ultimas.forEachIndexed { index, venta ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically) {
                            IconoTono(Icons.Filled.PointOfSale, Tono.OK, tamano = 40)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = TramaStore.feriaDeLaVenta(venta.feriaId), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(text = "${fechaLegible(venta.fecha)} · ${venta.medioPago.etiqueta}", fontSize = 14.sp, color = TextoSecundario)
                            }
                            Text(text = formatearMoneda(venta.total), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = VerdeOk)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Botón grande de una tarea (modo sencillo): ícono de color, texto visible, al menos 80 dp. */
@Composable
private fun BotonTarea(titulo: String, detalle: String, icono: ImageVector, principal: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (principal) Bosque else BlancoPuro,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClickLabel = titulo, onClick = onClick)
            .semantics(mergeDescendants = true) {}
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (principal) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(BlancoPuro.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(icono, contentDescription = null, tint = BlancoPuro, modifier = Modifier.size(28.dp))
                }
            } else IconoTono(icono, Tono.MUSGO, tamano = 52)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(titulo, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = if (principal) BlancoPuro else NegroApple)
                Text(detalle, fontSize = 15.sp, color = if (principal) com.example.trabajodea10.ui.theme.SobreBosqueSuave else TextoSecundario)
            }
        }
    }
}

/** Inicio del modo sencillo: siete accesos grandes, con texto visible y en el orden de uso diario. */
@Composable
private fun InicioSencillo(nav: ControladorNavegacion, onVerCompleto: () -> Unit) {
    BotonTarea("Hablar con mi asistente", "Dime qué necesitas, con tu voz o escribiendo", Icons.Filled.RecordVoiceOver, principal = true) { nav.irA(Ruta.Asistente) }
    BotonTarea("Registrar una venta", "Descuenta lo que vendiste", Icons.Filled.PointOfSale, principal = false) { nav.seleccionarTab(Ruta.EmpVenta) }
    BotonTarea("Agregar un producto", "Foto, nombre y precio", Icons.Filled.AddCircle, principal = false) {
        nav.irA(Ruta.ProductoDetalle(TramaStore.crearProductoBorrador()))
    }
    BotonTarea("Ver mis productos", "Cuántos tengo y quién los ve", Icons.Filled.Inventory2, principal = false) { nav.seleccionarTab(Ruta.EmpCatalogo) }
    BotonTarea("Ver mis ferias", "Invitaciones y fechas", Icons.Filled.EventAvailable, principal = false) { nav.seleccionarTab(Ruta.EmpFerias) }
    BotonTarea("Compartir mi tienda", "Código QR y enlace", Icons.Filled.QrCode2, principal = false) { nav.irA(Ruta.EmpTiendaQR(TramaStore.tienda.id)) }
    BotonTarea("Necesito ayuda", "Te explico paso a paso", Icons.AutoMirrored.Filled.HelpOutline, principal = false) { nav.irA(Ruta.CentroAyuda) }
    TextButton(onClick = onVerCompleto, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("Ver el inicio completo", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
    Text("El modo sencillo se apaga en Ajustes › Accesibilidad.", color = TextoSecundario, fontSize = 14.sp, modifier = Modifier.padding(bottom = 24.dp))
}

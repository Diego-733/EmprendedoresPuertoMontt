package com.example.trabajodea10.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.trabajodea10.data.AdminStore
import com.example.trabajodea10.data.EstadoCuentaAdmin
import com.example.trabajodea10.data.Feria
import com.example.trabajodea10.data.FeriaPublica
import com.example.trabajodea10.data.ReglasAdmin
import com.example.trabajodea10.data.ReglasFerias
import com.example.trabajodea10.data.SesionManager
import com.example.trabajodea10.data.VitrinaStore
import com.example.trabajodea10.data.rangoDeFechas
import com.example.trabajodea10.ui.componentes.AccionCabecera
import com.example.trabajodea10.ui.componentes.AvisoAccion
import com.example.trabajodea10.ui.componentes.BloqueFecha
import com.example.trabajodea10.ui.componentes.CabeceraMarca
import com.example.trabajodea10.ui.componentes.ChipEstado
import com.example.trabajodea10.ui.componentes.CifraCabecera
import com.example.trabajodea10.ui.componentes.EncabezadoSeccion
import com.example.trabajodea10.ui.componentes.EstadoCargando
import com.example.trabajodea10.ui.componentes.GrupoFilas
import com.example.trabajodea10.ui.componentes.MosaicoAccion
import com.example.trabajodea10.ui.componentes.PildoraEstado
import com.example.trabajodea10.ui.componentes.Tarjeta
import com.example.trabajodea10.ui.componentes.Tono
import com.example.trabajodea10.ui.componentes.colorDeFeria
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.theme.FondoLimpio
import com.example.trabajodea10.ui.theme.GrisTexto
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.TextoSecundario

/*
 * Inicio de administración (rediseño v2).
 * Antes: cuatro métricas y ferias de EJEMPLO (MockData), un botón «Nueva feria» que solo creaba un
 * borrador en el celular y «Directorio». Ahora: cifras reales del servidor, lo que requiere atención,
 * el calendario de ferias publicadas, las áreas de gestión y la actividad reciente (bitácora).
 */
@Composable
fun PantallaAdminInicio(
    nav: ControladorNavegacion,
    avisar: (String) -> Unit
) {
    val ambito = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (!AdminStore.cargadoAlgunaVez) AdminStore.cargar()
        VitrinaStore.cargar()
    }
    val resumen = ReglasAdmin.resumen(AdminStore.cuentas)
    val hoy = ReglasFerias.fechaSantiago()
    val ferias = ReglasAdmin.clasificarFerias(VitrinaStore.ferias.toList(), hoy)
    val nombre = SesionManager.nombreUsuarioActual.ifBlank { "Administración" }

    Column(Modifier.fillMaxSize().background(FondoLimpio).verticalScroll(rememberScrollState())) {
        CabeceraMarca(
            sobretitulo = FechasAdmin.hoyLegible(hoy),
            titulo = "Hola, ${nombre.substringBefore(' ')}",
            subtitulo = "Panel de administración de Alerce",
            acciones = {
                AccionCabecera(Icons.Filled.Refresh, "Actualizar datos") { ambito.launch { AdminStore.cargar(); VitrinaStore.cargar() } }
                AccionCabecera(Icons.Filled.Settings, "Ajustes y mi cuenta") { nav.irA(Ruta.Ajustes) }
            }
        ) {
            com.example.trabajodea10.ui.componentes.FilaCifras(
                resumen.activas.toString() to "Tiendas activas",
                resumen.pendientes.toString() to "Por registrarse",
                (ferias.enCurso.size + ferias.proximas.size).toString() to "Ferias por venir"
            )
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (AdminStore.sonEjemplo) AvisoAccion(
                "Datos de ejemplo: esta versión no está conectada al servidor. Nada de lo que ves aquí es real.",
                Tono.AMBAR, icono = Icons.Filled.Science
            )
            AdminStore.error?.let { AvisoAccion(it, Tono.CORTEZA, accion = "Reintentar", onAccion = { ambito.launch { AdminStore.cargar() } }) }
            if (AdminStore.cargando && !AdminStore.cargadoAlgunaVez) EstadoCargando("Trayendo los datos del servidor…")

            // 1. Lo que requiere atención (solo lo que existe)
            EncabezadoSeccion("Requiere atención", sobretitulo = "Hoy")
            var hayPendientes = false
            ferias.enCurso.firstOrNull()?.let { f ->
                hayPendientes = true
                AvisoAccion("En curso hoy: «${f.nombre}»${if (f.lugar.isNotBlank()) " · ${f.lugar}" else ""}.", Tono.OK,
                    icono = Icons.Filled.EventAvailable, accion = "Ver", onAccion = { nav.irA(Ruta.FeriaPublica(f.id)) })
            }
            if (resumen.pendientes > 0) {
                hayPendientes = true
                AvisoAccion(
                    if (resumen.pendientes == 1) "1 correo autorizado aún no se registra." else "${resumen.pendientes} correos autorizados aún no se registran.",
                    Tono.AMBAR, icono = Icons.Filled.HourglassTop, accion = "Revisar",
                    onAccion = { AdminStore.filtroAlAbrir = EstadoCuentaAdmin.PENDIENTE_REGISTRO; nav.seleccionarTab(Ruta.AdminEmprendedoras) }
                )
            }
            if (resumen.desactivadas > 0) {
                hayPendientes = true
                AvisoAccion(
                    if (resumen.desactivadas == 1) "1 tienda está desactivada." else "${resumen.desactivadas} tiendas están desactivadas.",
                    Tono.CORTEZA, icono = Icons.Filled.PauseCircle, accion = "Ver",
                    onAccion = { AdminStore.filtroAlAbrir = EstadoCuentaAdmin.DESACTIVADA; nav.seleccionarTab(Ruta.AdminEmprendedoras) }
                )
            }
            if (resumen.sinTienda > 0) {
                hayPendientes = true
                AvisoAccion(
                    if (resumen.sinTienda == 1) "1 cuenta de emprendedora no tiene tienda." else "${resumen.sinTienda} cuentas de emprendedora no tienen tienda.",
                    Tono.AZUL, icono = Icons.Filled.Storefront, accion = "Ver",
                    onAccion = { AdminStore.filtroAlAbrir = EstadoCuentaAdmin.SIN_TIENDA; nav.seleccionarTab(Ruta.AdminEmprendedoras) }
                )
            }
            if (resumen.sinAutorizacion > 0) {
                hayPendientes = true
                AvisoAccion(
                    "${resumen.sinAutorizacion} emprendedora(s) se registraron por la vía abierta anterior, sin autorización. Revisa y decide.",
                    Tono.AZUL, icono = Icons.Filled.Storefront, accion = "Revisar",
                    onAccion = { nav.seleccionarTab(Ruta.AdminEmprendedoras) }
                )
            }
            if (!AdminStore.registroControlado && !AdminStore.sonEjemplo) {
                AvisoAccion("El servidor todavía no tiene el registro controlado (migración 11). Ver docs/administracion.", Tono.AZUL)
            }
            if (!hayPendientes && AdminStore.cargadoAlgunaVez && AdminStore.error == null) {
                AvisoAccion("Todo al día: no hay cuentas ni ferias esperando una acción.", Tono.OK, icono = Icons.Filled.CheckCircle)
            }

            // 2. Calendario de ferias publicadas
            EncabezadoSeccion("Ferias", sobretitulo = "Calendario", accion = "Ver todas", onAccion = { nav.seleccionarTab(Ruta.AdminFerias) })
            val siguientes = (ferias.enCurso + ferias.proximas).take(3)
            if (siguientes.isEmpty()) {
                Text("No hay ferias publicadas por venir. Se crean y publican en el panel web.", color = TextoSecundario, fontSize = 15.sp)
            } else {
                GrupoFilas {
                    siguientes.forEachIndexed { i, f ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        FilaFeria(f, enCurso = f in ferias.enCurso) { nav.irA(Ruta.FeriaPublica(f.id)) }
                    }
                }
            }

            // 3. Áreas de gestión
            EncabezadoSeccion("Áreas de gestión", sobretitulo = "Administrar")
            com.example.trabajodea10.ui.componentes.ParAdaptable(
                { m -> MosaicoAccion("Emprendedoras", Icons.Filled.People, Tono.MUSGO, { nav.seleccionarTab(Ruta.AdminEmprendedoras) },
                    m, detalle = "${resumen.total} cuentas", insignia = resumen.pendientes.takeIf { it > 0 }?.toString()) },
                { m -> MosaicoAccion("Ferias", Icons.Filled.CalendarMonth, Tono.AZUL, { nav.seleccionarTab(Ruta.AdminFerias) },
                    m, detalle = "${ferias.proximas.size} próximas") }
            )
            com.example.trabajodea10.ui.componentes.ParAdaptable(
                { m -> MosaicoAccion("Asociaciones", Icons.Filled.Groups, Tono.AMBAR, { nav.irA(Ruta.AdminAsociaciones) },
                    m, detalle = "${VitrinaStore.asociaciones.size} registradas") },
                { m -> MosaicoAccion("Tipos de producto", Icons.Filled.Category, Tono.CORTEZA, { nav.irA(Ruta.AdminTipos) },
                    m, detalle = "${AdminStore.tipos.count { it.activo }} activos") }
            )
            com.example.trabajodea10.ui.componentes.ParAdaptable(
                { m -> MosaicoAccion("Administradores", Icons.Filled.SupervisorAccount, Tono.NEUTRO, { nav.irA(Ruta.AdminEquipo) },
                    m, detalle = "${AdminStore.admins.size} cuentas") },
                { m -> MosaicoAccion("Bitácora", Icons.Filled.History, Tono.OK, { nav.irA(Ruta.AdminBitacora) },
                    m, detalle = "Quién cambió qué") }
            )

            // 4. Actividad reciente (bitácora del servidor)
            if (AdminStore.bitacora.isNotEmpty()) {
                EncabezadoSeccion("Actividad reciente", sobretitulo = "Bitácora", accion = "Ver todo", onAccion = { nav.irA(Ruta.AdminBitacora) })
                GrupoFilas {
                    AdminStore.bitacora.take(4).forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        FilaBitacora(e)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Fila de una feria con su bloque de fecha, lugar y estado. */
@Composable
internal fun FilaFeria(f: FeriaPublica, enCurso: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClickLabel = "Ver ${f.nombre}", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (dia, mes) = FechasAdmin.diaMes(f.fechaInicio)
        BloqueFecha(dia, mes)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(f.nombre, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = NegroApple, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(f.lugar, f.horario).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { com.example.trabajodea10.data.ReglasVitrina.rangoFechas(f) },
                fontSize = 14.sp, color = TextoSecundario, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        PildoraEstado(
            when { f.estado == "CANCELADA" -> "Cancelada"; enCurso -> "Hoy"; f.estado == "FINALIZADA" -> "Finalizada"; else -> "Publicada" },
            when { f.estado == "CANCELADA" -> Tono.CORTEZA; enCurso -> Tono.OK; else -> Tono.MUSGO }
        )
    }
}

/** Fila de bitácora: acción, quién y cuándo. */
@Composable
internal fun FilaBitacora(e: com.example.trabajodea10.data.EntradaBitacora) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {}) {
        Text(e.accion, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = NegroApple)
        Text("${e.quien} · ${FechasAdmin.momento(e.en)}", fontSize = 14.sp, color = TextoSecundario)
    }
}

/** Fechas legibles en español de Chile para el panel. */
internal object FechasAdmin {
    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val MESES_LARGOS = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    fun diaMes(iso: String?): Pair<String, String> {
        val p = iso?.take(10)?.split("-") ?: return "?" to "fecha"
        val mes = p.getOrNull(1)?.toIntOrNull()?.let { MESES.getOrNull(it - 1) } ?: return "?" to "fecha"
        return (p.getOrNull(2)?.trimStart('0') ?: "?") to mes
    }

    fun hoyLegible(iso: String): String {
        val p = iso.split("-")
        val mes = p.getOrNull(1)?.toIntOrNull()?.let { MESES_LARGOS.getOrNull(it - 1) } ?: return iso
        return "${p.getOrNull(2)?.trimStart('0')} de $mes"
    }

    /** «24 nov · 10:12» a partir de una marca ISO (sin convertir zona: así la entrega el servidor). */
    fun momento(iso: String): String {
        if (iso.length < 10) return iso
        val (dia, mes) = diaMes(iso)
        val hora = iso.substringAfter('T', "").take(5)
        return if (hora.length == 5) "$dia $mes · $hora" else "$dia $mes"
    }
}

// Se conserva el formato compacto de ferias del celular (lo usa el detalle de ferias de ejemplo).
@Composable
fun FeriaCompacta(feria: Feria) {
    Tarjeta {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(feria.nombre, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = NegroApple)
                ChipEstado(texto = feria.estado.etiqueta, color = colorDeFeria(feria.estado))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("${feria.ubicacion} · ${rangoDeFechas(feria.fechaInicio, feria.fechaFin)}", style = MaterialTheme.typography.bodySmall, color = GrisTexto)
        }
    }
}

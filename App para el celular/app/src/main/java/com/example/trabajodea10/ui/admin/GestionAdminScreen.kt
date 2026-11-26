package com.example.trabajodea10.ui.admin

import androidx.core.net.toUri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trabajodea10.data.AdminStore
import com.example.trabajodea10.data.NavegacionAnonima
import com.example.trabajodea10.data.ReglasAdmin
import com.example.trabajodea10.data.SesionManager
import com.example.trabajodea10.data.VitrinaStore
import com.example.trabajodea10.ui.componentes.AvisoAccion
import com.example.trabajodea10.ui.componentes.BarraSuperior
import com.example.trabajodea10.ui.componentes.CabeceraMarca
import com.example.trabajodea10.ui.componentes.EncabezadoSeccion
import com.example.trabajodea10.ui.componentes.EstadoCargando
import com.example.trabajodea10.ui.componentes.FilaGestion
import com.example.trabajodea10.ui.componentes.GrupoFilas
import com.example.trabajodea10.ui.componentes.IconoTono
import com.example.trabajodea10.ui.componentes.PildoraEstado
import com.example.trabajodea10.ui.componentes.Tono
import com.example.trabajodea10.ui.componentes.Vacio
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.theme.FondoLimpio
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.TextoSecundario

/** Dirección del panel web de administración (la misma que ya usaba «Ferias»). */
internal const val URL_PANEL_WEB = "https://diego-733.github.io/EmprendedoresPuertoMontt/"

internal fun abrirPanelWeb(contexto: android.content.Context) {
    runCatching {
        contexto.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, URL_PANEL_WEB.toUri()))
    }
}

/*
 * «Gestión» (rediseño v2): todas las áreas de administración agrupadas por tema, con cifras reales.
 * En el celular se CONSULTA; crear, editar y borrar asociaciones, tipos y administradores se hace en
 * el panel web (como hasta ahora). Nada de esto cambia permisos: el servidor sigue exigiendo es_admin().
 */
@Composable
fun PantallaAdminGestion(nav: ControladorNavegacion) {
    val contexto = LocalContext.current
    LaunchedEffect(Unit) {
        if (!AdminStore.cargadoAlgunaVez) AdminStore.cargar()
        VitrinaStore.cargar()
    }
    val resumen = ReglasAdmin.resumen(AdminStore.cuentas)
    Column(Modifier.fillMaxSize().background(FondoLimpio).verticalScroll(rememberScrollState())) {
        CabeceraMarca(
            sobretitulo = "Administración",
            titulo = "Gestión",
            subtitulo = "Todas las áreas de Alerce en un solo lugar"
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (AdminStore.sonEjemplo) AvisoAccion("Datos de ejemplo: esta versión no está conectada al servidor.", Tono.AMBAR)

            EncabezadoSeccion("Personas", sobretitulo = "Cuentas")
            GrupoFilas {
                FilaGestion("Emprendedoras", Icons.Filled.People, Tono.MUSGO, { nav.seleccionarTab(Ruta.AdminEmprendedoras) },
                    detalle = "${resumen.total} cuentas · ${resumen.activas} activas",
                    insignia = resumen.pendientes.takeIf { it > 0 }?.let { "$it por registrarse" })
                Separador()
                FilaGestion("Administradores", Icons.Filled.SupervisorAccount, Tono.NEUTRO, { nav.irA(Ruta.AdminEquipo) },
                    detalle = "${AdminStore.admins.size} con acceso a este panel")
            }

            EncabezadoSeccion("Vitrina y ferias", sobretitulo = "Contenido")
            GrupoFilas {
                FilaGestion("Ferias", Icons.Filled.CalendarMonth, Tono.AZUL, { nav.seleccionarTab(Ruta.AdminFerias) },
                    detalle = "${VitrinaStore.ferias.size} en el servidor")
                Separador()
                FilaGestion("Asociaciones", Icons.Filled.Groups, Tono.AMBAR, { nav.irA(Ruta.AdminAsociaciones) },
                    detalle = "${VitrinaStore.asociaciones.size} registradas")
                Separador()
                FilaGestion("Tipos de producto", Icons.Filled.Category, Tono.CORTEZA, { nav.irA(Ruta.AdminTipos) },
                    detalle = "${AdminStore.tipos.count { it.activo }} activos de ${AdminStore.tipos.size}")
            }

            EncabezadoSeccion("Control", sobretitulo = "Seguridad y registro")
            GrupoFilas {
                FilaGestion("Bitácora", Icons.Filled.History, Tono.OK, { nav.irA(Ruta.AdminBitacora) },
                    detalle = "Quién cambió qué, y cuándo")
                Separador()
                // Configuración de la app sin cuenta: se lee aquí y se cambia en la web (W12).
                FilaGestion("App sin cuenta", Icons.Filled.PublicOff, Tono.NEUTRO, { abrirPanelWeb(contexto) },
                    detalle = "Hoy: " + when (VitrinaStore.navegacion) {
                        NavegacionAnonima.PERMITIR -> "se puede comprar sin cuenta"
                        NavegacionAnonima.INVITAR -> "se invita a entrar con Google"
                        NavegacionAnonima.OBLIGAR -> "hay que entrar con Google"
                    } + " · se cambia en la web")
            }

            EncabezadoSeccion("Herramientas", sobretitulo = "Cuenta y diagnóstico")
            GrupoFilas {
                FilaGestion("Ajustes y mi cuenta", Icons.Filled.Settings, Tono.NEUTRO, { nav.irA(Ruta.Ajustes) },
                    detalle = "Accesibilidad, voz, privacidad y cerrar sesión")
                Separador()
                FilaGestion("Diagnóstico técnico", Icons.Filled.VerifiedUser, Tono.MUSGO, { nav.irA(Ruta.Ajustes) },
                    detalle = "Protección de la app (App Check), al final de Ajustes")
                Separador()
                FilaGestion("Abrir el panel web", Icons.AutoMirrored.Filled.OpenInNew, Tono.AZUL, { abrirPanelWeb(contexto) },
                    detalle = "Para crear, editar o borrar ferias, asociaciones, tipos y administradores")
            }
            AvisoAccion(
                "En el celular se consulta y se autorizan correos. Lo demás se edita en el panel web, con los mismos permisos de siempre.",
                Tono.AZUL, icono = Icons.Filled.Info
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Separador() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 74.dp, end = 16.dp))

/** Fila simple de una lista de consulta. */
@Composable
private fun FilaConsulta(titulo: String, detalle: String?, estado: String?, tono: Tono, icono: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconoTono(icono, tono, tamano = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NegroApple)
            if (!detalle.isNullOrBlank()) Text(detalle, fontSize = 14.sp, color = TextoSecundario)
        }
        if (estado != null) { Spacer(Modifier.width(8.dp)); PildoraEstado(estado, if (estado == "Inactiva" || estado == "Inactivo" || estado == "Sin acceso") Tono.NEUTRO else Tono.OK) }
    }
}

@Composable
private fun PantallaConsulta(titulo: String, subtitulo: String, nav: ControladorNavegacion, contenido: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val contexto = LocalContext.current
    Column(Modifier.fillMaxSize().background(FondoLimpio)) {
        BarraSuperior(titulo = titulo, subtitulo = subtitulo, onVolver = { nav.volver() })
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (AdminStore.sonEjemplo) item { AvisoAccion("Datos de ejemplo: esta versión no está conectada al servidor.", Tono.AMBAR) }
            contenido()
            item {
                AvisoAccion("Para crear, editar o desactivar, usa el panel web.", Tono.AZUL, icono = Icons.Filled.Info,
                    accion = "Abrir", onAccion = { abrirPanelWeb(contexto) })
            }
        }
    }
}

@Composable
fun PantallaAdminAsociaciones(nav: ControladorNavegacion) {
    LaunchedEffect(Unit) { VitrinaStore.cargar() }
    val conteo = ReglasAdmin.integrantesPorAsociacion(VitrinaStore.pertenencias, SesionManager.tiendas.toList())
    PantallaConsulta("Asociaciones", "${VitrinaStore.asociaciones.size} registradas", nav) {
        if (VitrinaStore.asociaciones.isEmpty()) item { Vacio(titulo = "Sin asociaciones", mensaje = "Todavía no hay asociaciones en el servidor.") }
        else item {
            GrupoFilas {
                VitrinaStore.asociaciones.forEachIndexed { i, a ->
                    if (i > 0) Separador()
                    FilaGestion(
                        a.nombre, Icons.Filled.Groups, Tono.AMBAR, { nav.irA(Ruta.AsociacionPublica(a.id)) },
                        detalle = listOf(a.sector, a.localidad).filter { it.isNotBlank() }.joinToString(" · ")
                            .let { if (it.isBlank()) "" else "$it · " } + "${conteo[a.id] ?: 0} tiendas" + if (!a.activa) " · inactiva" else ""
                    )
                }
            }
        }
    }
}

@Composable
fun PantallaAdminTipos(nav: ControladorNavegacion) {
    LaunchedEffect(Unit) { if (!AdminStore.cargadoAlgunaVez) AdminStore.cargar() }
    val porCategoria = AdminStore.tipos.groupBy { it.categoria.ifBlank { "Sin categoría" } }
    PantallaConsulta("Tipos de producto", "${AdminStore.tipos.count { it.activo }} activos de ${AdminStore.tipos.size}", nav) {
        if (AdminStore.cargando && AdminStore.tipos.isEmpty()) item { EstadoCargando("Trayendo los tipos…") }
        porCategoria.forEach { (categoria, lista) ->
            item { EncabezadoSeccion(categoria) }
            item {
                GrupoFilas {
                    lista.forEachIndexed { i, t ->
                        if (i > 0) Separador()
                        FilaConsulta(t.nombre, if (AdminStore.sonEjemplo) null else "${t.productos} productos lo usan",
                            if (t.activo) "Activo" else "Inactivo", Tono.CORTEZA, Icons.Filled.Category)
                    }
                }
            }
        }
    }
}

@Composable
fun PantallaAdminEquipo(nav: ControladorNavegacion) {
    LaunchedEffect(Unit) { if (!AdminStore.cargadoAlgunaVez) AdminStore.cargar() }
    PantallaConsulta("Administradores", "${AdminStore.admins.size} cuentas con acceso", nav) {
        if (AdminStore.admins.isEmpty()) item { Vacio(titulo = "Sin datos", mensaje = "No se pudo leer la lista de administradores.") }
        else item {
            GrupoFilas {
                AdminStore.admins.forEachIndexed { i, a ->
                    if (i > 0) Separador()
                    FilaConsulta(
                        a.nombre.ifBlank { a.email } + if (a.esYo) " (tú)" else "", a.email,
                        when (a.estado) { "ACTIVA" -> "Activa"; "PENDIENTE_REGISTRO" -> "Falta que entre"; else -> "Sin acceso" },
                        Tono.NEUTRO, Icons.Filled.SupervisorAccount
                    )
                }
            }
        }
    }
}

@Composable
fun PantallaAdminBitacora(nav: ControladorNavegacion) {
    LaunchedEffect(Unit) { if (!AdminStore.cargadoAlgunaVez) AdminStore.cargar() }
    PantallaConsulta("Bitácora", "Últimos ${AdminStore.bitacora.size} cambios registrados", nav) {
        if (AdminStore.bitacora.isEmpty()) item { Vacio(titulo = "Sin movimientos", mensaje = "Aún no hay cambios registrados en la bitácora.") }
        else items(AdminStore.bitacora.chunked(8)) { bloque ->
            GrupoFilas {
                bloque.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    FilaBitacora(e)
                }
            }
        }
    }
}

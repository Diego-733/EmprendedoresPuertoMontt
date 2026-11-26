package com.example.trabajodea10.ui.navegacion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import com.example.trabajodea10.data.Rol
import com.example.trabajodea10.data.SesionManager
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.ui.theme.Berenjena
import com.example.trabajodea10.ui.theme.SuperficieBlanca
import com.example.trabajodea10.ui.theme.TextoSecundario

sealed class Ruta(val grafica: String? = null) {

    data object Login : Ruta()

    data object AdminInicio : Ruta("admin_inicio")
    data object AdminFerias : Ruta("admin_ferias")
    data object AdminEmprendedoras : Ruta("admin_emprendedoras")
    // Rediseño v2: áreas de gestión (consulta con datos reales; se edita en el panel web).
    data object AdminGestion : Ruta("admin_gestion")
    data object AdminAsociaciones : Ruta()
    data object AdminTipos : Ruta()
    data object AdminEquipo : Ruta()
    data object AdminBitacora : Ruta()
    data object Ajustes : Ruta("ajustes")
    data class FeriaDetalle(val feriaId: String) : Ruta()

    data object EmpInicio : Ruta("emp_inicio")
    data object EmpCatalogo : Ruta("emp_catalogo")
    data object EmpVenta : Ruta("emp_venta")
    data object PagoVenta : Ruta()
    data object EmpFerias : Ruta("emp_ferias")
    data object EmpTienda : Ruta("emp_tienda")
    data class EmpTiendaQR(val tiendaId: String) : Ruta()
    data class ProductoDetalle(val productoId: String) : Ruta()
    data object VistaPrevia : Ruta()
    data object HistorialVentas : Ruta()
    data object ConfigVentas : Ruta()

    data object VisInicio : Ruta("vis_inicio")
    data object VisProductos : Ruta("vis_productos")
    data object VisAyuda : Ruta("vis_ayuda")

    // Entrega 6: modo comprador, difusión y mapas
    data object VisMarketplaceClasico : Ruta()
    data class TiendaPublica(val tiendaId: String) : Ruta()
    data class ProductoPublico(val productoId: String) : Ruta()
    data class AsociacionPublica(val asociacionId: String) : Ruta()
    data class FeriaPublica(val feriaId: String) : Ruta()
    data object ConfigVitrina : Ruta()
    data object BandejaContactos : Ruta()

    // Asistente Alerce (enséñame / ayúdame a hacerlo): pantalla opcional, sin barra inferior.
    data object Asistente : Ruta()
    // Centro de ayuda por tareas (los mismos temas que «Enséñame» del asistente).
    data object CentroAyuda : Ruta()
}

/** Estado de la pantalla de entrada sin sesión (RF-ACC-06). */
object AccesoApp {
    /** true: mostrar el ingreso con correo en vez de la opción del administrador. */
    var mostrarLogin by androidx.compose.runtime.mutableStateOf(false)
}

data class ItemNavegacion(
    val ruta: Ruta,
    val etiqueta: String,
    val icono: ImageVector
)

fun itemsSegunRol(rol: Rol): List<ItemNavegacion> = when (rol) {
    Rol.ADMIN -> listOf(
        ItemNavegacion(Ruta.AdminInicio, "Inicio", Icons.Filled.Home),
        ItemNavegacion(Ruta.AdminFerias, "Ferias", Icons.Filled.CalendarMonth),
        ItemNavegacion(Ruta.AdminEmprendedoras, "Emprendedoras", Icons.Filled.People),
        // Ajustes sigue a un toque desde la cabecera y dentro de «Gestión».
        ItemNavegacion(Ruta.AdminGestion, "Gestión", Icons.Filled.Dashboard)
    )
    Rol.EMPRENDEDORA -> listOf(
        ItemNavegacion(Ruta.EmpInicio, "Inicio", Icons.Filled.Home),
        ItemNavegacion(Ruta.EmpCatalogo, "Productos", Icons.Filled.Inventory2),
        ItemNavegacion(Ruta.EmpVenta, "Venta", Icons.Filled.ShoppingCart),
        ItemNavegacion(Ruta.EmpFerias, "Ferias", Icons.Filled.CalendarMonth),
        ItemNavegacion(Ruta.EmpTienda, "Tienda", Icons.Filled.Store)
    )
    Rol.VISITANTE -> listOf(
        ItemNavegacion(Ruta.VisProductos, "Vitrina", Icons.Filled.Store),
        ItemNavegacion(Ruta.VisInicio, "Mapa ferias", Icons.Filled.CalendarMonth),
        ItemNavegacion(Ruta.VisAyuda, "Ayuda", Icons.Filled.Phone),
        ItemNavegacion(Ruta.Ajustes, "Mi perfil", Icons.Filled.Person)
    )
}

/** Ficha de la tienda o asociación de una acción pendiente. */
fun rutaDeFicha(p: com.example.trabajodea10.data.AccionPendiente): Ruta {
    val (tipo, id) = com.example.trabajodea10.data.ReglasSeguir.fichaDe(p)
    return if (tipo == "TIENDA") Ruta.TiendaPublica(id) else Ruta.AsociacionPublica(id)
}

class ControladorNavegacion(rutaInicial: Ruta = Ruta.Login) {

    val pila = mutableStateListOf<Ruta>(rutaInicial)

    val actual: Ruta get() = pila.lastOrNull() ?: Ruta.Login

    fun irA(ruta: Ruta) {
        if (actual != ruta) pila.add(ruta)
    }

    fun volver() {
        if (pila.size <= 1) return
        val saliente = pila.lastOrNull() ?: return
        pila.removeAt(pila.lastIndex)
        when (saliente) {
            is Ruta.FeriaDetalle -> TramaStore.descartarSiEstaVacio(saliente.feriaId)
            is Ruta.ProductoDetalle -> TramaStore.descartarSiEstaVacio(saliente.productoId)
            else -> Unit
        }
    }

    fun seleccionarTab(ruta: Ruta) {
        if (actual == ruta) return
        if (pila.isNotEmpty()) {
            pila[0] = ruta
            while (pila.size > 1) {
                pila.removeAt(pila.lastIndex)
            }
        } else {
            pila.add(ruta)
        }
    }

    fun iniciarSesion(rol: Rol) {
        TramaStore.iniciarSesion(rol)
        // RF-ACC-08: la acción pendiente NO se descarta aquí. Si Android recrea la pantalla al
        // volver del navegador, la vitrina anónima se abre antes de que termine el ingreso con
        // Google; la acción se cancela con «Ahora no», al vencer (15 min) o al cerrar sesión.
        val destino = when (rol) {
            Rol.ADMIN -> Ruta.AdminInicio
            Rol.EMPRENDEDORA -> Ruta.EmpInicio
            Rol.VISITANTE -> Ruta.VisProductos
        }
        if (pila.isNotEmpty()) {
            pila[0] = destino
            while (pila.size > 1) {
                pila.removeAt(pila.lastIndex)
            }
        } else {
            pila.add(destino)
        }
        // Con sesión, se vuelve a la ficha que se quería seguir (tienda o asociación).
        if (com.example.trabajodea10.data.remoto.ClienteSupabase.sesion != null) {
            com.example.trabajodea10.data.FeriasStore.fichaPendiente()?.let { irA(rutaDeFicha(it)) }
        }
    }

    /** Desde el modo comprador sin cuenta: ir a la pantalla de ingreso con correo. */
    fun irAIngreso() {
        AccesoApp.mostrarLogin = true
        com.example.trabajodea10.ui.asistente.AsistenteMemoria.olvidar()
        TramaStore.cerrarSesion()
        if (pila.isNotEmpty()) {
            pila[0] = Ruta.Login
            while (pila.size > 1) pila.removeAt(pila.lastIndex)
        } else {
            pila.add(Ruta.Login)
        }
    }

    fun cerrarSesion() {
        AccesoApp.mostrarLogin = true
        // La conversación del asistente no pasa a la próxima persona que use este celular.
        com.example.trabajodea10.ui.asistente.AsistenteMemoria.olvidar()
        // Tampoco los datos de administración.
        com.example.trabajodea10.data.AdminStore.limpiar()
        // Cierra también la sesión del servidor (o la conserva si la huella está activada)
        SesionManager.cerrarSesion()
        if (pila.isNotEmpty()) {
            pila[0] = Ruta.Login
            while (pila.size > 1) {
                pila.removeAt(pila.lastIndex)
            }
        } else {
            pila.add(Ruta.Login)
        }
    }
}

private fun rutaInicialDe(rol: Rol?): Ruta = when (rol) {
    Rol.ADMIN -> Ruta.AdminInicio
    Rol.EMPRENDEDORA -> Ruta.EmpInicio
    // Sin sesión en el servidor, cada vez que se abre la app se vuelve a aplicar la
    // navegación anónima del administrador (A08). Nunca se abre en el mapa: el permiso
    // de ubicación se pide solo al entrar a esa pantalla (A15).
    Rol.VISITANTE -> if (com.example.trabajodea10.data.remoto.ClienteSupabase.sesion == null) Ruta.Login else Ruta.VisProductos
    null -> Ruta.Login
}

/**
 * Conversión de una ruta a texto y de vuelta, para guardar la pila cuando Android recrea la pantalla
 * (girar el celular, cambiar el tamaño de letra o el modo oscuro, o cuando el sistema cierra la app
 * en segundo plano). Solo se guardan nombres de pantalla e ids, nunca datos de la persona.
 */
object CodigoRuta {
    private val SIMPLES: Map<String, Ruta> = listOf(
        Ruta.Login, Ruta.AdminInicio, Ruta.AdminFerias, Ruta.AdminEmprendedoras, Ruta.Ajustes,
        Ruta.AdminGestion, Ruta.AdminAsociaciones, Ruta.AdminTipos, Ruta.AdminEquipo, Ruta.AdminBitacora,
        Ruta.EmpInicio, Ruta.EmpCatalogo, Ruta.EmpVenta, Ruta.PagoVenta, Ruta.EmpFerias, Ruta.EmpTienda,
        Ruta.VistaPrevia, Ruta.HistorialVentas, Ruta.ConfigVentas, Ruta.VisInicio, Ruta.VisProductos, Ruta.VisAyuda,
        Ruta.VisMarketplaceClasico, Ruta.ConfigVitrina, Ruta.BandejaContactos, Ruta.Asistente, Ruta.CentroAyuda
    ).associateBy { it.toString() }

    fun aTexto(r: Ruta): String = when (r) {
        is Ruta.FeriaDetalle -> "FeriaDetalle|${r.feriaId}"
        is Ruta.EmpTiendaQR -> "EmpTiendaQR|${r.tiendaId}"
        is Ruta.ProductoDetalle -> "ProductoDetalle|${r.productoId}"
        is Ruta.TiendaPublica -> "TiendaPublica|${r.tiendaId}"
        is Ruta.ProductoPublico -> "ProductoPublico|${r.productoId}"
        is Ruta.AsociacionPublica -> "AsociacionPublica|${r.asociacionId}"
        is Ruta.FeriaPublica -> "FeriaPublica|${r.feriaId}"
        else -> r.toString()
    }

    fun desdeTexto(t: String): Ruta? {
        val partes = t.split("|", limit = 2)
        val id = partes.getOrNull(1)
        return when (partes[0]) {
            "FeriaDetalle" -> id?.let { Ruta.FeriaDetalle(it) }
            "EmpTiendaQR" -> id?.let { Ruta.EmpTiendaQR(it) }
            "ProductoDetalle" -> id?.let { Ruta.ProductoDetalle(it) }
            "TiendaPublica" -> id?.let { Ruta.TiendaPublica(it) }
            "ProductoPublico" -> id?.let { Ruta.ProductoPublico(it) }
            "AsociacionPublica" -> id?.let { Ruta.AsociacionPublica(it) }
            "FeriaPublica" -> id?.let { Ruta.FeriaPublica(it) }
            else -> SIMPLES[t]
        }
    }

    /** Cuenta y rol a los que pertenece la pila: si cambiaron, no se restaura (nunca se muestra lo de otra cuenta). */
    fun firmaSesion(): String = "${TramaStore.cuentaActual}|${TramaStore.rol}"

    /**
     * Pila para guardar (la firma va primero). Al restaurar, se descarta todo si cambió la sesión,
     * y se quitan las pantallas que ya no tienen sentido (pago sin productos, producto que ya no existe).
     */
    fun guardar(pila: List<Ruta>): ArrayList<String> = ArrayList(listOf(firmaSesion()) + pila.map(::aTexto))

    fun restaurar(guardado: List<String>, inicial: Ruta, valida: (Ruta) -> Boolean = ::rutaValida): List<Ruta> {
        if (guardado.isEmpty() || guardado.first() != firmaSesion()) return listOf(inicial)
        val rutas = guardado.drop(1).mapNotNull(::desdeTexto)
        val raiz = rutas.firstOrNull() ?: return listOf(inicial)
        // La pantalla de entrada nunca queda debajo de otra; y sin sesión solo vale la de entrada.
        if (raiz == Ruta.Login && rutas.size > 1) return listOf(inicial)
        return listOf(raiz) + rutas.drop(1).filter(valida)
    }

    private fun rutaValida(r: Ruta): Boolean = when (r) {
        Ruta.PagoVenta -> TramaStore.carritoVenta.isNotEmpty()
        is Ruta.ProductoDetalle -> TramaStore.borrador?.producto?.id == r.productoId || TramaStore.productoPorId(r.productoId) != null
        else -> true
    }
}

/**
 * La pila de pantallas sobrevive cuando Android recrea la actividad (antes volvía a Inicio).
 * No se guarda nada de la persona: solo nombres de pantalla e ids, ligados a la sesión.
 */
@Composable
fun rememberControladorNavegacion(): ControladorNavegacion =
    androidx.compose.runtime.saveable.rememberSaveable(
        saver = androidx.compose.runtime.saveable.Saver<ControladorNavegacion, ArrayList<String>>(
            save = { CodigoRuta.guardar(it.pila) },
            restore = { guardado ->
                val inicial = rutaInicialDe(TramaStore.rol)
                val rutas = CodigoRuta.restaurar(guardado, inicial)
                ControladorNavegacion(rutas.first()).also { c -> rutas.drop(1).forEach { c.pila.add(it) } }
            }
        )
    ) { ControladorNavegacion(rutaInicialDe(TramaStore.rol)) }

@Composable
fun BarraInferior(
    rol: Rol,
    rutaActual: Ruta,
    onSeleccionar: (Ruta) -> Unit
) {
    val items = itemsSegunRol(rol)
    // Escala común de las etiquetas; se recalcula si cambia la letra, el ancho de la barra (girar el celular) o el rol.
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    var anchoBarra by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var escalaEtiquetas by remember(fontScale, anchoBarra, rol) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    NavigationBar(
        containerColor = SuperficieBlanca,
        modifier = androidx.compose.ui.Modifier.onSizeChanged { anchoBarra = it.width }
    ) {
        items.forEach { item ->
            val seleccionado = rutaActual.grafica != null && rutaActual.grafica == item.ruta.grafica
            NavigationBarItem(
                selected = seleccionado,
                onClick = { onSeleccionar(item.ruta) },
                // La etiqueta ya nombra el botón (sin duplicarla para TalkBack).
                icon = { Icon(item.icono, contentDescription = null) },
                label = {
                    // Entrega 8 (RNF-USA-03): una sola línea, sin partir la palabra («Producto / s») ni
                    // empujar el ícono. La etiqueta crece con la letra del celular todo lo que cabe: si una
                    // no cabe, las cinco se achican juntas (mismo tamaño) en vez de cortarse («Pro…»). Solo
                    // si ya no se pueden achicar más (mitad del tamaño) se acortan con «…».
                    val base = androidx.compose.material3.MaterialTheme.typography.labelSmall
                    Text(
                        text = item.etiqueta,
                        style = base.copy(fontSize = base.fontSize * escalaEtiquetas),
                        maxLines = 1,
                        softWrap = false,
                        overflow = if (escalaEtiquetas > 0.5f) androidx.compose.ui.text.style.TextOverflow.Clip
                            else androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        fontWeight = if (seleccionado) FontWeight.SemiBold else FontWeight.Normal,
                        onTextLayout = { r -> if (r.hasVisualOverflow && escalaEtiquetas > 0.5f) escalaEtiquetas *= 0.92f }
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    // Rediseño v2: pestaña activa en bosque con ícono blanco (antes, pastilla verde clara).
                    selectedIconColor = androidx.compose.ui.graphics.Color.White,
                    selectedTextColor = com.example.trabajodea10.ui.theme.Bosque,
                    indicatorColor = com.example.trabajodea10.ui.theme.Bosque,
                    unselectedIconColor = TextoSecundario,
                    unselectedTextColor = TextoSecundario
                )
            )
        }
    }
}


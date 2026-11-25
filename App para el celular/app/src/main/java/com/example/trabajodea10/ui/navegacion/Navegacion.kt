package com.example.trabajodea10.ui.navegacion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
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
        ItemNavegacion(Ruta.Ajustes, "Ajustes", Icons.Filled.Settings)
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

@Composable
fun rememberControladorNavegacion(): ControladorNavegacion =
    remember { ControladorNavegacion(rutaInicialDe(TramaStore.rol)) }

@Composable
fun BarraInferior(
    rol: Rol,
    rutaActual: Ruta,
    onSeleccionar: (Ruta) -> Unit
) {
    val items = itemsSegunRol(rol)
    NavigationBar(containerColor = SuperficieBlanca) {
        items.forEach { item ->
            val seleccionado = rutaActual.grafica != null && rutaActual.grafica == item.ruta.grafica
            NavigationBarItem(
                selected = seleccionado,
                onClick = { onSeleccionar(item.ruta) },
                // La etiqueta ya nombra el botón (sin duplicarla para TalkBack).
                icon = { Icon(item.icono, contentDescription = null) },
                label = {
                    // Entrega 8 (RNF-USA-03): una sola línea; con letra grande se acorta con «…»
                    // en vez de partir la palabra («Producto / s») y empujar el ícono.
                    Text(
                        text = item.etiqueta,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        fontWeight = if (seleccionado) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Berenjena,
                    selectedTextColor = Berenjena,
                    indicatorColor = com.example.trabajodea10.ui.theme.BerenjenaContenedor,
                    unselectedIconColor = TextoSecundario,
                    unselectedTextColor = TextoSecundario
                )
            )
        }
    }
}


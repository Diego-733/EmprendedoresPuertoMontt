package com.example.trabajodea10

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.ui.admin.PantallaAdminEmprendedoras
import com.example.trabajodea10.ui.admin.PantallaAdminFerias
import com.example.trabajodea10.ui.admin.PantallaAdminInicio
import com.example.trabajodea10.ui.admin.PantallaFeriaDetalle
import com.example.trabajodea10.ui.ajustes.PantallaAjustes
import com.example.trabajodea10.ui.auth.PantallaLogin
import com.example.trabajodea10.ui.emprendedora.PantallaCatalogo
import com.example.trabajodea10.ui.emprendedora.PantallaEmprendedoraInicio
import com.example.trabajodea10.ui.emprendedora.PantallaMisFerias
import com.example.trabajodea10.ui.emprendedora.PantallaPagoVenta
import com.example.trabajodea10.ui.emprendedora.PantallaProductoDetalle
import com.example.trabajodea10.ui.emprendedora.PantallaTienda
import com.example.trabajodea10.ui.emprendedora.PantallaTiendaQR
import com.example.trabajodea10.ui.emprendedora.PantallaVenta
import com.example.trabajodea10.ui.navegacion.BarraInferior
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.navegacion.rememberControladorNavegacion
import com.example.trabajodea10.ui.publico.PantallaMarketplace
import com.example.trabajodea10.ui.publico.PantallaVisAyuda
import com.example.trabajodea10.ui.publico.PantallaVisFerias
import com.example.trabajodea10.ui.publico.PantallaVisProductos
import com.example.trabajodea10.ui.theme.Berenjena
import com.example.trabajodea10.ui.theme.FondoLimpio
import kotlinx.coroutines.launch

@Composable
fun TramaApp() {
    val nav = rememberControladorNavegacion()
    val snackbarHostState = remember { SnackbarHostState() }
    val ambito = rememberCoroutineScope()
    val avisar: (String) -> Unit = { mensaje ->
        ambito.launch { snackbarHostState.showSnackbar(mensaje) }
    }

    val rol = TramaStore.rol
    val ruta = nav.actual

    // Con una sesión guardada, al abrir se sube lo pendiente y se trae lo nuevo del servidor.
    androidx.compose.runtime.LaunchedEffect(Unit) { com.example.trabajodea10.data.SesionManager.actualizarAlAbrir() }

    // Regreso del ingreso con Google (enlace com.company.ultrapp://login-callback).
    val rolOAuth = com.example.trabajodea10.data.SesionManager.rolPorOAuth
    androidx.compose.runtime.LaunchedEffect(rolOAuth) {
        if (rolOAuth != null) {
            com.example.trabajodea10.ui.navegacion.AccesoApp.mostrarLogin = false
            nav.iniciarSesion(rolOAuth)
            com.example.trabajodea10.data.SesionManager.rolPorOAuth = null
            avisar("¡Bienvenida! Ingresaste con Google.")
        }
    }

    // Entrega 7: al volver de iniciar sesión, se abre lo que se quería seguir (ya seguido).
    val completada = com.example.trabajodea10.data.FeriasStore.accionRecienCompletada
    androidx.compose.runtime.LaunchedEffect(completada) {
        if (completada != null) {
            nav.irA(com.example.trabajodea10.ui.navegacion.rutaDeFicha(completada))   // irA no duplica si ya está ahí
            avisar("Ahora sigues a ${completada.nombre}.")
            com.example.trabajodea10.data.FeriasStore.accionRecienCompletada = null
        }
    }
    // Al tocar un aviso: la emprendedora va a sus ferias; quien compra, a la ficha de la feria.
    val feriaAviso = com.example.trabajodea10.data.FeriasStore.feriaDesdeAviso
    androidx.compose.runtime.LaunchedEffect(feriaAviso, rol) {
        if (feriaAviso != null && rol != null) {
            if (rol == com.example.trabajodea10.data.Rol.EMPRENDEDORA) nav.seleccionarTab(Ruta.EmpFerias) else nav.irA(Ruta.FeriaPublica(feriaAviso))
            com.example.trabajodea10.data.FeriasStore.feriaDesdeAviso = null
        }
    }

    BackHandler(enabled = nav.pila.size > 1) {
        nav.volver()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = FondoLimpio,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (rol != null && ruta.grafica != null) {
                BarraInferior(
                    rol = rol,
                    rutaActual = ruta,
                    onSeleccionar = { nav.seleccionarTab(it) }
                )
            }
        },
        floatingActionButton = {
            FabSegunRuta(ruta = ruta, nav = nav)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ContenidoSegunRuta(ruta = ruta, nav = nav, avisar = avisar)
        }
    }
}

@Composable
private fun FabSegunRuta(
    ruta: Ruta,
    nav: ControladorNavegacion
) {
    when (ruta) {
        // Entrega 7: las ferias se crean en la web (datos del servidor), no en el celular.
        Ruta.AdminFerias -> Unit

        Ruta.EmpInicio -> Fab(
            descripcion = "Registrar venta",
            icono = { Icon(Icons.Filled.ShoppingCart, contentDescription = "Registrar venta") },
            onClick = { nav.seleccionarTab(Ruta.EmpVenta) }
        )

        Ruta.EmpCatalogo -> Fab(
            descripcion = "Nuevo producto",
            icono = { Icon(Icons.Filled.Add, contentDescription = "Nuevo producto") },
            onClick = {
                val id = TramaStore.crearProductoBorrador()
                nav.irA(Ruta.ProductoDetalle(id))
            }
        )

        else -> Unit
    }
}

@Composable
private fun Fab(
    descripcion: String,
    icono: @Composable () -> Unit,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = Berenjena,
        contentColor = Color.White
    ) {
        icono()
    }
}

@Composable
private fun ContenidoSegunRuta(
    ruta: Ruta,
    nav: ControladorNavegacion,
    avisar: (String) -> Unit
) {
    when (ruta) {
        Ruta.Login -> com.example.trabajodea10.ui.auth.PantallaAcceso(nav = nav)

        Ruta.AdminInicio -> PantallaAdminInicio(nav = nav, avisar = avisar)
        Ruta.AdminFerias -> PantallaAdminFerias(nav = nav, avisar = avisar)
        Ruta.AdminEmprendedoras -> PantallaAdminEmprendedoras(nav = nav, avisar = avisar)
        Ruta.Ajustes -> PantallaAjustes(nav = nav, avisar = avisar)
        is Ruta.FeriaDetalle -> PantallaFeriaDetalle(
            feriaId = ruta.feriaId,
            nav = nav,
            avisar = avisar
        )

        Ruta.EmpInicio -> PantallaEmprendedoraInicio(nav = nav, avisar = avisar)
        Ruta.EmpCatalogo -> PantallaCatalogo(nav = nav, avisar = avisar)
        Ruta.EmpVenta -> PantallaVenta(nav = nav, avisar = avisar)
        Ruta.PagoVenta -> PantallaPagoVenta(nav = nav, avisar = avisar)
        Ruta.EmpFerias -> PantallaMisFerias(nav = nav, avisar = avisar)
        Ruta.EmpTienda -> PantallaTienda(nav = nav, avisar = avisar)
        is Ruta.EmpTiendaQR -> PantallaTiendaQR(tiendaId = ruta.tiendaId, nav = nav)
        is Ruta.ProductoDetalle -> PantallaProductoDetalle(
            productoId = ruta.productoId,
            nav = nav,
            avisar = avisar
        )
        Ruta.VistaPrevia -> com.example.trabajodea10.ui.emprendedora.PantallaVistaPrevia(nav = nav)
        Ruta.HistorialVentas -> com.example.trabajodea10.ui.emprendedora.PantallaHistorialVentas(nav = nav, avisar = avisar)
        Ruta.ConfigVentas -> com.example.trabajodea10.ui.emprendedora.PantallaConfigVentas(nav = nav, avisar = avisar)

        // Entrega 6: mapa de próximas ferias (pide la ubicación al abrirse) y directorio con filtros.
        Ruta.VisInicio -> com.example.trabajodea10.ui.comprador.PantallaMapaFerias(nav = nav)
        Ruta.VisProductos -> com.example.trabajodea10.ui.comprador.PantallaDirectorio(nav = nav, avisar = avisar)
        Ruta.VisAyuda -> PantallaVisAyuda()
        Ruta.VisMarketplaceClasico -> PantallaMarketplace(nav = nav, avisar = avisar)
        is Ruta.TiendaPublica -> com.example.trabajodea10.ui.comprador.PantallaTiendaPublica(ruta.tiendaId, nav, avisar)
        is Ruta.ProductoPublico -> com.example.trabajodea10.ui.comprador.PantallaProductoPublico(ruta.productoId, nav, avisar)
        is Ruta.AsociacionPublica -> com.example.trabajodea10.ui.comprador.PantallaAsociacionPublica(ruta.asociacionId, nav, avisar)
        is Ruta.FeriaPublica -> com.example.trabajodea10.ui.comprador.PantallaFeriaPublica(ruta.feriaId, nav)
        Ruta.ConfigVitrina -> com.example.trabajodea10.ui.emprendedora.PantallaConfigVitrina(nav, avisar)
        Ruta.BandejaContactos -> com.example.trabajodea10.ui.emprendedora.PantallaBandejaContactos(nav, avisar)
    }
}

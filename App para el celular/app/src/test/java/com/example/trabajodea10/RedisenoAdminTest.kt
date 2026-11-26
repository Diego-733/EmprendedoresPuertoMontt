package com.example.trabajodea10

import com.example.trabajodea10.data.CuentaEmprendedora
import com.example.trabajodea10.data.EstadoCuentaAdmin
import com.example.trabajodea10.data.FeriaPublica
import com.example.trabajodea10.data.ReglasAdmin
import com.example.trabajodea10.data.Tienda
import com.example.trabajodea10.ui.navegacion.CodigoRuta
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.navegacion.itemsSegunRol
import com.example.trabajodea10.data.Rol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rediseño v2: reglas puras del panel de administración en Android y su navegación. */
class RedisenoAdminTest {

    private fun cuenta(nombre: String, estado: EstadoCuentaAdmin, tienda: String? = "t_$nombre") =
        CuentaEmprendedora(if (estado == EstadoCuentaAdmin.PENDIENTE_REGISTRO) null else "u_$nombre", "$nombre@correo.cl", nombre,
            tienda, tienda?.let { "Tienda $nombre" }, estado, "2026-11-01")

    @Test
    fun resumenCuentaCadaEstadoUnaVez() {
        val r = ReglasAdmin.resumen(listOf(
            cuenta("ana", EstadoCuentaAdmin.ACTIVA), cuenta("bea", EstadoCuentaAdmin.ACTIVA),
            cuenta("cata", EstadoCuentaAdmin.DESACTIVADA), cuenta("dani", EstadoCuentaAdmin.PENDIENTE_REGISTRO, null),
            cuenta("eli", EstadoCuentaAdmin.SIN_TIENDA, null)
        ))
        assertEquals(2, r.activas); assertEquals(1, r.desactivadas); assertEquals(1, r.pendientes); assertEquals(1, r.sinTienda)
        assertEquals(5, r.total)
    }

    @Test
    fun estadoDesconocidoDelServidorNoSeTomaComoActiva() {
        assertEquals(EstadoCuentaAdmin.SIN_TIENDA, EstadoCuentaAdmin.desde("OTRO"))
        assertEquals(EstadoCuentaAdmin.PENDIENTE_REGISTRO, EstadoCuentaAdmin.desde("PENDIENTE_REGISTRO"))
    }

    @Test
    fun filtrarPorTextoEnNombreCorreoOTiendaYPorEstado() {
        val todas = listOf(cuenta("ana", EstadoCuentaAdmin.ACTIVA), cuenta("bea", EstadoCuentaAdmin.DESACTIVADA))
        assertEquals(listOf("ana"), ReglasAdmin.filtrar(todas, "ANA@", null).map { it.nombre })
        assertEquals(listOf("bea"), ReglasAdmin.filtrar(todas, "tienda bea", null).map { it.nombre })
        assertEquals(listOf("bea"), ReglasAdmin.filtrar(todas, "", EstadoCuentaAdmin.DESACTIVADA).map { it.nombre })
        assertTrue(ReglasAdmin.filtrar(todas, "zzz", null).isEmpty())
    }

    @Test
    fun ferias_hoyProximasAnterioresYCanceladasAparte() {
        val hoy = "2026-11-26"
        val f = listOf(
            FeriaPublica("a", "Hoy", fechaInicio = "2026-11-25", fechaFin = "2026-11-27"),
            FeriaPublica("b", "Mañana", fechaInicio = "2026-11-27"),
            FeriaPublica("c", "Ayer", fechaInicio = "2026-11-25"),
            FeriaPublica("d", "Cancelada", fechaInicio = "2026-11-28", estado = "CANCELADA"),
            FeriaPublica("e", "Borrador", fechaInicio = "2026-11-29", estado = "BORRADOR")
        )
        val g = ReglasAdmin.clasificarFerias(f, hoy)
        assertEquals(listOf("a"), g.enCurso.map { it.id })
        assertEquals(listOf("b"), g.proximas.map { it.id })
        assertEquals(listOf("c"), g.anteriores.map { it.id })
        assertEquals(listOf("d"), g.canceladas.map { it.id })
    }

    @Test
    fun integrantesSumaPertenenciasYAsociacionPrincipalSinDuplicar() {
        val tiendas = listOf(
            Tienda(id = "t1", nombre = "Uno", asociacionId = "as1"),
            Tienda(id = "t2", nombre = "Dos", asociacionId = "as2")
        )
        val conteo = ReglasAdmin.integrantesPorAsociacion(mapOf("t1" to listOf("as1", "as2")), tiendas)
        assertEquals(1, conteo["as1"])   // t1 ya estaba en las pertenencias: no se cuenta dos veces
        assertEquals(2, conteo["as2"])   // t1 por pertenencia + t2 por asociación principal
    }

    @Test
    fun administracionTieneGestionComoPestanaYLasRutasNuevasSeGuardan() {
        val pestanas = itemsSegunRol(Rol.ADMIN).map { it.etiqueta }
        assertEquals(listOf("Inicio", "Ferias", "Emprendedoras", "Gestión"), pestanas)
        listOf(Ruta.AdminGestion, Ruta.AdminAsociaciones, Ruta.AdminTipos, Ruta.AdminEquipo, Ruta.AdminBitacora).forEach { r ->
            assertEquals(r, CodigoRuta.desdeTexto(CodigoRuta.aTexto(r)))
        }
    }
}

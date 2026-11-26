package com.example.trabajodea10

import com.example.trabajodea10.data.CuentaEmprendedora
import com.example.trabajodea10.data.EstadoCuentaAdmin
import com.example.trabajodea10.data.ReglasAdmin
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.asistente.SesionAsistente.Accion
import com.example.trabajodea10.data.asistente.TemasAyuda
import com.example.trabajodea10.data.remoto.CodigoActivacion
import com.example.trabajodea10.data.remoto.ConfigSupabase
import com.example.trabajodea10.data.remoto.FormFeria
import com.example.trabajodea10.ui.asistente.EjecutorAsistente
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Administración integral (11): registro controlado, ferias reales, cuentas desactivadas y datos de ejemplo. */
class AdministracionIntegralTest {

    private fun cuenta(n: String, e: EstadoCuentaAdmin, autorizada: Boolean? = true) =
        CuentaEmprendedora(if (e.name.startsWith("PENDIENTE")) null else "u_$n", "$n@c.cl", n, null, null, e, null, autorizada)

    @Test
    fun estadosDelServidorSeLeenSinInventar() {
        assertEquals(EstadoCuentaAdmin.PENDIENTE_ACTIVACION, EstadoCuentaAdmin.desde("PENDIENTE_ACTIVACION"))
        assertEquals(EstadoCuentaAdmin.PENDIENTE_REGISTRO, EstadoCuentaAdmin.desde("PENDIENTE_REGISTRO"))
        assertEquals(EstadoCuentaAdmin.SIN_TIENDA, EstadoCuentaAdmin.desde("CUALQUIER_OTRO"))
    }

    @Test
    fun pendientesAgrupaRegistroYActivacion_ySinAutorizacionSeCuenta() {
        val todas = listOf(cuenta("a", EstadoCuentaAdmin.PENDIENTE_REGISTRO), cuenta("b", EstadoCuentaAdmin.PENDIENTE_ACTIVACION),
            cuenta("c", EstadoCuentaAdmin.ACTIVA, autorizada = false), cuenta("d", EstadoCuentaAdmin.ACTIVA, autorizada = null))
        assertEquals(listOf("a", "b"), ReglasAdmin.filtrar(todas, "", EstadoCuentaAdmin.PENDIENTE_REGISTRO).map { it.nombre })
        val r = ReglasAdmin.resumen(todas)
        assertEquals(2, r.pendientes); assertEquals(1, r.sinAutorizacion)   // null (servidor sin 11) no se cuenta como «sin autorización»
    }

    @Test
    fun mensajeDelCodigoTieneCorreoYCodigoPeroNuncaUnaContrasena() {
        val m = ReglasAdmin.mensajeCodigo("Ana", CodigoActivacion("ana@c.cl", "ABCD-2345", null, null))
        assertTrue(m.contains("ana@c.cl") && m.contains("ABCD-2345") && m.contains("contraseña que elijas tú"))
        assertFalse(m.contains("Clave:") || m.contains("password"))
    }

    @Test
    fun formularioDeFeria_reglasDeLaWebYDelServidor() {
        val hoy = "2026-11-26"
        val base = FormFeria("f1", true, "Feria", "", "Plaza", "2026-12-01", "", "10:00 a 18:00", "")
        assertNull(ReglasAdmin.validarFeria(base, hoy))
        assertEquals("Falta: nombre, lugar.", ReglasAdmin.validarFeria(base.copy(nombre = " ", lugar = ""), hoy))
        assertEquals("Escribe las fechas como AAAA-MM-DD.", ReglasAdmin.validarFeria(base.copy(fechaInicio = "01/12/2026"), hoy))
        assertEquals("La fecha de término no puede ser antes del inicio.", ReglasAdmin.validarFeria(base.copy(fechaFin = "2026-11-30"), hoy))
        assertEquals("La fecha de inicio ya pasó.", ReglasAdmin.validarFeria(base.copy(fechaInicio = "2026-11-01"), hoy))
        assertNull("Editar una feria pasada no se bloquea en la app (lo decide el servidor)", ReglasAdmin.validarFeria(base.copy(esNueva = false, fechaInicio = "2026-11-01"), hoy))
    }

    @Test
    fun laAppConectadaNoUsaDatosDeEjemplo() {
        // En la app conectada (con servidor) los datos de ejemplo quedan apagados; solo el modo de prueba los carga.
        assertEquals(!ConfigSupabase.configurado, TramaStore.usarEjemplos)
    }

    @Test
    fun elAsistenteNoOperaSinPermiso() {
        assertTrue(EjecutorAsistente.cambiaDatos(Accion.PrepararVenta(emptyList(), null, false)))
        assertTrue(EjecutorAsistente.cambiaDatos(Accion.CambiarVisibilidad(emptyList(), true)))
        assertTrue(EjecutorAsistente.cambiaDatos(Accion.ResponderFeria("f", "F", true)))
        assertFalse(EjecutorAsistente.cambiaDatos(Accion.Navegar(TemasAyuda.Destino.AYUDA)))
        assertTrue(EjecutorAsistente.MENSAJE_DESACTIVADA.contains("desactivó"))
    }
}

package com.example.trabajodea10

import com.example.trabajodea10.data.AccionPendiente
import com.example.trabajodea10.data.AccionSeguir
import com.example.trabajodea10.data.MetodoIngreso
import com.example.trabajodea10.data.ReglasFerias
import com.example.trabajodea10.data.ReglasSeguir
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.navegacion.rutaDeFicha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E7-A12: «Seguir» sin sesión → Google → vuelve a la ficha correcta. */
class SeguirConGoogleTest {

    @Test fun sinSesionElBotonPideIngresoYElMetodoEsGoogleNoCorreo() {
        assertEquals(AccionSeguir.PEDIR_INGRESO, ReglasSeguir.alTocar(haySesion = false, siguiendo = false))
        assertEquals(MetodoIngreso.GOOGLE, ReglasSeguir.metodoParaSeguir())
        assertEquals(AccionSeguir.SEGUIR, ReglasSeguir.alTocar(haySesion = true, siguiendo = false))
        assertEquals(AccionSeguir.DEJAR_DE_SEGUIR, ReglasSeguir.alTocar(haySesion = true, siguiendo = true))
    }

    @Test fun alVolverSeAbreLaFichaDeLaTiendaODeLaAsociacion() {
        assertEquals(Ruta.TiendaPublica("t_tejidos"), rutaDeFicha(AccionPendiente("seguir", "TIENDA", "t_tejidos", "Tejidos", 0)))
        assertEquals(Ruta.AsociacionPublica("as1"), rutaDeFicha(AccionPendiente("seguir", "ASOCIACION", "as1", "Manos del Sur", 0)))
    }

    @Test fun laAccionSigueVigenteMientrasDuraElIngresoYVenceDespues() {
        val p = AccionPendiente("seguir", "TIENDA", "t1", "Tejidos", 1_000)
        assertTrue("volver del navegador 3 minutos después", ReglasFerias.accionPendienteVigente(p, 1_000 + 3 * 60_000))
        assertFalse(ReglasFerias.accionPendienteVigente(p, 1_000 + ReglasFerias.VIGENCIA_PENDIENTE_MS + 1))
    }
}

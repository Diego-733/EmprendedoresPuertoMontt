package com.example.trabajodea10

import com.example.trabajodea10.data.remoto.EstadoGuardado
import com.example.trabajodea10.data.remoto.ReglasGuardado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que la emprendedora lee bajo cada producto después de cambiar «Cuántos tengo» o «Se acabó»:
 * si el cambio ya llegó al servidor o quedó pendiente por falta de señal (RNF-REN-02).
 */
class EstadoGuardadoTest {
    @Test
    fun sinPendientesEstaGuardadoEnElServidor() {
        assertEquals(EstadoGuardado.GUARDADO, ReglasGuardado.estado(hayCuenta = true, pendiente = false, subiendo = false, conectado = true))
        assertEquals(EstadoGuardado.GUARDADO, ReglasGuardado.estado(hayCuenta = true, pendiente = false, subiendo = true, conectado = false))
    }

    @Test
    fun sinSenalElCambioQuedaPendienteAunqueSeEsteIntentando() {
        assertEquals(EstadoGuardado.PENDIENTE, ReglasGuardado.estado(hayCuenta = true, pendiente = true, subiendo = false, conectado = false))
        assertEquals(EstadoGuardado.PENDIENTE, ReglasGuardado.estado(hayCuenta = true, pendiente = true, subiendo = true, conectado = false))
    }

    @Test
    fun conSenalYSubiendoSeDiceSubiendo() {
        assertEquals(EstadoGuardado.SUBIENDO, ReglasGuardado.estado(hayCuenta = true, pendiente = true, subiendo = true, conectado = true))
    }

    @Test
    fun sinCuentaDelServidorSeAclaraQueQuedaSoloEnElCelular() {
        assertEquals(EstadoGuardado.SOLO_EN_EL_CELULAR, ReglasGuardado.estado(hayCuenta = false, pendiente = false, subiendo = false, conectado = true))
    }

    @Test
    fun cadaEstadoSeExplicaConPalabrasSinJerga() {
        EstadoGuardado.entries.forEach { e ->
            assertTrue(e.etiqueta.isNotBlank() && e.detalle.isNotBlank())
            listOf("stock", "sync", "sincroniz", "error").forEach { palabra ->
                assertTrue("«$palabra» en ${e.name}", !(e.etiqueta + e.detalle).lowercase().contains(palabra))
            }
        }
    }
}

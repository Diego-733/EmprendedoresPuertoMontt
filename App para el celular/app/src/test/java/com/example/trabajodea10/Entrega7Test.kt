package com.example.trabajodea10

import com.example.trabajodea10.data.AccionPendiente
import com.example.trabajodea10.data.MiFeria
import com.example.trabajodea10.data.MotivoSugerencia
import com.example.trabajodea10.data.ReglasFerias
import com.example.trabajodea10.data.ReglasVitrina
import com.example.trabajodea10.data.Tienda
import com.example.trabajodea10.data.UbicacionMomento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas de la Entrega 7 (data/Ferias7.kt): mismas que el servidor y la web. */
class Entrega7Test {

    private val hoy = "2026-11-22"
    private fun feria(id: String, invitacion: String, ini: String = hoy, fin: String = ini, lat: Double? = null, lng: Double? = null,
                      estado: String = "PROXIMA", horario: String = "10:00 a 18:00") =
        MiFeria(id, "Feria $id", "Lugar", ini, fin, horario, estado, lat, lng, invitacion)

    @Test fun fechaEnSantiagoConCambiosDeHora() {
        assertEquals("2026-11-19", ReglasFerias.fechaSantiago(java.time.Instant.parse("2026-11-20T02:59:59Z").toEpochMilli()))
        assertEquals("2026-11-20", ReglasFerias.fechaSantiago(java.time.Instant.parse("2026-11-20T03:00:00Z").toEpochMilli()))
        assertEquals("2026-09-05", ReglasFerias.fechaSantiago(java.time.Instant.parse("2026-09-06T03:59:59Z").toEpochMilli()))
        assertEquals("2026-09-06", ReglasFerias.fechaSantiago(java.time.Instant.parse("2026-09-06T04:00:00Z").toEpochMilli()))
        assertEquals("01:00", ReglasFerias.horaSantiago(java.time.Instant.parse("2026-09-06T04:00:00Z").toEpochMilli()))
        assertEquals("2026-04-04", ReglasFerias.fechaSantiago(java.time.Instant.parse("2026-04-05T03:30:00Z").toEpochMilli()))
    }

    @Test fun sugierePorFechaSoloSiParticipa() {
        val s = ReglasFerias.sugerirFeria(listOf(feria("ajena", "PENDIENTE"), feria("rechazada", "RECHAZADA"), feria("mia", "CONFIRMADA")), hoy, "12:00", null)
        assertEquals("mia", s?.feria?.feriaId)
        assertEquals(MotivoSugerencia.FECHA, s?.motivo)
        assertNull("nunca una feria donde no está confirmada",
            ReglasFerias.sugerirFeria(listOf(feria("ajena", "PENDIENTE"), feria("no", "RECHAZADA")), hoy, "12:00", null))
        assertEquals("agregada por administración también participa", "agr",
            ReglasFerias.sugerirFeria(listOf(feria("agr", "AGREGADA")), hoy, "12:00", null)?.feria?.feriaId)
    }

    @Test fun noSugiereCanceladasNiDeOtroDia() {
        assertNull(ReglasFerias.sugerirFeria(listOf(feria("c", "CONFIRMADA", estado = "CANCELADA")), hoy, "12:00", null))
        assertNull(ReglasFerias.sugerirFeria(listOf(feria("m", "CONFIRMADA", ini = "2026-11-23")), hoy, "12:00", null))
        assertNull(ReglasFerias.sugerirFeria(listOf(feria("b", "CONFIRMADA", estado = "BORRADOR")), hoy, "12:00", null))
    }

    @Test fun feriaDeVariosDiasSeSugiereCadaDia() {
        val f = feria("larga", "CONFIRMADA", ini = "2026-11-21", fin = "2026-11-23")
        assertEquals("larga", ReglasFerias.sugerirFeria(listOf(f), "2026-11-23", "12:00", null)?.feria?.feriaId)
        assertNull(ReglasFerias.sugerirFeria(listOf(f), "2026-11-24", "12:00", null))
    }

    @Test fun conUbicacionPrefiereLaFeriaEnCursoDondeEsta() {
        val plaza = feria("plaza", "CONFIRMADA", lat = -41.3959, lng = -72.8955)
        val costanera = feria("costanera", "CONFIRMADA", lat = -41.4717, lng = -72.9396)
        val enCostanera = UbicacionMomento(-41.4720, -72.9390, 30f, 60_000)
        val s = ReglasFerias.sugerirFeria(listOf(plaza, costanera), hoy, "12:00", enCostanera)
        assertEquals("costanera", s?.feria?.feriaId)
        assertEquals(MotivoSugerencia.UBICACION, s?.motivo)
        assertTrue((s?.distanciaMetros ?: 9e9) < 100)
    }

    @Test fun ubicacionLejanaImprecisaOAntiguaNoDecide() {
        val plaza = feria("plaza", "CONFIRMADA", lat = -41.3959, lng = -72.8955)
        val lejos = UbicacionMomento(-41.4717, -72.9396, 20f, 30_000)                 // a ~9 km
        assertEquals(MotivoSugerencia.FECHA, ReglasFerias.sugerirFeria(listOf(plaza), hoy, "12:00", lejos)?.motivo)
        val imprecisa = UbicacionMomento(-41.3959, -72.8955, 3000f, 30_000)
        assertEquals(MotivoSugerencia.FECHA, ReglasFerias.sugerirFeria(listOf(plaza), hoy, "12:00", imprecisa)?.motivo)
        val antigua = UbicacionMomento(-41.3959, -72.8955, 10f, 2 * 60 * 60 * 1000L)
        assertEquals(MotivoSugerencia.FECHA, ReglasFerias.sugerirFeria(listOf(plaza), hoy, "12:00", antigua)?.motivo)
    }

    @Test fun ubicacionCercaPeroFueraDeHorarioNoEsEnCurso() {
        val plaza = feria("plaza", "CONFIRMADA", lat = -41.3959, lng = -72.8955, horario = "10:00 a 14:00")
        val alli = UbicacionMomento(-41.3959, -72.8955, 15f, 10_000)
        assertEquals(MotivoSugerencia.UBICACION, ReglasFerias.sugerirFeria(listOf(plaza), hoy, "14:30", alli)?.motivo)   // margen de 1 h
        assertEquals(MotivoSugerencia.FECHA, ReglasFerias.sugerirFeria(listOf(plaza), hoy, "20:00", alli)?.motivo)
    }

    @Test fun respuestaNegativaPendienteNoSeSugiere() {
        val f = feria("m", "CONFIRMADA").copy(respuestaPendiente = false)
        assertNull(ReglasFerias.sugerirFeria(listOf(f), hoy, "12:00", null))
    }

    @Test fun calendarioInvitadasYConfirmadasPorMes() {
        val cal = ReglasFerias.calendario(listOf(
            feria("dic", "PENDIENTE", ini = "2026-12-05"),
            feria("nov", "CONFIRMADA", ini = "2026-11-28"),
            feria("rech", "RECHAZADA", ini = "2026-11-29"),
            feria("canc", "CONFIRMADA", ini = "2026-11-30", estado = "CANCELADA"),
            feria("pasada", "CONFIRMADA", ini = "2026-11-01")
        ), hoy)
        assertEquals(listOf("noviembre de 2026" to listOf("nov"), "diciembre de 2026" to listOf("dic")),
            cal.map { (m, l) -> m to l.map { it.feriaId } })
    }

    @Test fun horarioDeAvisosDeContacto() {
        assertTrue(ReglasFerias.dentroDeHorario("10:00", "09:00", "20:00"))
        assertFalse(ReglasFerias.dentroDeHorario("20:00", "09:00", "20:00"))
        assertTrue(ReglasFerias.dentroDeHorario("23:30", "22:00", "07:00"))
        assertFalse(ReglasFerias.dentroDeHorario("12:00", "22:00", "07:00"))
        assertTrue("desde = hasta: todo el día", ReglasFerias.dentroDeHorario("03:00", "08:00", "08:00"))
    }

    @Test fun accionPendienteVenceYSoloSeguir() {
        val p = AccionPendiente("seguir", "TIENDA", "t1", "Tejidos", 1_000)
        assertTrue(ReglasFerias.accionPendienteVigente(p, 1_000 + 60_000))
        assertFalse(ReglasFerias.accionPendienteVigente(p, 1_000 + ReglasFerias.VIGENCIA_PENDIENTE_MS + 1))
        assertFalse(ReglasFerias.accionPendienteVigente(p.copy(accion = "borrar"), 2_000))
        assertFalse(ReglasFerias.accionPendienteVigente(p.copy(tipo = "PRODUCTO"), 2_000))
        assertFalse(ReglasFerias.accionPendienteVigente(null, 2_000))
    }

    @Test fun rangoDeHorarioEnTexto() {
        assertEquals(600 to 1080, ReglasFerias.rangoHorario("10:00 a 18:00"))
        assertEquals(600 to 1080, ReglasFerias.rangoHorario("10 a 18 hrs"))
        assertNull(ReglasFerias.rangoHorario("todo el día"))
    }

    @Test fun pertenenciaAVariasAsociaciones() {
        val t = Tienda(id = "t1", asociacionId = "as1")
        assertTrue(ReglasVitrina.perteneceA(t, "as2", mapOf("t1" to listOf("as1", "as2"))))
        assertTrue("servidor sin la tabla: usa la asociación única", ReglasVitrina.perteneceA(t, "as1", emptyMap()))
        assertFalse(ReglasVitrina.perteneceA(t, ReglasVitrina.SIN_ASOCIACION, emptyMap()))
        val filtradas = ReglasVitrina.filtrarTiendas(listOf(t, Tienda(id = "t2", asociacionId = null)), "", null, null, "as2", mapOf("t1" to listOf("as1", "as2")))
        assertEquals(listOf("t1"), filtradas.map { it.id })
    }
}

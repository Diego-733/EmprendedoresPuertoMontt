package com.example.trabajodea10

import com.example.trabajodea10.data.RevisionFoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Revisión local de la luz de una foto: solo un consejo, nunca impide guardar. */
class RevisionFotoTest {

    private fun gris(v: Int, n: Int = 100) = IntArray(n) { (0xFF shl 24) or (v shl 16) or (v shl 8) or v }

    @Test
    fun `luminancia promedio`() {
        assertEquals(0.0, RevisionFoto.promedioLuminancia(gris(0)), 0.01)
        assertEquals(255.0, RevisionFoto.promedioLuminancia(gris(255)), 0.01)
        assertEquals(128.0, RevisionFoto.promedioLuminancia(IntArray(0)), 0.01)
    }

    @Test
    fun `solo aconseja en los extremos`() {
        assertNotNull(RevisionFoto.consejo(RevisionFoto.promedioLuminancia(gris(20))))
        assertNotNull(RevisionFoto.consejo(RevisionFoto.promedioLuminancia(gris(250))))
        assertNull(RevisionFoto.consejo(RevisionFoto.promedioLuminancia(gris(120))))
        // Nunca bloquea: el consejo dice que se puede seguir.
        assert(RevisionFoto.consejo(10.0)!!.contains("puedes seguir"))
    }

    @Test
    fun `fotos ya subidas o inexistentes no se revisan`() {
        assertNull(RevisionFoto.revisarArchivo("https://servidor/foto.jpg"))
        assertNull(RevisionFoto.revisarArchivo("/no/existe.jpg"))
        assertNull(RevisionFoto.revisarArchivo(null))
    }
}

package com.example.trabajodea10

import com.example.trabajodea10.seguridad.FabricaAppCheck
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Entrega 8 (A11): el proveedor de App Check depende del tipo de APK.
 * testDebugUnitTest comprueba la variante de prueba; la pública la comprueba scripts/verificar-apk.js
 * sobre el APK release (no debe contener el proveedor de depuración).
 */
class AppCheckVarianteTest {
    @Test
    fun elApkPublicoUsaPlayIntegrityYElDePruebaElDeDepuracion() {
        assertEquals(BuildConfig.DEBUG, FabricaAppCheck.esDepuracion)
        assertEquals(if (BuildConfig.DEBUG) "depuración (solo APK de prueba del equipo)" else "Play Integrity", FabricaAppCheck.nombre)
    }

}

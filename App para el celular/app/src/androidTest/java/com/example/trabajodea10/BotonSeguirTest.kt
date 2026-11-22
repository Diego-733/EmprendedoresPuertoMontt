package com.example.trabajodea10

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.trabajodea10.data.FeriasStore
import com.example.trabajodea10.data.remoto.ClienteSupabase
import com.example.trabajodea10.ui.comprador.BotonSeguir
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E7-A12 en la interfaz: sin sesión, «Seguir» → «Continuar con Google» abre el ingreso
 * con Google (aquí sustituido para no salir a internet) y NO el formulario de correo.
 * La acción queda pendiente con la ficha correcta para completarse al volver.
 */
@RunWith(AndroidJUnit4::class)
class BotonSeguirTest {

    @get:Rule val compose = createComposeRule()

    @Before fun sinSesion() {
        assumeTrue("La prueba necesita la app sin sesión iniciada", ClienteSupabase.sesion == null)
        FeriasStore.descartarPendiente()
    }

    @After fun limpiar() { FeriasStore.descartarPendiente() }

    private fun recorrido(tipo: String, id: String, nombre: String, fichaEsperada: Ruta) {
        var llamadasGoogle = 0
        val nav = ControladorNavegacion(fichaEsperada)
        compose.setContent {
            BotonSeguir(tipo, id, nombre, nav, avisar = {}, iniciarGoogle = { llamadasGoogle++; null })
        }
        compose.onNodeWithContentDescription("Seguir a $nombre").performClick()
        compose.onNodeWithText("Ingresa con Google para seguir").assertIsDisplayed()
        compose.onNodeWithText("Continuar con Google").performClick()
        compose.waitUntil(3_000) { llamadasGoogle == 1 }
        assertEquals("no debe ir al formulario de correo", fichaEsperada, nav.actual)
        val pendiente = FeriasStore.fichaPendiente()
        assertEquals(tipo to id, pendiente?.tipo to pendiente?.id)
    }

    @Test fun seguirUnaTiendaSinSesionAbreGoogle() =
        recorrido("TIENDA", "t_prueba", "Tienda de prueba", Ruta.TiendaPublica("t_prueba"))

    @Test fun seguirUnaAsociacionSinSesionAbreGoogle() =
        recorrido("ASOCIACION", "as_prueba", "Asociación de prueba", Ruta.AsociacionPublica("as_prueba"))

    @Test fun siGoogleFallaSeAvisaYSePuedeReintentar() {
        val avisos = mutableListOf<String>()
        val nav = ControladorNavegacion(Ruta.TiendaPublica("t_prueba"))
        compose.setContent {
            BotonSeguir("TIENDA", "t_prueba", "Tienda de prueba", nav, avisar = { avisos += it },
                iniciarGoogle = { "El ingreso con Google aún no está habilitado en el servidor." })
        }
        compose.onNodeWithContentDescription("Seguir a Tienda de prueba").performClick()
        compose.onNodeWithText("Continuar con Google").performClick()
        compose.waitUntil(3_000) { avisos.isNotEmpty() }
        assertEquals(null, FeriasStore.fichaPendiente())
        // El botón sigue disponible para volver a intentarlo.
        compose.onNodeWithContentDescription("Seguir a Tienda de prueba").performClick()
        compose.onNodeWithText("Ingresa con Google para seguir").assertIsDisplayed()
    }
}

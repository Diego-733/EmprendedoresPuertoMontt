package com.example.trabajodea10

import com.example.trabajodea10.TramaApp


import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trabajodea10.ui.theme.BlancoPuro
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.RojoAlerce
import com.example.trabajodea10.ui.theme.TramaAppTheme
// FragmentActivity (y no ComponentActivity) porque el aviso de huella de Android
// (BiometricPrompt) solo funciona sobre una FragmentActivity.
class MainActivity : FragmentActivity() {

    // Regreso del ingreso con Google: com.company.ultrapp://login-callback#access_token=…
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        procesarEnlace(intent)
    }

    private fun procesarEnlace(intent: android.content.Intent?) {
        // Entrega 7: al tocar un aviso se abre la feria correspondiente.
        intent?.getStringExtra(com.example.trabajodea10.avisos.AvisosCelular.EXTRA_FERIA)?.let {
            com.example.trabajodea10.data.FeriasStore.feriaDesdeAviso = it
            intent.removeExtra(com.example.trabajodea10.avisos.AvisosCelular.EXTRA_FERIA)
        }
        intent?.getStringExtra(com.example.trabajodea10.avisos.AvisosCelular.EXTRA_AVISO)?.let {
            // La persona tocó la notificación en este celular.
            com.example.trabajodea10.data.FeriasStore.confirmarAviso(it, "ABIERTO")
            intent.removeExtra(com.example.trabajodea10.avisos.AvisosCelular.EXTRA_AVISO)
        }
        val datos = intent?.data ?: return
        if (datos.scheme == "com.company.ultrapp" && datos.host == "login-callback") {
            com.example.trabajodea10.data.SesionManager.procesarRetornoOAuth(datos)
            intent?.data = null
        }
    }

    // Al salir de la app (o bloquear la pantalla) la lectura en voz alta se detiene.
    override fun onStop() {
        super.onStop()
        com.example.trabajodea10.voz.LectorVoz.detener()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // El manifiesto abre con Theme.TrabajoDeA10.Arranque (logo sobre musgo mientras carga);
        // apenas la actividad existe se vuelve al tema normal, sin esperas añadidas.
        setTheme(R.style.Theme_TrabajoDeA10)
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("app_diagnostico", Context.MODE_PRIVATE)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("MainActivity", "CRASH INTERCEPTADO: ${throwable.message}", throwable)
                val resumen = "${throwable.javaClass.simpleName}: ${throwable.message}\n" +
                    throwable.stackTrace.take(6).joinToString("\n") { "  en ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
                prefs.edit().putString("ultimo_error", resumen).commit()
            } catch (_: Throwable) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
        // La app usa siempre su paleta clara. Con el celular en modo oscuro, el estilo automático ponía
        // blancos la hora, la batería y la señal sobre el fondo claro: se fijan oscuros para que se lean.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        procesarEnlace(intent)
        setContent {
            com.example.trabajodea10.ui.theme.TramaAppTheme {
                val errorPrevio = remember { prefs.getString("ultimo_error", null) }
                var mostrarDialogoError by remember { mutableStateOf(errorPrevio != null) }
                TramaApp()
                if (mostrarDialogoError && errorPrevio != null) {
                    AlertDialog(
                        onDismissRequest = {
                            prefs.edit().remove("ultimo_error").apply()
                            mostrarDialogoError = false
                        },
                        title = {
                            Text(
                                text = "Diagnóstico de Estabilidad",
                                fontWeight = FontWeight.Bold,
                                color = RojoAlerce,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Text(
                                text = "Se detectó un reporte previo:\n\n$errorPrevio",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = NegroApple
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    prefs.edit().remove("ultimo_error").apply()
                                    mostrarDialogoError = false
                                },
                                shape = RoundedCornerShape(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NegroApple)
                            ) {
                                Text("Limpiar y Continuar", color = BlancoPuro, fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = BlancoPuro,
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
    }
}
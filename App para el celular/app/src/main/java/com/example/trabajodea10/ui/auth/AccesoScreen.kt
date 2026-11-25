package com.example.trabajodea10.ui.auth

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.trabajodea10.data.NavegacionAnonima
import com.example.trabajodea10.data.Rol
import com.example.trabajodea10.data.SesionManager
import com.example.trabajodea10.data.VitrinaStore
import com.example.trabajodea10.data.remoto.ClienteSupabase
import com.example.trabajodea10.data.remoto.ConfigSupabase
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.navegacion.AccesoApp
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.RojoAlerce
import com.example.trabajodea10.ui.theme.TextoSecundario

/*
 * ENTRADA SIN SESIÓN (RF-ACC-06; checklist A08). La app obedece la opción que el
 * administrador guardó en la web (tabla configuracion, clave navegacion_anonima):
 *  - PERMITIR: entra directo a la vitrina (modo comprador).
 *  - INVITAR:  invita a ingresar con Google y deja «Omitir».
 *  - OBLIGAR:  sin sesión solo se puede continuar ingresando con Google.
 * Las emprendedoras y la administración siguen entrando con su correo.
 */
@Composable
fun PantallaAcceso(nav: ControladorNavegacion) {
    // Con una sesión guardada (por ejemplo, huella activada) o si la persona pidió entrar con correo.
    if (ClienteSupabase.sesion != null || AccesoApp.mostrarLogin) {
        Column(Modifier.fillMaxSize()) {
            if (ClienteSupabase.sesion == null) {
                TextButton(onClick = { AccesoApp.mostrarLogin = false }, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    Text("  Otras formas de entrar")
                }
            }
            Box(Modifier.weight(1f)) { PantallaLogin(nav = nav) }
        }
        return
    }

    var modo by remember { mutableStateOf<NavegacionAnonima?>(null) }
    LaunchedEffect(Unit) { modo = VitrinaStore.actualizarNavegacion() }

    when (modo) {
        null -> Cargando("Preparando la vitrina…")
        // Entrega 8 (RF-ACC-05): aunque se permita navegar sin cuenta, la primera pantalla separa
        // el modo comprador del modo emprendedora. Comprar no exige iniciar sesión.
        NavegacionAnonima.PERMITIR -> EleccionDeModo(nav)
        NavegacionAnonima.INVITAR -> Invitacion(nav, obligar = false)
        NavegacionAnonima.OBLIGAR -> Invitacion(nav, obligar = true)
    }
}

@Composable
private fun Cargando(texto: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = NegroApple)
        Spacer(Modifier.height(12.dp))
        Text(texto, color = TextoSecundario)
    }
}

@Composable
private fun Invitacion(nav: ControladorNavegacion, obligar: Boolean) {
    val contexto = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ALERCE", fontSize = 34.sp, fontWeight = FontWeight.Black, color = NegroApple)
        Text("Vitrina de emprendedoras de Alerce", color = TextoSecundario)
        Spacer(Modifier.height(28.dp))
        Text(
            if (obligar) "Para usar la app, ingresa con tu cuenta de Google."
            else "Ingresa con Google para guardar tus datos y escribir a las tiendas más rápido.",
            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(20.dp))
        val ambitoGoogle = androidx.compose.runtime.rememberCoroutineScope()
        BotonPrimario(
            texto = "Continuar con Google",
            icono = { Icon(Icons.Filled.Login, contentDescription = null) },
            onClick = {
                ambitoGoogle.launch {
                    abrirIngresoGoogle(contexto)?.let { Toast.makeText(contexto, it, Toast.LENGTH_LONG).show() }
                }
            }
        )
        SesionManager.errorOAuth?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = RojoAlerce, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }
        if (!obligar) {
            Spacer(Modifier.height(10.dp))
            BotonSecundario(texto = "Omitir por ahora", onClick = { nav.iniciarSesion(Rol.VISITANTE) })
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { AccesoApp.mostrarLogin = true }) {
            Text("Soy emprendedora o administradora · entrar con correo", textAlign = TextAlign.Center)
        }
    }
}

/** Entrega 8 (RF-ACC-05): con la navegación sin cuenta permitida, elegir entre comprar o vender. */
@Composable
private fun EleccionDeModo(nav: ControladorNavegacion) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ALERCE", fontSize = 34.sp, fontWeight = FontWeight.Black, color = NegroApple)
        Text("Vitrina de emprendedoras de Alerce", color = TextoSecundario)
        Spacer(Modifier.height(28.dp))
        Text("¿Cómo quieres usar la app?", style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(20.dp))
        BotonPrimario(
            texto = "Quiero comprar · ver la vitrina",
            onClick = { nav.iniciarSesion(Rol.VISITANTE) }
        )
        Spacer(Modifier.height(6.dp))
        Text("No necesitas cuenta. Para seguir tiendas o calificar, podrás entrar con Google después.",
            color = TextoSecundario, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        BotonSecundario(
            texto = "Soy emprendedora · entrar a mi tienda",
            onClick = { AccesoApp.mostrarLogin = true }
        )
        Spacer(Modifier.height(6.dp))
        Text("También para la administración de la plataforma.",
            color = TextoSecundario, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

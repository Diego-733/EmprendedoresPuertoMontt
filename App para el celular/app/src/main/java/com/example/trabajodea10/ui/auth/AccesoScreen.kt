package com.example.trabajodea10.ui.auth

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.trabajodea10.R
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
import com.example.trabajodea10.ui.theme.Musgo
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

/**
 * Contenido centrado en la pantalla que se puede desplazar si no cabe (letra del celular
 * ampliada o pantallas bajas). Ancho máximo cómodo en tablets.
 */
@Composable
private fun ContenidoCentrado(contenido: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                // Más aire abajo que arriba: el grupo queda en el centro óptico (algo sobre el centro geométrico).
                .padding(start = 28.dp, end = 28.dp, top = 32.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = contenido
        )
    }
}

/**
 * Rediseño v2: portada de marca. El logo oficial (sin cambios) va sobre un disco blanco, encima de un
 * panel de bosque con el nombre y la promesa. Las opciones quedan debajo, en tarjetas con ícono.
 * El logo es decorativo para TalkBack: el nombre «ALERCE» ya lo dice.
 */
@Composable
private fun PortadaMarca(contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(com.example.trabajodea10.ui.theme.FondoLimpio).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(com.example.trabajodea10.ui.theme.Bosque, com.example.trabajodea10.ui.theme.Brote)))
                .padding(top = 40.dp, bottom = 36.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 480.dp)) {
                Box(
                    Modifier.size(128.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painter = painterResource(R.drawable.logo_alerce), contentDescription = null, modifier = Modifier.height(92.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "ALERCE", fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.semantics { heading() }
                )
                Text("Vitrina de emprendedoras de Alerce", color = com.example.trabajodea10.ui.theme.SobreBosqueSuave, textAlign = TextAlign.Center, fontSize = 17.sp)
                Spacer(Modifier.height(14.dp))
                // Sello en ámbar con texto oscuro (10:1); el ámbar sobre verde no tendría contraste suficiente.
                Text("Hecho a mano, cerca tuyo · Puerto Montt", color = NegroApple, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .background(com.example.trabajodea10.ui.theme.Ambar).padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = contenido
        )
    }
}

/** Tarjeta de elección con ícono, título (el texto del botón) y explicación. */
@Composable
private fun OpcionEntrada(
    titulo: String, detalle: String, icono: androidx.compose.ui.graphics.vector.ImageVector, principal: Boolean, onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = if (principal) com.example.trabajodea10.ui.theme.Musgo else androidx.compose.ui.graphics.Color.White,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = titulo, onClick = onClick)
            .semantics(mergeDescendants = true) {}
    ) {
        androidx.compose.foundation.layout.Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(if (principal) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f) else com.example.trabajodea10.ui.theme.MusgoClaro),
                contentAlignment = Alignment.Center
            ) { Icon(icono, contentDescription = null, tint = if (principal) androidx.compose.ui.graphics.Color.White else Musgo, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                    color = if (principal) androidx.compose.ui.graphics.Color.White else NegroApple)
                Text(detalle, fontSize = 15.sp, lineHeight = 20.sp,
                    color = if (principal) com.example.trabajodea10.ui.theme.SobreBosqueSuave else TextoSecundario)
            }
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = if (principal) androidx.compose.ui.graphics.Color.White else TextoSecundario)
        }
    }
}

@Composable
private fun Cargando(texto: String) {
    // Continúa la pantalla de arranque: el mismo logo mientras se lee la configuración.
    ContenidoCentrado {
        Image(painter = painterResource(R.drawable.logo_alerce), contentDescription = null, modifier = Modifier.height(88.dp))
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(color = Musgo)
        Spacer(Modifier.height(12.dp))
        Text(texto, color = TextoSecundario)
    }
}

@Composable
private fun Invitacion(nav: ControladorNavegacion, obligar: Boolean) {
    val contexto = LocalContext.current
    PortadaMarca {
        Spacer(Modifier.height(4.dp))
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
    PortadaMarca {
        Text("¿Cómo quieres usar la app?", style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center, color = NegroApple)
        Spacer(Modifier.height(18.dp))
        OpcionEntrada(
            titulo = "Quiero comprar · ver la vitrina",
            detalle = "No necesitas cuenta. Para seguir tiendas o calificar, podrás entrar con Google después.",
            icono = androidx.compose.material.icons.Icons.Filled.ShoppingBag, principal = true,
            onClick = { nav.iniciarSesion(Rol.VISITANTE) }
        )
        Spacer(Modifier.height(14.dp))
        OpcionEntrada(
            titulo = "Soy emprendedora · entrar a mi tienda",
            detalle = "También para la administración de la plataforma.",
            icono = androidx.compose.material.icons.Icons.Filled.Storefront, principal = false,
            onClick = { AccesoApp.mostrarLogin = true }
        )
    }
}

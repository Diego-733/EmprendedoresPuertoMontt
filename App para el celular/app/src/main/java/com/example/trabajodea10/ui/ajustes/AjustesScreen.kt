package com.example.trabajodea10.ui.ajustes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.trabajodea10.data.SesionManager
import com.example.trabajodea10.data.Rol
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.ui.auth.autenticarConHuella
import com.example.trabajodea10.ui.auth.autenticadoresHuella
import com.example.trabajodea10.ui.componentes.AvatarUsuario
import com.example.trabajodea10.ui.componentes.BarraSuperior
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.componentes.BotonesTamanoTexto
import com.example.trabajodea10.ui.componentes.ChipEstado
import com.example.trabajodea10.ui.componentes.ChipFiltro
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.trabajodea10.ui.componentes.DatoFila
import com.example.trabajodea10.ui.componentes.FilaSwitch
import com.example.trabajodea10.ui.componentes.LineaSeparadora
import com.example.trabajodea10.ui.componentes.Tarjeta
import com.example.trabajodea10.ui.componentes.TituloSeccion
import com.example.trabajodea10.ui.componentes.colorDeCuenta
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.theme.LineaSuave
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.RojoError
import com.example.trabajodea10.ui.theme.SuperficieVariante
import com.example.trabajodea10.ui.theme.TextoSecundario
import kotlinx.coroutines.launch

@Composable
fun PantallaAjustes(
    nav: ControladorNavegacion,
    avisar: (String) -> Unit
) {
    val rol = TramaStore.rol
    val puedeVolver = nav.pila.size > 1
    val contextoHuella = LocalContext.current
    val actividadHuella = androidx.activity.compose.LocalActivity.current as? FragmentActivity
    var huellaActiva by remember { mutableStateOf(SesionManager.huellaActivada()) }
    val biometriaDisponible = remember {
        BiometricManager.from(contextoHuella).canAuthenticate(autenticadoresHuella()) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Rediseño v2: cabecera de marca (antes, título suelto).
        com.example.trabajodea10.ui.componentes.CabeceraMarca(
            sobretitulo = when (rol) {
                Rol.ADMIN -> "Administración"
                Rol.EMPRENDEDORA -> "Mi cuenta y mi tienda"
                Rol.VISITANTE -> "Mi cuenta"
                null -> null
            },
            titulo = "Ajustes",
            subtitulo = when (rol) {
                Rol.ADMIN -> "Configuración central"
                Rol.EMPRENDEDORA -> "Perfil, tienda y notificaciones"
                Rol.VISITANTE -> "Perfil y preferencias"
                null -> ""
            },
            onVolver = if (puedeVolver) ({ nav.volver() }) else null
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (rol) {
                Rol.ADMIN -> PerfilAdmin(avisar)
                Rol.EMPRENDEDORA -> PerfilEmprendedora(nav, avisar)
                Rol.VISITANTE -> PerfilVisitante(nav, avisar)
                null -> Unit
            }

            // Seguridad: huella disponible para todas las personas con cuenta
            TituloSeccion(texto = "Seguridad y acceso")
            Tarjeta {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    FilaSwitch(
                        titulo = "Ingresar con huella",
                        descripcion = "Usa el lector de huella o biometría del celular para iniciar sesión rápido",
                        checked = huellaActiva,
                        onCheckedChange = { activado ->
                            if (!activado) {
                                SesionManager.activarHuella(false)
                                huellaActiva = false
                                avisar("Huella desactivada.")
                            } else if (SesionManager.emailActual == null) {
                                huellaActiva = false
                                avisar("Inicia sesión con tu correo y contraseña para activar la huella.")
                            } else if (!biometriaDisponible || actividadHuella == null) {
                                huellaActiva = false
                                avisar("Este celular no tiene huella o biometría disponible.")
                            } else {
                                autenticarConHuella(
                                    activity = actividadHuella,
                                    onExito = {
                                        SesionManager.activarHuella(true)
                                        huellaActiva = true
                                        avisar("Huella activada para los próximos inicios de sesión.")
                                    },
                                    onError = { error ->
                                        huellaActiva = SesionManager.huellaActivada()
                                        avisar(error)
                                    },
                                    onCancelar = {
                                        huellaActiva = SesionManager.huellaActivada()
                                    }
                                )
                            }
                        }
                    )
                }
            }

            if (rol == Rol.ADMIN) {
                AjustesSistema()
            } else if (rol == Rol.EMPRENDEDORA) {
                AjustesNotificaciones()
            }

            // Accesibilidad: disponible para todos los perfiles.
            AjustesAccesibilidad(nav)

            // Entrega 8 (A11, RNF-SEG-06): la comprobación de App Check es una herramienta técnica. No se muestra a
            // compradoras ni emprendedoras: queda en «Diagnóstico técnico» para la administración y en las
            // compilaciones de depuración del equipo. La protección funciona igual para todos.
            if (rol == Rol.ADMIN || com.example.trabajodea10.BuildConfig.DEBUG) AjustesDiagnosticoTecnico(avisar)

            // Entrega 8 (RNF-PRI-01): el aviso de privacidad es el mismo de la web.
            val contextoPrivacidad = LocalContext.current
            BotonSecundario(
                texto = "Aviso de privacidad y datos personales",
                onClick = {
                    runCatching {
                        contextoPrivacidad.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(com.example.trabajodea10.ui.publico.URL_VITRINA + "?privacidad=1")))
                    }.onFailure { avisar("No hay un navegador para abrir el aviso.") }
                }
            )

            // Cerrar sesión no borra lo pendiente: se avisa y queda guardado para esta cuenta.
            var avisoPendientes by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            if (avisoPendientes) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { avisoPendientes = false },
                    title = { Text("Tienes cambios sin subir", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                    text = { Text("Hay ${com.example.trabajodea10.data.remoto.Sincronizador.pendientes} cambios que aún no llegan al servidor. Quedan guardados en este celular y se subirán cuando vuelvas a entrar con esta misma cuenta. No se suben con otra cuenta.") },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { avisoPendientes = false; nav.cerrarSesion() }) { Text("Cerrar sesión igual") } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { avisoPendientes = false; com.example.trabajodea10.data.remoto.Sincronizador.subirEnSegundoPlano() }) { Text("Intentar subir ahora") } }
                )
            }
            BotonSecundario(
                texto = "Cerrar sesión",
                onClick = { if (com.example.trabajodea10.data.remoto.Sincronizador.pendientes > 0) avisoPendientes = true else nav.cerrarSesion() },
                icono = { Icon(Icons.Filled.Logout, contentDescription = null, tint = RojoError) },
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "Alerce Emprende · versión ${com.example.trabajodea10.BuildConfig.VERSION_NAME}",
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** Herramientas técnicas plegadas (cerradas por defecto) para no mezclarlas con los ajustes de todos los días. */
@Composable
private fun AjustesDiagnosticoTecnico(avisar: (String) -> Unit) {
    if (!com.example.trabajodea10.seguridad.ProteccionApp.configurada) return
    var abierto by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    androidx.compose.material3.TextButton(
        onClick = { abierto = !abierto },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Text(if (abierto) "Ocultar diagnóstico técnico" else "Diagnóstico técnico (administración)", color = TextoSecundario, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
    if (abierto) AjustesProteccionApp(avisar)
}

/**
 * Entrega 8 (RNF-SEG-06): prueba de Firebase App Check. Escribe una comprobación sin datos
 * personales en Realtime Database; con «Enforce» activo, Firebase solo la acepta desde esta app.
 */
@Composable
private fun AjustesProteccionApp(avisar: (String) -> Unit) {
    val proteccion = com.example.trabajodea10.seguridad.ProteccionApp
    if (!proteccion.configurada) return
    val ambito = rememberCoroutineScope()
    val portapapeles = androidx.compose.ui.platform.LocalClipboardManager.current
    var probando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf<String?>(null) }

    TituloSeccion(texto = "Protección de la app")
    Tarjeta {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Firebase App Check comprueba que las solicitudes vienen de esta app y no de otro programa.",
                style = MaterialTheme.typography.bodyMedium)
            // Lo que verifica de verdad (docs/entrega8/matriz-requisitos.md, RNF-SEG-06): no se promete más.
            Text("Hoy lo verifica solo la comprobación de Firebase (Realtime Database). Supabase y la web no verifican App Check.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            Text("Proveedor: ${proteccion.nombreProveedor}", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            BotonSecundario(
                texto = if (probando) "Comprobando…" else "Comprobar App Check",
                onClick = {
                    if (probando) return@BotonSecundario
                    probando = true
                    ambito.launch {
                        resultado = when (val r = proteccion.comprobar()) {
                            is com.example.trabajodea10.seguridad.ProteccionApp.Resultado.Aceptada ->
                                "Firebase aceptó la solicitud de esta app."
                            is com.example.trabajodea10.seguridad.ProteccionApp.Resultado.Rechazada ->
                                "Firebase rechazó la solicitud: ${r.motivo}"
                            is com.example.trabajodea10.seguridad.ProteccionApp.Resultado.SinRespuesta ->
                                "Sin respuesta de Firebase. Revisa la conexión."
                            is com.example.trabajodea10.seguridad.ProteccionApp.Resultado.SinConfigurar ->
                                "Firebase no está configurado en esta versión."
                        }
                        probando = false
                    }
                }
            )
            resultado?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            if (proteccion.usaDepuracion) {
                androidx.compose.material3.TextButton(onClick = {
                    token = proteccion.tokenDepuracion()
                    if (token == null) avisar("Aún no hay token: toca primero «Comprobar App Check».")
                }) { Text("Ver token de depuración para la consola") }
                token?.let { t ->
                    Text(t, style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.TextButton(onClick = {
                        portapapeles.setText(androidx.compose.ui.text.AnnotatedString(t))
                        avisar("Token copiado.")
                    }) { Text("Copiar token") }
                }
            }
        }
    }
}

@Composable
private fun PerfilAdmin(avisar: (String) -> Unit) {
    val contexto = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var subiendoFoto by remember { mutableStateOf(false) }

    val selectorFoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            subiendoFoto = true
            coroutineScope.launch {
                val res = SesionManager.subirFotoPerfil(contexto, uri)
                subiendoFoto = false
                res.onSuccess { avisar("Foto de perfil actualizada.") }
                    .onFailure { avisar(it.message ?: "No se pudo cambiar la foto.") }
            }
        }
    }

    val nombre = if (SesionManager.nombreUsuarioActual.isNotBlank()) SesionManager.nombreUsuarioActual else "Administrador"
    val email = SesionManager.emailActual ?: "admin@alerce.app"

    Tarjeta {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarUsuario(
                    nombre = nombre,
                    color = 0xFF111111,
                    fotoUrl = SesionManager.fotoPerfilUrl,
                    tamano = 56
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(nombre, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoSecundario
                    )
                }
                ChipEstado(
                    texto = "Super Admin",
                    color = com.example.trabajodea10.ui.componentes.ColorEstado(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = { selectorFoto.launch("image/*") },
                enabled = !subiendoFoto,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NegroApple),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (subiendoFoto) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NegroApple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardando foto...", fontSize = 13.sp, color = NegroApple)
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = NegroApple, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cambiar foto de perfil", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NegroApple)
                }
            }
        }
    }
}

@Composable
private fun PerfilEmprendedora(nav: ControladorNavegacion, avisar: (String) -> Unit) {
    val contexto = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var subiendoFoto by remember { mutableStateOf(false) }
    var subiendoLogo by remember { mutableStateOf(false) }

    val selectorFoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            subiendoFoto = true
            coroutineScope.launch {
                val res = SesionManager.subirFotoPerfil(contexto, uri)
                subiendoFoto = false
                res.onSuccess { avisar("Foto de perfil actualizada.") }
                    .onFailure { avisar(it.message ?: "No se pudo cambiar la foto.") }
            }
        }
    }

    val selectorLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            subiendoLogo = true
            coroutineScope.launch {
                val res = SesionManager.subirLogo(contexto, uri)
                subiendoLogo = false
                res.onSuccess { avisar("Logo del negocio actualizado.") }
                    .onFailure { avisar(it.message ?: "No se pudo cambiar el logo.") }
            }
        }
    }

    val emprendedora = TramaStore.emprendedoraActual
    // Nunca el nombre de los datos de ejemplo: si la cuenta no tiene nombre, «Mi cuenta».
    val nombre = SesionManager.nombreUsuarioActual.ifBlank { "Mi cuenta" }
    val tienda = TramaStore.tienda

    // Perfil personal
    Tarjeta {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarUsuario(
                    nombre = nombre,
                    color = emprendedora.color,
                    fotoUrl = SesionManager.fotoPerfilUrl,
                    tamano = 56
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(nombre, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = SesionManager.emailActual ?: tienda.nombre,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoSecundario
                    )
                }
                ChipEstado(
                    texto = emprendedora.estado.etiqueta,
                    color = colorDeCuenta(emprendedora.estado)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = { selectorFoto.launch("image/*") },
                enabled = !subiendoFoto,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NegroApple),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (subiendoFoto) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NegroApple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardando foto...", fontSize = 13.sp, color = NegroApple)
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = NegroApple, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cambiar foto de perfil", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NegroApple)
                }
            }
        }
    }

    // Logo y vitrina del negocio
    Tarjeta {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarUsuario(
                    nombre = tienda.nombre,
                    color = tienda.colorLogo,
                    fotoUrl = tienda.logoUrl,
                    tamano = 56
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(tienda.nombre, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Logo público del negocio",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoSecundario
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = { selectorLogo.launch("image/*") },
                enabled = !subiendoLogo,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NegroApple),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (subiendoLogo) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NegroApple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardando logo...", fontSize = 13.sp, color = NegroApple)
                } else {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = NegroApple, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cambiar logo del negocio", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NegroApple)
                }
            }
        }
    }

    Tarjeta {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            FilaNavegacion(
                titulo = "Mi tienda",
                descripcion = "Logo, portada, bio y contacto",
                icono = { Icon(Icons.Filled.Store, contentDescription = null, tint = TextoSecundario) },
                onClick = { nav.irA(Ruta.EmpTienda) }
            )
            LineaSeparadora()
            FilaNavegacion(
                titulo = "Mis ferias",
                descripcion = "Invitaciones y estados de participación",
                icono = { Icon(Icons.Filled.Public, contentDescription = null, tint = TextoSecundario) },
                onClick = { nav.irA(Ruta.EmpFerias) }
            )
        }
    }
}

@Composable
private fun FilaNavegacion(
    titulo: String,
    descripcion: String,
    icono: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { icono() }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.Medium)
            Text(descripcion, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextoSecundario)
    }
}

@Composable
private fun PerfilVisitante(nav: ControladorNavegacion, avisar: (String) -> Unit) {
    val contexto = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var subiendoFoto by remember { mutableStateOf(false) }
    var mostrarCrearTienda by remember { mutableStateOf(false) }
    var nombreTienda by remember { mutableStateOf("") }
    var creandoTienda by remember { mutableStateOf(false) }

    val selectorFoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            subiendoFoto = true
            coroutineScope.launch {
                val res = SesionManager.subirFotoPerfil(contexto, uri)
                subiendoFoto = false
                res.onSuccess { avisar("Foto de perfil actualizada.") }
                    .onFailure { avisar(it.message ?: "No se pudo cambiar la foto.") }
            }
        }
    }

    val nombre = if (SesionManager.nombreUsuarioActual.isNotBlank()) SesionManager.nombreUsuarioActual else "Comprador"
    val email = SesionManager.emailActual ?: "Sin correo registrado"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Modo comprador sin cuenta (navegación anónima permitida): ofrecer ingresar.
        if (SesionManager.emailActual == null) {
            com.example.trabajodea10.ui.componentes.BotonPrimario(
                texto = "Ingresar o crear cuenta",
                onClick = { nav.irAIngreso() }
            )
        }
        Tarjeta {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarUsuario(
                        nombre = nombre,
                        color = 0xFF6B7FB3,
                        fotoUrl = SesionManager.fotoPerfilUrl,
                        tamano = 56
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(nombre, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextoSecundario
                        )
                    }
                    ChipEstado(
                        texto = "Comprador",
                        color = com.example.trabajodea10.ui.componentes.ColorEstado(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = { selectorFoto.launch("image/*") },
                enabled = !subiendoFoto,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NegroApple),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (subiendoFoto) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NegroApple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardando foto...", fontSize = 13.sp, color = NegroApple)
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = NegroApple, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cambiar foto de perfil", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NegroApple)
                }
            }
            }
        }

        OutlinedButton(
            onClick = { mostrarCrearTienda = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, NegroApple),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
        ) {
            Icon(Icons.Filled.Store, contentDescription = null, tint = NegroApple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Crear mi tienda y empezar a vender", fontWeight = FontWeight.SemiBold, color = NegroApple)
        }
    }

    // Entrega 7: consentimiento de avisos, lo que sigue y su bandeja (RNF-PRI-02)
    com.example.trabajodea10.ui.comprador.SeccionAvisosCuenta(nav, avisar)

    if (mostrarCrearTienda) {
        AlertDialog(
            onDismissRequest = { if (!creandoTienda) mostrarCrearTienda = false },
            title = { Text("Crear mi tienda", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Con tu tienda podrás agregar productos, precios y fotos. Luego podrás completar sus datos desde la pestaña Tienda.")
                    OutlinedTextField(
                        value = nombreTienda,
                        onValueChange = { nombreTienda = it },
                        label = { Text("Nombre de la tienda") },
                        singleLine = true,
                        enabled = !creandoTienda,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = nombreTienda.isNotBlank() && !creandoTienda,
                    onClick = {
                        creandoTienda = true
                        coroutineScope.launch {
                            val resultado = SesionManager.crearTiendaParaUsuario(nombreTienda)
                            creandoTienda = false
                            resultado.onSuccess { rol ->
                                mostrarCrearTienda = false
                                nav.iniciarSesion(rol)
                                avisar("Tienda creada. Ya puedes agregar tus productos.")
                            }.onFailure { error ->
                                avisar(error.message ?: "No se pudo crear tu tienda.")
                            }
                        }
                    }
                ) { Text(if (creandoTienda) "Creando…" else "Crear tienda") }
            },
            dismissButton = {
                TextButton(enabled = !creandoTienda, onClick = { mostrarCrearTienda = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun AjustesSistema() {
    val config = TramaStore.config

    TituloSeccion(texto = "Sistema")
    Tarjeta {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            DatoFila(etiqueta = "Moneda", valor = config.moneda)
            LineaSeparadora()
            DatoFila(etiqueta = "Zona horaria", valor = config.zonaHoraria)
            LineaSeparadora()
            DatoFila(etiqueta = "Idioma", valor = "Español (Chile)")
        }
    }

    TituloSeccion(texto = "Interacción pública")
    Tarjeta {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            FilaSwitch(
                titulo = "Registro público de cuentas",
                descripcion = "Permite que emprendedoras se registren desde la web",
                checked = config.registroPublico,
                onCheckedChange = {
                    TramaStore.guardarConfig(config.copy(registroPublico = it))
                }
            )
            FilaSwitch(
                titulo = "Catálogo visible para visitantes",
                descripcion = "Muestra los productos publicados sin iniciar sesión",
                checked = config.catalogoPublico,
                onCheckedChange = {
                    TramaStore.guardarConfig(config.copy(catalogoPublico = it))
                }
            )
            FilaSwitch(
                titulo = "Chat directo",
                descripcion = "Habilita mensajes entre emprendedoras",
                checked = config.chatDirecto,
                onCheckedChange = {
                    TramaStore.guardarConfig(config.copy(chatDirecto = it))
                }
            )
            FilaSwitch(
                titulo = "Ferias visibles públicamente",
                descripcion = "Las ferias aparecen en el calendario público",
                checked = config.feriasPublicas,
                onCheckedChange = {
                    TramaStore.guardarConfig(config.copy(feriasPublicas = it))
                }
            )
        }
    }
}

@Composable
private fun AjustesNotificaciones() {
    val prefs = TramaStore.prefs

    TituloSeccion(texto = "Notificaciones")
    Tarjeta {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            FilaSwitch(
                titulo = "Alertas de stock bajo",
                descripcion = "Avisa cuando un producto queda con 3 unidades o menos",
                checked = prefs.alertasStock,
                onCheckedChange = { TramaStore.guardarPrefs(prefs.copy(alertasStock = it)) }
            )
            FilaSwitch(
                titulo = "Recordatorios de ferias",
                descripcion = "Te avisa 24 horas antes de una feria confirmada",
                checked = prefs.recordatoriosFeria,
                onCheckedChange = { TramaStore.guardarPrefs(prefs.copy(recordatoriosFeria = it)) }
            )
            FilaSwitch(
                titulo = "Resumen semanal de ventas",
                descripcion = "Envía un resumen los lunes",
                checked = prefs.resumenVentas,
                onCheckedChange = { TramaStore.guardarPrefs(prefs.copy(resumenVentas = it)) }
            )
        }
    }
}

/** Control de accesibilidad para agrandar el texto, disponible para todos los perfiles. */
@Composable
private fun AjustesAccesibilidad(nav: ControladorNavegacion) {
    TituloSeccion(texto = "Accesibilidad")
    Tarjeta {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tamaño del texto",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Agranda todo el texto de la app para leerlo más fácil.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextoSecundario,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            BotonesTamanoTexto()
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    AjustesLecturaVoz()
    if (TramaStore.rol == com.example.trabajodea10.data.Rol.EMPRENDEDORA) {
        Spacer(modifier = Modifier.height(12.dp))
        AjustesModoSencillo(nav)
    }
}

/**
 * Modo sencillo (opcional y reversible): Inicio con botones grandes. Se guarda por cuenta
 * (en las preferencias de la emprendedora), así que no pasa a otra cuenta del mismo celular.
 */
@Composable
private fun AjustesModoSencillo(nav: ControladorNavegacion) {
    val prefs = TramaStore.prefs
    Tarjeta {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilaSwitch(
                titulo = "Modo sencillo",
                descripcion = "Inicio con botones grandes: hablar con el asistente, vender, agregar un producto, ver productos y ferias, compartir y ayuda. Todo lo demás sigue en la barra de abajo.",
                checked = prefs.modoSencillo,
                onCheckedChange = { TramaStore.guardarPrefs(prefs.copy(modoSencillo = it)) }
            )
            BotonSecundario(texto = "Abrir el centro de ayuda", onClick = { nav.irA(com.example.trabajodea10.ui.navegacion.Ruta.CentroAyuda) })
        }
    }
}

/**
 * Lectura en voz alta (guía y Asistente Alerce): con la voz del propio celular, sin costo.
 * Si el celular no tiene voz en español, se avisa y la app sigue igual con el texto.
 */
@Composable
private fun AjustesLecturaVoz() {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val voz = com.example.trabajodea10.voz.LectorVoz
    val talkBack = remember { voz.lectorDePantallaActivo(contexto) }
    // Si antes no había voz en español, al volver a Ajustes se intenta de nuevo (pudo instalarse una).
    androidx.compose.runtime.LaunchedEffect(Unit) { voz.reintentarSiNoHabia() }
    Tarjeta {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lectura en voz alta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "En la guía y en el Asistente Alerce aparece «🔊 Escuchar». Usa la voz de tu celular.",
                style = MaterialTheme.typography.bodyMedium, color = TextoSecundario
            )
            FilaSwitch(
                titulo = "Leer en voz alta sin tocar «Escuchar»",
                descripcion = if (talkBack) "Con TalkBack activo no se lee solo, para no hablar encima del lector de pantalla."
                    else "Al pasar de paso o recibir una respuesta, se lee solo.",
                checked = voz.lecturaAutomatica,
                onCheckedChange = { voz.cambiarLecturaAutomatica(it) }
            )
            Text("Velocidad", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.example.trabajodea10.voz.LectorVoz.Velocidad.entries.forEach { v ->
                    ChipFiltro(texto = v.etiqueta, seleccionado = voz.velocidad == v, onClick = { voz.cambiarVelocidad(v) })
                }
            }
            if (voz.disponible == false) {
                Text(
                    "Este celular no tiene una voz en español instalada. Puedes agregarla en Ajustes del celular › Idioma › Texto a voz. Mientras tanto, todo se muestra por escrito.",
                    style = MaterialTheme.typography.bodyMedium, color = TextoSecundario,
                    modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
                )
            } else {
                com.example.trabajodea10.voz.BotonEscuchar(
                    id = "ajustes-prueba",
                    texto = "Hola. Así suena la lectura en voz alta de Alerce. Un chaleco de lana cuesta \$15.000.",
                    etiqueta = "Probar la voz"
                )
            }
        }
    }
}

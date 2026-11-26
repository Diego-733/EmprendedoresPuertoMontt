package com.example.trabajodea10.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta Alerce (la misma lógica que la web: css/alerce.css).
// Musgo del bosque sureño para las acciones importantes, corteza del alerce para los precios,
// neblina de Puerto Montt de fondo. Los estados siempre van con texto; el color solo lo refuerza.
val Musgo = Color(0xFF1F5C46)            // acción principal · 7,8:1 sobre blanco
val MusgoOscuro = Color(0xFF15412F)
val MusgoClaro = Color(0xFFE4EFEA)
val Corteza = Color(0xFF8C3B22)          // precios y marca · 7,6:1 sobre blanco
val Tinta = Color(0xFF1E2421)            // texto
val TintaSuave = Color(0xFF4F5B55)       // texto secundario · 7:1 sobre blanco
val Neblina = Color(0xFFF5F3EE)          // fondo cálido (antes gris verdoso F3F5F2)
val Linea = Color(0xFFDDD8CE)

// Nueva generación (rediseño v2): bosque profundo para cabeceras de marca, brote para degradados
// y ámbar como acento comercial (insignias, cifras destacadas). El ámbar se usa siempre con texto oscuro.
val Bosque = Color(0xFF0E3B2E)           // cabeceras · texto blanco 12,9:1
val BosqueProfundo = Color(0xFF09271E)
val Brote = Color(0xFF2F7A5A)            // extremo claro del degradado de cabecera · blanco 5,4:1
val SobreBosqueSuave = Color(0xFFCFE3DA) // texto secundario sobre Bosque · 10:1
val Ambar = Color(0xFFF4B740)            // acento · Tinta sobre ámbar 10:1
val AmbarSuave = Color(0xFFFFF1D1)
val AmbarTexto = Color(0xFF6B4500)       // sobre AmbarSuave 7,4:1
val Superficie2 = Color(0xFFFBFAF7)      // segundo nivel dentro de tarjetas

// Estados: «Disponible / Guardado», «Pendiente / Quedan pocos», «Se acabó», «A pedido».
val EstadoOkTexto = Color(0xFF1E6B3A)
val EstadoOkFondo = Color(0xFFE3F1E6)
val EstadoPendTexto = Color(0xFF7A4E00)
val EstadoPendFondo = Color(0xFFFBEFD2)
val EstadoAcaboTexto = Color(0xFF9B2C1E)
val EstadoAcaboFondo = Color(0xFFFBE4E0)
val EstadoPedidoTexto = Color(0xFF2E4E7A)
val EstadoPedidoFondo = Color(0xFFE4ECF6)

// Nombres anteriores, reasignados a la paleta Alerce para que toda la app la tome.
val NegroApple = Tinta
val NegroSuave = MusgoOscuro
val RojoAlerce = Corteza
val RojoAlerceContenedor = EstadoAcaboFondo
val RojoAlerceOscuro = Color(0xFF6E2A15)
val BlancoPuro = Color(0xFFFFFFFF)
val FondoLimpio = Neblina
val GrisBorde = Linea
val GrisTexto = TintaSuave
val GrisChip = Color(0xFFEAEFEB)

val Berenjena = Musgo
val BerenjenaOscuro = MusgoOscuro
val BerenjenaContenedor = MusgoClaro
val SobreBerenjenaContenedor = MusgoOscuro

val VerdeOk = EstadoOkTexto
val VerdeContenedor = EstadoOkFondo
val AmarilloAlerta = Color(0xFFB7791F)
val NaranjaAlerta = EstadoPendTexto
val NaranjaContenedor = EstadoPendFondo
val RojoError = EstadoAcaboTexto
val RojoContenedor = EstadoAcaboFondo

val FondoNeutro = FondoLimpio
val SuperficieBlanca = BlancoPuro
val SuperficieVariante = GrisChip
val TextoSecundario = GrisTexto
val LineaSuave = GrisBorde

// Modo oscuro
val DarkBackground = Color(0xFF0F1412)
val DarkSurface = Color(0xFF18201C)
val DarkSurfaceVariant = Color(0xFF243029)
val DarkPrimary = Color(0xFF8FD6B8)
val DarkOnPrimary = Color(0xFF0E3A2A)
val DarkOnSurfaceVariant = Color(0xFFB4C2BA)
val DarkOutline = Color(0xFF3E4C44)

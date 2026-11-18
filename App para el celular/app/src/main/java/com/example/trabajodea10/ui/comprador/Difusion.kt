package com.example.trabajodea10.ui.comprador

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.theme.TextoSecundario
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

/*
 * Difusión (RF-COM-01, RF-COM-02): compartir con el menú nativo de Android y
 * códigos QR que se guardan o se comparten como imagen. Todos los enlaces son
 * de la vitrina web pública, que se ve sin iniciar sesión (RF-COM-03).
 */

/** Abre el menú nativo de compartir de Android (WhatsApp, redes, correo…). */
fun compartirEnlace(contexto: Context, titulo: String, url: String) {
    val enviar = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, titulo)
        putExtra(Intent.EXTRA_TEXT, "$titulo\n$url")
    }
    val selector = Intent.createChooser(enviar, "Compartir").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { contexto.startActivity(selector) }
        .onFailure { Toast.makeText(contexto, "No hay aplicaciones para compartir.", Toast.LENGTH_SHORT).show() }
}

/** Abre la aplicación de mapas del celular en ese punto ("Cómo llegar", RF-MAP-02). */
fun abrirComoLlegar(contexto: Context, lat: Double, lng: Double, nombre: String) {
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse(com.example.trabajodea10.data.ReglasVitrina.uriComoLlegar(lat, lng, nombre)))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val abrio = runCatching { contexto.startActivity(geo) }.isSuccess
    if (!abrio) {
        // Sin app de mapas instalada: se abre Google Maps en el navegador con el destino.
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { contexto.startActivity(web) }
            .onFailure { Toast.makeText(contexto, "No se encontró una aplicación de mapas.", Toast.LENGTH_SHORT).show() }
    }
}

/** QR con borde blanco y el nombre debajo, listo para imprimir o compartir. */
fun generarImagenQR(contenido: String, titulo: String, tamano: Int = 720): Bitmap? = runCatching {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
    val matriz = QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, tamano, tamano, hints)
    val altoTexto = 110
    val bmp = Bitmap.createBitmap(tamano, tamano + altoTexto, Bitmap.Config.ARGB_8888)
    val pixeles = IntArray(tamano * tamano)
    for (y in 0 until tamano) for (x in 0 until tamano) {
        pixeles[y * tamano + x] = if (matriz[x, y]) Color.BLACK else Color.WHITE
    }
    bmp.setPixels(pixeles, 0, tamano, 0, 0, tamano, tamano)
    val lienzo = Canvas(bmp)
    lienzo.drawRect(0f, tamano.toFloat(), tamano.toFloat(), (tamano + altoTexto).toFloat(), Paint().apply { color = Color.WHITE })
    val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 38f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val texto = if (titulo.length > 34) titulo.take(33) + "…" else titulo
    lienzo.drawText(texto, tamano / 2f, tamano + 48f, pincel)
    pincel.textSize = 24f
    pincel.isFakeBoldText = false
    lienzo.drawText("Escanéame · Alerce Emprende", tamano / 2f, tamano + 88f, pincel)
    bmp
}.getOrNull()

private fun nombreArchivo(titulo: String): String =
    "qr_" + titulo.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifBlank { "alerce" } + ".png"

/** Guarda el QR en la galería (Imágenes/Alerce). Devuelve el lugar donde quedó o null. */
fun guardarQREnGaleria(contexto: Context, bitmap: Bitmap, titulo: String): String? = runCatching {
    val nombre = nombreArchivo(titulo)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val valores = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, nombre)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Alerce")
        }
        val uri = contexto.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores)
            ?: return@runCatching null
        contexto.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "Galería › Imágenes › Alerce"
    } else {
        // Android 7 a 9: carpeta de imágenes de la app (no requiere permisos extra).
        val carpeta = contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: contexto.filesDir
        val archivo = File(carpeta, nombre)
        FileOutputStream(archivo).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        archivo.absolutePath
    }
}.getOrNull()

/** Comparte el QR como imagen (con el enlace en el texto) mediante el menú nativo. */
fun compartirQRComoImagen(contexto: Context, bitmap: Bitmap, titulo: String, url: String) {
    runCatching {
        val carpeta = File(contexto.cacheDir, "qr").apply { mkdirs() }
        val archivo = File(carpeta, nombreArchivo(titulo))
        FileOutputStream(archivo).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(contexto, contexto.packageName + ".fileprovider", archivo)
        val enviar = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "$titulo\n$url")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        contexto.startActivity(Intent.createChooser(enviar, "Compartir QR").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(contexto, "No se pudo compartir el QR.", Toast.LENGTH_SHORT).show() }
}

/** Diálogo con el QR y los botones Guardar / Compartir (RF-COM-02). */
@Composable
fun DialogoQR(titulo: String, url: String, onCerrar: () -> Unit) {
    val contexto = LocalContext.current
    val bitmap = remember(url, titulo) { generarImagenQR(url, titulo) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Código QR", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (bitmap != null) {
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Código QR de $titulo", modifier = Modifier.size(240.dp))
                } else {
                    Text("No se pudo generar el código.")
                }
                Text(url, style = MaterialTheme.typography.bodySmall, color = TextoSecundario, textAlign = TextAlign.Center)
                BotonPrimario(
                    texto = "Compartir imagen",
                    enabled = bitmap != null,
                    icono = { Icon(Icons.Filled.Share, contentDescription = null) },
                    onClick = { bitmap?.let { compartirQRComoImagen(contexto, it, titulo, url) } }
                )
                BotonSecundario(
                    texto = "Guardar en el celular",
                    enabled = bitmap != null,
                    icono = { Icon(Icons.Filled.Download, contentDescription = null) },
                    onClick = {
                        val lugar = bitmap?.let { guardarQREnGaleria(contexto, it, titulo) }
                        Toast.makeText(
                            contexto,
                            if (lugar != null) "QR guardado en $lugar" else "No se pudo guardar el QR.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
        modifier = Modifier.fillMaxWidth()
    )
}

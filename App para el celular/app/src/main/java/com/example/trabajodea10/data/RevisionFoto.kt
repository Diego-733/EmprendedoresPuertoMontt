package com.example.trabajodea10.data

import android.graphics.BitmapFactory
import java.io.File

/**
 * Revisión local y liviana de una foto de producto: solo si está muy oscura o muy clara.
 * Es un consejo, nunca bloquea el guardado. No usa servicios externos ni librerías pesadas.
 *
 * El desenfoque no se revisa: sin calibrar con fotos reales, una estimación así se equivoca
 * demasiado y molestaría más de lo que ayuda.
 */
object RevisionFoto {

    /** Luminancia promedio (0 a 255) de píxeles ARGB. */
    fun promedioLuminancia(pixeles: IntArray): Double {
        if (pixeles.isEmpty()) return 128.0
        var suma = 0.0
        for (c in pixeles) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            suma += 0.299 * r + 0.587 * g + 0.114 * b
        }
        return suma / pixeles.size
    }

    /** Consejo según la luz, o null si se ve bien. */
    fun consejo(luminancia: Double): String? = when {
        luminancia < 55 -> "La foto se ve oscura. Si puedes, tómala de nuevo cerca de una ventana o con más luz. Si te gusta así, puedes seguir."
        luminancia > 235 -> "La foto se ve muy clara y puede que no se note el producto. Prueba con menos luz directa. Si te gusta así, puedes seguir."
        else -> null
    }

    /** Revisa un archivo del celular (las fotos ya subidas no se revisan). null si no hay consejo o no se pudo leer. */
    fun revisarArchivo(ruta: String?): String? {
        if (ruta == null || ruta.startsWith("http")) return null
        val archivo = File(ruta)
        if (!archivo.exists()) return null
        return runCatching {
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(archivo.path, limites)
            var muestra = 1
            while (limites.outWidth / (muestra * 2) >= 64 && limites.outHeight / (muestra * 2) >= 64) muestra *= 2
            val bmp = BitmapFactory.decodeFile(archivo.path, BitmapFactory.Options().apply { inSampleSize = muestra }) ?: return null
            val pixeles = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixeles, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            bmp.recycle()
            consejo(promedioLuminancia(pixeles))
        }.getOrNull()
    }
}

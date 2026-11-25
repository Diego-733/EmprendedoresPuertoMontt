package com.example.trabajodea10.avisos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.trabajodea10.BuildConfig
import com.example.trabajodea10.MainActivity
import com.example.trabajodea10.R
import com.example.trabajodea10.data.FeriasStore
import com.example.trabajodea10.data.remoto.ClienteSupabase
import com.example.trabajodea10.data.remoto.FeriasRepositorio
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.TimeUnit

/**
 * Avisos en el celular (Entrega 7):
 *  - Push con Firebase Cloud Messaging si la app tiene firebase.properties: llega con
 *    la app en segundo plano o cerrada (no tras una «detención forzada» de Android).
 *  - Sin Firebase, una revisión periódica (WorkManager: mínimo cada 15 min, con red; Android puede postergarla) muestra
 *    los avisos nuevos de la bandeja. No es push: puede demorar y Android la agrupa
 *    para ahorrar batería.
 * En ambos casos se marca «recibido» en el servidor, distinto de «enviado».
 */
object AvisosCelular {

    private const val TAG = "AvisosCelular"
    const val CANAL = "avisos"
    const val EXTRA_FERIA = "feria_id"
    const val EXTRA_AVISO = "aviso_id"
    private const val TRABAJO_PERIODICO = "revisar_avisos"

    val firebaseConfigurado: Boolean
        get() = BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() && BuildConfig.FIREBASE_SENDER_ID.isNotBlank()

    fun crearCanal(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL, "Avisos de ferias y mensajes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Invitaciones a ferias, recordatorios, avisos de quienes sigues y mensajes de tu tienda"
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
        }
    }

    /** Android 13+ exige permiso para mostrar notificaciones; antes basta con el canal activo. */
    fun puedeNotificar(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        // Si la persona silenció el canal en los ajustes, la notificación no se vería.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CANAL)
            if (canal != null && canal.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    /**
     * Respaldo periódico (sin push): solo si NO hay Firebase y el celular puede mostrar
     * notificaciones. Con Firebase, el push es el mecanismo y no se consulta periódicamente.
     * Una consulta por ejecución; Android decide cuándo (mínimo 15 min, con red).
     */
    fun actualizarRespaldo(context: Context) {
        if (!firebaseConfigurado && puedeNotificar(context)) { programar(context); revisarAhora(context) } else cancelar(context)
    }

    /** Muestra un aviso. El tag es el id del aviso: si llega dos veces (push y revisión), se reemplaza. */
    fun mostrar(context: Context, avisoId: String, titulo: String, cuerpo: String, feriaId: String?): Boolean {
        if (!puedeNotificar(context)) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_AVISO, avisoId)
            if (feriaId != null) putExtra(EXTRA_FERIA, feriaId)
        }
        val pendiente = PendingIntent.getActivity(context, avisoId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)   // silueta de un color (el ícono a color se veía como un cuadrado)
            .setColor(0xFF1F5C46.toInt())
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendiente)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(avisoId, 0, n)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Sin permiso para notificar: ${e.message}")
            false
        }
    }

    /** Programa la revisión periódica (solo con una cuenta iniciada). */
    fun programar(context: Context) {
        val restricciones = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val trabajo = PeriodicWorkRequestBuilder<RevisarAvisosWorker>(15, TimeUnit.MINUTES).setConstraints(restricciones).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(TRABAJO_PERIODICO, ExistingPeriodicWorkPolicy.KEEP, trabajo)
    }

    fun revisarAhora(context: Context) {
        val restricciones = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniqueWork("revisar_avisos_ahora", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RevisarAvisosWorker>().setConstraints(restricciones).build())
    }

    fun cancelar(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(TRABAJO_PERIODICO)
    }

    /** Inicia Firebase sin google-services.json (con los identificadores públicos de firebase.properties). */
    fun iniciarFirebase(context: Context): Boolean {
        if (!firebaseConfigurado) return false
        return runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val opciones = FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                // Entrega 8: Realtime Database solo para demostrar App Check (ver seguridad/ProteccionApp).
                if (BuildConfig.FIREBASE_DATABASE_URL.isNotBlank()) opciones.setDatabaseUrl(BuildConfig.FIREBASE_DATABASE_URL)
                FirebaseApp.initializeApp(context, opciones.build())
                com.example.trabajodea10.seguridad.ProteccionApp.recordarContexto(context)
                com.example.trabajodea10.seguridad.ProteccionApp.instalar()
            }
            true
        }.onFailure { Log.w(TAG, "Firebase no se pudo iniciar: ${it.message}") }.getOrDefault(false)
    }

    /** Pide el token de este celular para registrarlo en la cuenta (registrar_dispositivo). */
    fun obtenerToken(alObtener: (String) -> Unit) {
        if (!firebaseConfigurado) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { t -> if (!t.isNullOrBlank()) alObtener(t) }
        }.onFailure { Log.w(TAG, "Sin token de Firebase: ${it.message}") }
    }

    fun borrarToken() {
        if (!firebaseConfigurado) return
        runCatching { FirebaseMessaging.getInstance().deleteToken() }
    }
}

/** Revisa la bandeja y muestra los avisos que aún no llegaron a este celular. */
class RevisarAvisosWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (ClienteSupabase.sesion == null) return Result.success()
        return runCatching {
            val limite = System.currentTimeMillis() - 2 * 24 * 3600 * 1000L
            // El servidor aplica la misma regla que al enviar push (consentimiento, seguir, horario, feria).
            val destino = FeriasStore.destino()
            FeriasRepositorio.avisosParaMostrar(destino, 10)
                .filter { FeriasStore.instanteIso(it.creadoEn) >= limite }
                .forEach { a ->
                    // «Mostrado» solo si el sistema aceptó mostrarla (permiso y canal activos).
                    if (AvisosCelular.mostrar(applicationContext, a.id, a.titulo, a.cuerpo, a.feriaId)) {
                        runCatching { FeriasRepositorio.confirmarAviso(a.id, destino, "MOSTRADO") }
                    }
                }
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

/** Push de Firebase. Con la app abierta llega aquí; en segundo plano lo muestra Android. */
class ServicioMensajes : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FeriasStore.alRenovarToken(token)
    }

    override fun onMessageReceived(mensaje: RemoteMessage) {
        val datos = mensaje.data
        val avisoId = datos["aviso_id"] ?: mensaje.messageId ?: return
        val titulo = mensaje.notification?.title ?: datos["titulo"] ?: "Aviso de Alerce"
        val cuerpo = mensaje.notification?.body ?: datos["cuerpo"] ?: ""
        if (AvisosCelular.mostrar(applicationContext, avisoId, titulo, cuerpo, datos["feria_id"]?.ifBlank { null })) {
            FeriasStore.confirmarAviso(avisoId, "MOSTRADO")   // primer plano: la app la mostró
        }
    }
}

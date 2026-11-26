package com.example.trabajodea10.ui.asistente

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.trabajodea10.data.AdminStore
import com.example.trabajodea10.data.ReglasAdmin
import com.example.trabajodea10.data.Rol
import com.example.trabajodea10.data.SesionManager
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.VitrinaStore
import com.example.trabajodea10.data.asistente.ContextoGeneral
import com.example.trabajodea10.data.asistente.InterpreteGeneral
import com.example.trabajodea10.data.asistente.SesionAsistente
import com.example.trabajodea10.data.remoto.ClienteSupabase

/** Búsqueda que el asistente pidió para la vitrina: la aplica PantallaDirectorio una vez. */
object PedidoVitrina {
    var pendiente by mutableStateOf<SesionAsistente.Accion.Buscar?>(null)
}

/** Datos reales y de solo lectura para el asistente general: ferias publicadas, tiendas públicas y conteos del panel. */
class ContextoGeneralApp(private val rol: InterpreteGeneral.Rol) : ContextoGeneral {
    override fun ferias(): List<ContextoGeneral.Feria> {
        val hoy = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        return VitrinaStore.ferias.toList()
            .filter { it.estado != "CANCELADA" && it.estado != "BORRADOR" && (it.fechaFin ?: it.fechaInicio ?: hoy) >= hoy }
            .sortedBy { it.fechaInicio ?: "9999" }
            .map { ContextoGeneral.Feria(it.id, it.nombre, listOfNotNull(it.fechaInicio, it.horario.ifBlank { null }).joinToString(" ").ifBlank { "fecha por confirmar" }, it.lugar) }
    }

    override fun tiendas(): List<ContextoGeneral.TiendaPublica> =
        SesionManager.tiendas.toList().map { ContextoGeneral.TiendaPublica(it.id, it.nombre, it.rubro, it.sector) }

    override fun conSesion(): Boolean = ClienteSupabase.sesion != null

    override fun resumenAdmin(): ContextoGeneral.ResumenAdmin? {
        if (rol != InterpreteGeneral.Rol.ADMIN || !AdminStore.cargadoAlgunaVez) return null
        val r = ReglasAdmin.resumen(AdminStore.cuentas.toList())
        return ContextoGeneral.ResumenAdmin(r.activas, r.pendientes, r.desactivadas, r.sinAutorizacion)
    }

    companion object {
        /** null = emprendedora (usa SesionAsistente, que puede operar su tienda con confirmación). */
        fun rolActual(): InterpreteGeneral.Rol? = when (TramaStore.rol) {
            Rol.EMPRENDEDORA -> null
            Rol.ADMIN -> InterpreteGeneral.Rol.ADMIN
            Rol.VISITANTE, null -> if (ClienteSupabase.sesion != null) InterpreteGeneral.Rol.COMPRADOR else InterpreteGeneral.Rol.VISITANTE
        }
    }
}

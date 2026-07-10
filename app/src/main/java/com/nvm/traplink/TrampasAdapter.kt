package com.nvm.traplink

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nvm.traplink.data.MisDispositivosResponseDto

class TrampasAdapter(
    private val listaTrampas: List<MisDispositivosResponseDto>,
    private val onTrampaClick: (MisDispositivosResponseDto) -> Unit
) : RecyclerView.Adapter<TrampasAdapter.TrampaViewHolder>() {

    class TrampaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumeroSerie: TextView = view.findViewById(R.id.tvNumeroSerie)
        val tvEstadoTrampa: TextView = view.findViewById(R.id.tvEstadoTrampa)
        val vStatusDot: View = view.findViewById(R.id.vStatusDot) // Vinculamos el punto
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrampaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trampa, parent, false)
        return TrampaViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrampaViewHolder, position: Int) {
        val trampa = listaTrampas[position]
        holder.tvNumeroSerie.text = "Dispositivo: ${trampa.numeroSerie}"
        holder.tvEstadoTrampa.text = "Estado: ${trampa.estado}"

        val context = holder.itemView.context

        // Unificación de la lógica visual (Texto + Círculo)
        if (!trampa.enLinea) {
            // CASO 1: Desconectado (Gris)
            holder.tvEstadoTrampa.setTextColor(ContextCompat.getColor(context, R.color.status_disconnected))
            holder.vStatusDot.setBackgroundResource(R.drawable.circle_status_offline) // Asegura usar tu drawable gris
        } else if (trampa.estado.contains("Captura", ignoreCase = true) || trampa.estado.contains("Alerta", ignoreCase = true)) {
            // CASO 2: Captura / Alerta activa (Rojo)
            holder.tvEstadoTrampa.setTextColor(ContextCompat.getColor(context, R.color.status_capture))
            // Si tienes un drawable para círculo rojo puedes ponerlo aquí, ej: holder.vStatusDot.setBackgroundResource(R.drawable.circle_status_capture)
        } else {
            // CASO 3: Conectado / Monitoreando normal (Verde)
            holder.tvEstadoTrampa.setTextColor(ContextCompat.getColor(context, R.color.status_active))
            // Si tienes un drawable para círculo verde, ej: holder.vStatusDot.setBackgroundResource(R.drawable.circle_status_online)
        }

        holder.itemView.setOnClickListener { onTrampaClick(trampa) }
    }

    override fun getItemCount(): Int = listaTrampas.size
}
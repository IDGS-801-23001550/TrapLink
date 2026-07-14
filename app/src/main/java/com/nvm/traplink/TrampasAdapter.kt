package com.nvm.traplink

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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrampaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trampa, parent, false)
        return TrampaViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrampaViewHolder, position: Int) {
        val trampa = listaTrampas[position]
        val context = holder.itemView.context

        holder.tvNumeroSerie.text = trampa.numeroSerie

        when {
            !trampa.enLinea -> {
                holder.tvEstadoTrampa.text = "Desconectado"
                holder.tvEstadoTrampa.setBackgroundResource(R.drawable.bg_chip_offline)
                holder.tvEstadoTrampa.setTextColor(ContextCompat.getColor(context, R.color.chip_text_offline))
            }
            trampa.estado.contains("Captura", ignoreCase = true) || trampa.estado.contains("Alerta", ignoreCase = true) -> {
                holder.tvEstadoTrampa.text = "Captura detectada"
                holder.tvEstadoTrampa.setBackgroundResource(R.drawable.bg_chip_capture)
                holder.tvEstadoTrampa.setTextColor(ContextCompat.getColor(context, R.color.chip_text_capture))
            }
            trampa.estado.contains("Batería", ignoreCase = true) || trampa.estado.contains("Bateria", ignoreCase = true) -> {
                holder.tvEstadoTrampa.text = "Batería baja"
                holder.tvEstadoTrampa.setBackgroundResource(R.drawable.bg_chip_battery)
                holder.tvEstadoTrampa.setTextColor(ContextCompat.getColor(context, R.color.chip_text_battery))
            }
            else -> {
                holder.tvEstadoTrampa.text = "Activo"
                holder.tvEstadoTrampa.setBackgroundResource(R.drawable.bg_chip_active)
                holder.tvEstadoTrampa.setTextColor(ContextCompat.getColor(context, R.color.chip_text_active))
            }
        }

        holder.itemView.setOnClickListener { onTrampaClick(trampa) }
    }

    override fun getItemCount(): Int = listaTrampas.size
}
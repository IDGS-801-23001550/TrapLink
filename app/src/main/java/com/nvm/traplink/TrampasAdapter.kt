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
        holder.tvNumeroSerie.text = "Dispositivo: ${trampa.numeroSerie}"
        holder.tvEstadoTrampa.text = "Estado: ${trampa.estado}"

        val context = holder.itemView.context
        if (trampa.estado.contains("Captura", ignoreCase = true)) {
            holder.tvEstadoTrampa.setTextColor(ContextCompat.getColor(context, R.color.status_capture))
        } else {
            holder.tvEstadoTrampa.setTextColor(ContextCompat.getColor(context, R.color.status_active))
        }

        holder.itemView.setOnClickListener { onTrampaClick(trampa) }
    }

    override fun getItemCount(): Int = listaTrampas.size
}
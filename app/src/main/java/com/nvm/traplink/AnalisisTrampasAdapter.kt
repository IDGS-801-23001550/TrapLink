package com.nvm.traplink

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nvm.traplink.data.FalsosPositivosResponseDto
import java.util.Locale

class AnalisisTrampasAdapter(
    private val listaAnalisis: List<FalsosPositivosResponseDto>,
    private val onNodoClick: (FalsosPositivosResponseDto) -> Unit
) : RecyclerView.Adapter<AnalisisTrampasAdapter.AnalisisViewHolder>() {

    class AnalisisViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIdDispositivo: TextView = view.findViewById(R.id.tvIdDispositivo)
        val tvPorcentajeFalsos: TextView = view.findViewById(R.id.tvPorcentajeFalsos)
        val tvTotalEventos: TextView = view.findViewById(R.id.tvTotalEventos)
        val tvCapturasReales: TextView = view.findViewById(R.id.tvCapturasReales)
        val tvFalsosContador: TextView = view.findViewById(R.id.tvFalsosContador)
        val tvPendientesContador: TextView = view.findViewById(R.id.tvPendientesContador)
        val segmentReales: View = view.findViewById(R.id.segmentReales)
        val segmentFalsos: View = view.findViewById(R.id.segmentFalsos)
        val segmentPendientes: View = view.findViewById(R.id.segmentPendientes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnalisisViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_analisis_trampa, parent, false)
        return AnalisisViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnalisisViewHolder, position: Int) {
        val item = listaAnalisis[position]
        val context = holder.itemView.context


        // Leemos directamente del DTO sin realizar cálculos aproximados en el cliente
        val reales = item.capturasReales
        val falsos = item.falsosPositivos
        val pendientes = item.eventosSinRevisar

        holder.tvIdDispositivo.text = "${item.nombreDispositivo}"
        holder.tvPorcentajeFalsos.text = String.format(Locale.getDefault(), "%.1f%% Falsos", item.pctFalsosPositivos)
        holder.tvTotalEventos.text = "Eventos: ${item.totalEventos}"
        holder.tvCapturasReales.text = "Reales: $reales"
        holder.tvFalsosContador.text = "Falsos: $falsos"
        holder.tvPendientesContador.text = "Pend: $pendientes"

        // ===== Chip de porcentaje con color según severidad =====
        when {
            item.pctFalsosPositivos < 15.0 -> {
                holder.tvPorcentajeFalsos.setBackgroundResource(R.drawable.bg_chip_active)
                holder.tvPorcentajeFalsos.setTextColor(ContextCompat.getColor(context, R.color.chip_text_active))
            }
            item.pctFalsosPositivos < 40.0 -> {
                holder.tvPorcentajeFalsos.setBackgroundResource(R.drawable.bg_chip_battery)
                holder.tvPorcentajeFalsos.setTextColor(ContextCompat.getColor(context, R.color.chip_text_battery))
            }
            else -> {
                holder.tvPorcentajeFalsos.setBackgroundResource(R.drawable.bg_chip_capture)
                holder.tvPorcentajeFalsos.setTextColor(ContextCompat.getColor(context, R.color.chip_text_capture))
            }
        }

        // ===== Barra segmentada: proporción visual de reales / falsos / pendientes =====
        val realesSeguro = reales.coerceAtLeast(0)
        val falsosSeguro = falsos.coerceAtLeast(0)
        val pendientesSeguro = pendientes.coerceAtLeast(0)
        val totalSegmentos = realesSeguro + falsosSeguro + pendientesSeguro

        if (totalSegmentos <= 0) {
            // Sin datos suficientes: barra neutra completa
            aplicarPeso(holder.segmentReales, 1f)
            aplicarPeso(holder.segmentFalsos, 0f)
            aplicarPeso(holder.segmentPendientes, 0f)
            holder.segmentReales.setBackgroundColor(ContextCompat.getColor(context, R.color.skeleton_base))
        } else {
            aplicarPeso(holder.segmentReales, realesSeguro.toFloat())
            aplicarPeso(holder.segmentFalsos, falsosSeguro.toFloat())
            aplicarPeso(holder.segmentPendientes, pendientesSeguro.toFloat())
            holder.segmentReales.setBackgroundColor(ContextCompat.getColor(context, R.color.status_active))
        }

        holder.itemView.setOnClickListener {
            onNodoClick(item)
        }
    }

    private fun aplicarPeso(view: View, weight: Float) {
        val params = view.layoutParams as LinearLayout.LayoutParams
        params.weight = weight
        view.layoutParams = params
    }

    override fun getItemCount(): Int = listaAnalisis.size
}
package com.nvm.traplink

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nvm.traplink.data.FalsosPositivosResponseDto
import java.util.Locale

class AnalisisTrampasAdapter(
    private val listaAnalisis: List<FalsosPositivosResponseDto>,
    private val globalesReales: Int,
    private val globalesPendientes: Int
) : RecyclerView.Adapter<AnalisisTrampasAdapter.AnalisisViewHolder>() {

    class AnalisisViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIdDispositivo: TextView = view.findViewById(R.id.tvIdDispositivo)
        val tvPorcentajeFalsos: TextView = view.findViewById(R.id.tvPorcentajeFalsos)
        val tvTotalEventos: TextView = view.findViewById(R.id.tvTotalEventos)
        val tvCapturasReales: TextView = view.findViewById(R.id.tvCapturasReales)
        val tvFalsosContador: TextView = view.findViewById(R.id.tvFalsosContador)
        val tvPendientesContador: TextView = view.findViewById(R.id.tvPendientesContador)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnalisisViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_analisis_trampa, parent, false)
        return AnalisisViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnalisisViewHolder, position: Int) {
        val item = listaAnalisis[position]

        // Solución temporal: Si solo hay un nodo (ID: 1), toma los globales directamente.
        // Si hay más, calcula de forma segura para no romper la consistencia.
        val reales = if (listaAnalisis.size == 1) globalesReales else (item.totalEventos - item.falsosPositivos) * globalesReales / (globalesReales + globalesPendientes)
        val pendientes = item.totalEventos - item.falsosPositivos - reales

        holder.tvIdDispositivo.text = "ID Dispositivo: ${item.dispositivoID}"
        holder.tvPorcentajeFalsos.text = String.format(Locale.getDefault(), "%.1f%% Falsos", item.pctFalsosPositivos)
        holder.tvTotalEventos.text = "Eventos: ${item.totalEventos}"
        holder.tvCapturasReales.text = "Reales: $reales"
        holder.tvFalsosContador.text = "Falsos: ${item.falsosPositivos}"
        holder.tvPendientesContador.text = "Pend: $pendientes"
    }

    override fun getItemCount(): Int = listaAnalisis.size
}
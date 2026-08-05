package com.nvm.traplink

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

/**
 * VideoView que, en vez de dejar franjas negras cuando el video y el contenedor
 * no comparten la misma proporción, escala el video para llenar todo el espacio
 * disponible y recorta el sobrante (igual que "centerCrop" en un ImageView).
 *
 * El contenedor padre (un FrameLayout) recorta a sus límites por defecto, así que
 * basta con que este view esté centrado dentro de él (android:layout_gravity="center")
 * para que el excedente quede oculto en vez de desbordarse.
 */
class CenterCropVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : VideoView(context, attrs) {

    private var videoWidth = 0
    private var videoHeight = 0

    /** Llamar cuando el MediaPlayer ya está preparado y se conocen las dimensiones reales del video. */
    fun setVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (videoWidth == width && videoHeight == height) return
        videoWidth = width
        videoHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (videoWidth <= 0 || videoHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val containerWidth = MeasureSpec.getSize(widthMeasureSpec)
        val containerHeight = MeasureSpec.getSize(heightMeasureSpec)

        val containerRatio = containerWidth.toFloat() / containerHeight
        val videoRatio = videoWidth.toFloat() / videoHeight

        var finalWidth = containerWidth
        var finalHeight = containerHeight

        if (videoRatio > containerRatio) {
            // El video es proporcionalmente "más ancho" que el contenedor:
            // se agranda el ancho y sobra a los lados (se recorta izquierda/derecha)
            finalWidth = (containerHeight * videoRatio).toInt()
        } else {
            // El video es proporcionalmente "más angosto/alto" que el contenedor:
            // se agranda el alto y sobra arriba/abajo (se recorta ahí)
            finalHeight = (containerWidth / videoRatio).toInt()
        }

        setMeasuredDimension(finalWidth, finalHeight)
    }
}
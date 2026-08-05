package com.nvm.traplink

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class TutorialActivity : AppCompatActivity() {

    private lateinit var videoTutorial: VideoView
    private lateinit var progressVideo: ProgressBar
    private lateinit var btnPlayOverlay: FrameLayout
    private lateinit var btnCerrarTutorial: FrameLayout

    // Guarda la posición de reproducción si el usuario rota la pantalla o la app pasa a segundo plano
    private var posicionActual: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_tutorial)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        videoTutorial = findViewById(R.id.videoTutorial)
        progressVideo = findViewById(R.id.progressVideo)
        btnPlayOverlay = findViewById(R.id.btnPlayOverlay)
        btnCerrarTutorial = findViewById(R.id.btnCerrarTutorial)

        configurarVideo()

        btnCerrarTutorial.setOnClickListener {
            finish()
        }

        // Toca el overlay para iniciar reproducción la primera vez
        btnPlayOverlay.setOnClickListener {
            btnPlayOverlay.visibility = View.GONE
            videoTutorial.start()
        }

        // Toca el video para pausar/reanudar
        videoTutorial.setOnClickListener {
            if (videoTutorial.isPlaying) {
                videoTutorial.pause()
                btnPlayOverlay.visibility = View.VISIBLE
            } else {
                videoTutorial.start()
                btnPlayOverlay.visibility = View.GONE
            }
        }
    }

    private fun configurarVideo() {
        try {
            // Usa el video que envió Inicio según el método seleccionado (QR o Número de serie).
            // Si esta pantalla se abre sin ese extra (por ejemplo desde otro punto de la app),
            // cae de vuelta al video de QR por defecto.
            val videoResId = intent.getIntExtra(Inicio.EXTRA_VIDEO_RES, R.raw.tutorial_vincular)
            val uriVideo = Uri.parse("android.resource://$packageName/$videoResId")

            videoTutorial.setVideoURI(uriVideo)

            // Controles nativos: play/pause, barra de progreso, adelantar/atrasar
            val mediaController = MediaController(this)
            mediaController.setAnchorView(videoTutorial)
            videoTutorial.setMediaController(mediaController)

            videoTutorial.setOnPreparedListener { mp ->
                progressVideo.visibility = View.GONE
                mp.isLooping = false
                if (posicionActual > 0) {
                    videoTutorial.seekTo(posicionActual)
                }
                // Arranca solo, sin esperar a que el usuario toque el overlay de play
                videoTutorial.start()
                btnPlayOverlay.visibility = View.GONE
            }

            videoTutorial.setOnCompletionListener {
                btnPlayOverlay.visibility = View.VISIBLE
            }

            videoTutorial.setOnErrorListener { _, _, _ ->
                progressVideo.visibility = View.GONE
                Toast.makeText(this, "No se pudo cargar el video tutorial", Toast.LENGTH_SHORT).show()
                true
            }

        } catch (e: Exception) {
            progressVideo.visibility = View.GONE
            Toast.makeText(this, "Error al preparar el video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        // Guarda la posición y pausa al salir de la pantalla
        if (::videoTutorial.isInitialized && videoTutorial.isPlaying) {
            posicionActual = videoTutorial.currentPosition
            videoTutorial.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::videoTutorial.isInitialized && posicionActual > 0) {
            videoTutorial.seekTo(posicionActual)
        }
    }
}
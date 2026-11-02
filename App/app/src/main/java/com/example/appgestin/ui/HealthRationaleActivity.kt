package com.example.appgestin.ui

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Shown from Health Connect when the user asks why these permissions are needed. */
class HealthRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            textSize = 18f
            setPadding(36, 64, 36, 36)
            text = "GSTI Contigo usa Health Connect solo cuando eliges sincronizar. " +
                "Lee pasos, duración del sueño y frecuencia cardiaca, los resume en bloques " +
                "y los envía al servidor del estudio con tu código seudónimo. " +
                "No lee rutas, coordenadas ni nombres de aplicaciones. " +
                "Puedes revocar estos permisos en Health Connect cuando quieras. " +
                "La app no ofrece diagnósticos; el equipo autorizado revisa los resultados."
        })
    }
}

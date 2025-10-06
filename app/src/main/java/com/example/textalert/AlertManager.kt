package com.example.textalert

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Vibrator
import android.os.VibrationEffect

class AlertManager(private val ctx: Context) {
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

    fun notifyHit(keyword: String) {
        // kurzer Piepton (ca. 200 ms)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 200)

        // kurze Vibration dazu (optional)
        val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vib.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

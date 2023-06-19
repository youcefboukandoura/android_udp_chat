package com.youcefboukandoura.androidudppaudiochat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat

fun Context.getAudioRecorder(): AudioRecord? = when {
    ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.RECORD_AUDIO,
    ) != PackageManager.PERMISSION_GRANTED
    -> {
        // TODO: Consider calling
        //    ActivityCompat#requestPermissions
        // here to request the missing permissions, and then overriding
        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
        //                                          int[] grantResults)
        // to handle the case where the user grants the permission. See the documentation
        // for ActivityCompat#requestPermissions for more details.
        null
    }

    else -> {
        val sampleRateInHz = 8000
        AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRateInHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            AudioRecord.getMinBufferSize(
                sampleRateInHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, /* !!! Hit visitElement for element type: class org.jetbrains.kotlin.nj2k.tree.JKErrorExpression !!! */
            ) * 10,
            /* !!! Hit visitElement for element type: class org.jetbrains.kotlin.nj2k.tree.JKErrorExpression !!! */
        )
    }
}

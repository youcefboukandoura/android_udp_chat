package com.youcefboukandoura.androidudppaudiochat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class MakeCallActivity : Activity() {
    private var displayName: String? = null
    private var contactName: String? = null
    private var contactIp: String? = null
    private var LISTEN = true
    private var IN_CALL = false
    private var call: AudioCall? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_make_call)
        Log.i(LOG_TAG, "MakeCallActivity started!")
        val intent = intent
        displayName = intent.getStringExtra(MainActivity.EXTRA_DISPLAYNAME)
        contactName = intent.getStringExtra(MainActivity.EXTRA_CONTACT)
        contactIp = intent.getStringExtra(MainActivity.EXTRA_IP)
        val textView = findViewById<View>(R.id.textViewCalling) as TextView
        textView.text = "Calling: $contactName"
        startListener()
        makeCall()
        val endButton = findViewById<View>(R.id.buttonEndCall) as Button
        endButton.setOnClickListener { // Button to end the call has been pressed
            endCall()
        }
    }

    private fun makeCall() {
        // Send a request to start a call
        sendMessage("CAL:$displayName", 50003)
    }

    private fun endCall() {
        // Ends the chat sessions
        stopListener()
        if (IN_CALL) {
            call!!.endCall()
        }
        sendMessage("END:", BROADCAST_PORT)
        finish()
    }

    private fun startListener() {
        // Create listener thread
        LISTEN = true

        val listenThread = Thread(
            Runnable {
                try {
                    Log.i(LOG_TAG, "Listener started!")
                    val socket = DatagramSocket(BROADCAST_PORT)
                    socket.soTimeout = 15000
                    val buffer = ByteArray(BUF_SIZE)
                    val packet = DatagramPacket(buffer, BUF_SIZE)
                    while (LISTEN) {
                        try {
                            Log.i(LOG_TAG, "Listening for packets")
                            socket.receive(packet)
                            val data = String(buffer, 0, packet.length)
                            Log.i(
                                LOG_TAG,
                                "Packet received from " + packet.address + " with contents: " + data,
                            )
                            val action = data.substring(0, 4)
                            if (action == "ACC:") {
                                // Accept notification received. Start call

                                val audioRecorder =
                                    baseContext.getAudioRecorder() ?: return@Runnable
                                call = AudioCall(packet.address, audioRecorder)
                                call!!.startCall()
                                IN_CALL = true
                            } else if (action == "REJ:") {
                                // Reject notification received. End call
                                endCall()
                            } else if (action == "END:") {
                                // End call notification received. End call
                                endCall()
                            } else {
                                // Invalid notification received
                                Log.w(
                                    LOG_TAG,
                                    packet.address.toString() + " sent invalid message: " + data,
                                )
                            }
                        } catch (e: SocketTimeoutException) {
                            if (!IN_CALL) {
                                Log.i(LOG_TAG, "No reply from contact. Ending call")
                                endCall()
                                return@Runnable
                            }
                        } catch (e: IOException) {
                        }
                    }
                    Log.i(LOG_TAG, "Listener ending")
                    socket.disconnect()
                    socket.close()
                    return@Runnable
                } catch (e: SocketException) {
                    Log.e(LOG_TAG, "SocketException in Listener")
                    endCall()
                }
            },
        )
        listenThread.start()
    }

    private fun stopListener() {
        // Ends the listener thread
        LISTEN = false
    }

    private fun sendMessage(message: String, port: Int) {
        // Creates a thread used for sending notifications
        val replyThread = Thread {
            try {
                val address = InetAddress.getByName(contactIp)
                val data = message.toByteArray()
                val socket = DatagramSocket()
                val packet = DatagramPacket(data, data.size, address, port)
                socket.send(packet)
                Log.i(LOG_TAG, "Sent message( $message ) to $contactIp")
                socket.disconnect()
                socket.close()
            } catch (e: UnknownHostException) {
                Log.e(LOG_TAG, "Failure. UnknownHostException in sendMessage: $contactIp")
            } catch (e: SocketException) {
                Log.e(LOG_TAG, "Failure. SocketException in sendMessage: $e")
            } catch (e: IOException) {
                Log.e(LOG_TAG, "Failure. IOException in sendMessage: $e")
            }
        }
        replyThread.start()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.make_call, menu)
        return true
    }

    companion object {
        private const val LOG_TAG = "MakeCall"
        private const val BROADCAST_PORT = 50002
        private const val BUF_SIZE = 1024
    }
}

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

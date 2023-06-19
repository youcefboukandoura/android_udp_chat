package com.youcefboukandoura.androidudppaudiochat

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException

class ReceiveCallActivity : Activity() {
    private var contactIp: String? = null
    private var contactName: String? = null
    private var listen = true
    private var inCall = false
    private var call: AudioCall? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receive_call)
        val intent = intent
        contactName = intent.getStringExtra(MainActivity.EXTRA_CONTACT)
        contactIp = intent.getStringExtra(MainActivity.EXTRA_IP)
        val acceptButton = findViewById<Button>(R.id.buttonAccept)
        val endButton = findViewById<Button>(R.id.buttonEndCall1)
        val rejectButton = findViewById<Button>(R.id.buttonReject)
        val textView = findViewById<TextView>(R.id.textViewIncomingCall)
        textView.text = "Incoming call: $contactName"

        endButton.visibility = View.INVISIBLE
        startListener()

        acceptButton.setOnClickListener {
            onClickAcceptButton(
                acceptButton,
                rejectButton,
                endButton,
            )
        }
        rejectButton.setOnClickListener { // Send a reject notification and end the call
            sendMessage("REJ:")
            endCall()
        }

        // END BUTTON
        endButton.setOnClickListener { endCall() }
    }

    private fun onClickAcceptButton(
        acceptButton: Button,
        rejectButton: Button,
        endButton: Button,
    ) {
        try {
            // Accepting call. Send a notification and start the call
            sendMessage("ACC:")
            val address = InetAddress.getByName(contactIp)
            Log.i(LOG_TAG, "Calling $address")
            inCall = true
            val audioRecorder = applicationContext.getAudioRecorder() ?: return
            call = AudioCall(address, audioRecorder)
            call?.startCall()
            // Hide the buttons as they're not longer required

            acceptButton.isEnabled = false
            rejectButton.isEnabled = false
            endButton.visibility = View.VISIBLE
        } catch (e: UnknownHostException) {
            Log.e(LOG_TAG, "UnknownHostException in acceptButton: $e")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Exception in acceptButton: $e")
        }
    }

    private fun endCall() {
        // End the call and send a notification
        stopListener()
        if (inCall) {
            call?.endCall()
        }
        sendMessage("END:")
        finish()
    }

    private fun startListener() {
        // Creates the listener thread
        listen = true
        val listenThread = Thread(
            Runnable {
                try {
                    Log.i(LOG_TAG, "Listener started!")
                    val socket = DatagramSocket(BROADCAST_PORT)
                    socket.soTimeout = 1500
                    val buffer = ByteArray(BUF_SIZE)
                    val packet = DatagramPacket(buffer, BUF_SIZE)
                    while (listen) {
                        try {
                            Log.i(LOG_TAG, "Listening for packets")
                            socket.receive(packet)
                            val data = String(buffer, 0, packet.length)
                            Log.i(
                                LOG_TAG,
                                "Packet received from " + packet.address + " with contents: " + data,
                            )
                            val action = data.substring(0, 4)
                            if (action == "END:") {
                                // End call notification received. End call
                                endCall()
                            } else {
                                // Invalid notification received.
                                Log.w(
                                    LOG_TAG,
                                    packet.address.toString() + " sent invalid message: " + data,
                                )
                            }
                        } catch (e: IOException) {
                            Log.e(LOG_TAG, "IOException in Listener $e")
                        }
                    }
                    Log.i(LOG_TAG, "Listener ending")
                    socket.disconnect()
                    socket.close()
                    return@Runnable
                } catch (e: SocketException) {
                    Log.e(LOG_TAG, "SocketException in Listener $e")
                    endCall()
                }
            },
        )
        listenThread.start()
    }

    private fun stopListener() {
        // Ends the listener thread
        listen = false
    }

    private fun sendMessage(message: String) {
        // Creates a thread for sending notifications
        val replyThread = Thread {
            try {
                val address = InetAddress.getByName(contactIp)
                val data = message.toByteArray()
                val socket = DatagramSocket()
                val packet = DatagramPacket(data, data.size, address, BROADCAST_PORT)
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
        menuInflater.inflate(R.menu.receive_call, menu)
        return true
    }

    companion object {
        private const val LOG_TAG = "ReceiveCall"
        private const val BROADCAST_PORT = 50002
        private const val BUF_SIZE = 1024
    }
}

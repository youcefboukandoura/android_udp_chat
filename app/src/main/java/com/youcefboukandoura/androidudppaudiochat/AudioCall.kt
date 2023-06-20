package com.youcefboukandoura.androidudppaudiochat

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.util.Log
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException

class AudioCall(
    private val address: InetAddress,
    private val audioRecorder: AudioRecord,
) {

    private var mic = false // Enable mic?
    private var speakers = false // Enable speakers?
    fun startCall() {
        startMic()
        startSpeakers()
    }

    fun endCall() {
        Log.i(LOG_TAG, "Ending call!")
        muteMic()
        muteSpeakers()
    }

    private fun muteMic() {
        mic = false
    }

    private fun muteSpeakers() {
        speakers = false
    }

    private fun startMic() {
        // Creates the thread for capturing and transmitting audio
        mic = true
        val thread = Thread(
            Runnable {
                // Create an instance of the AudioRecord class
                Log.i(LOG_TAG, "Send thread started. Thread id: " + Thread.currentThread().id)
                var bytesRead = 0
                var bytesSent = 0
                val buf = ByteArray(BUF_SIZE)
                try {
                    // Create a socket and start recording
                    Log.i(LOG_TAG, "Packet destination: $address")
                    val socket = DatagramSocket()
                    audioRecorder.startRecording()
                    while (mic) {
                        // Capture audio from the mic and transmit it
                        bytesRead = audioRecorder.read(buf, 0, BUF_SIZE)
                        val packet = DatagramPacket(buf, bytesRead, address, ADDRESS_PORT)
                        socket.send(packet)
                        bytesSent += bytesRead
                        Log.i(LOG_TAG, "Total bytes sent $address: $bytesSent")
                        Thread.sleep(SAMPLE_INTERVAL.toLong(), 0)
                    }
                    // Stop recording and release resources
                    audioRecorder.stop()
                    audioRecorder.release()
                    socket.disconnect()
                    socket.close()
                    mic = false
                    return@Runnable
                } catch (e: InterruptedException) {
                    Log.e(LOG_TAG, "InterruptedException: $e")
                    mic = false
                } catch (e: SocketException) {
                    Log.e(LOG_TAG, "SocketException: $e")
                    mic = false
                } catch (e: UnknownHostException) {
                    Log.e(LOG_TAG, "UnknownHostException: $e")
                    mic = false
                } catch (e: IOException) {
                    Log.e(LOG_TAG, "IOException: $e")
                    mic = false
                }
            },
        )
        thread.start()
    }

    private fun startSpeakers() {
        // Creates the thread for receiving and playing back audio
        if (!speakers) {
            speakers = true
            val receiveThread = Thread(
                Runnable {
                    // Create an instance of AudioTrack, used for playing back audio
                    Log.i(
                        LOG_TAG,
                        "Receive thread started. Thread id: " + Thread.currentThread().id,
                    )
                    val track = AudioTrack(
                        /* streamType = */ AudioManager.STREAM_VOICE_CALL,
                        /* sampleRateInHz = */ SAMPLE_RATE,
                        /* channelConfig = */ AudioFormat.CHANNEL_OUT_MONO,
                        /* audioFormat = */ AudioFormat.ENCODING_PCM_16BIT,
                        /* bufferSizeInBytes = */ BUF_SIZE,
                        /* mode = */ AudioTrack.MODE_STREAM,
                    )

                    track.play()
                    try {
                        // Define a socket to receive the audio
                        val socket = DatagramSocket(ADDRESS_PORT)
                        val buf = ByteArray(BUF_SIZE)
                        while (speakers) {
                            // Play back the audio received from packets
                            val packet = DatagramPacket(buf, BUF_SIZE)
                            socket.receive(packet)
                            Log.i(LOG_TAG, "Packet received: " + packet.length)
                            track.write(packet.data, 0, BUF_SIZE)
                        }
                        // Stop playing back and release resources
                        socket.disconnect()
                        socket.close()
                        track.stop()
                        track.flush()
                        track.release()
                        speakers = false
                        return@Runnable
                    } catch (e: SocketException) {
                        Log.e(LOG_TAG, "SocketException: $e")
                        speakers = false
                    } catch (e: IOException) {
                        Log.e(LOG_TAG, "IOException: $e")
                        speakers = false
                    }
                },
            )
            receiveThread.start()
        }
    }

    companion object {
        private const val LOG_TAG = "AudioCall"
        private const val SAMPLE_RATE = 8000 // Hertz
        private const val SAMPLE_INTERVAL = 20 // Milliseconds
        private const val SAMPLE_SIZE = 2 // Bytes
        private const val BUF_SIZE = SAMPLE_INTERVAL * SAMPLE_INTERVAL * SAMPLE_SIZE * 2 // Bytes
        const val ADDRESS_PORT = 33242 // Port the packets are addressed to
    }
}

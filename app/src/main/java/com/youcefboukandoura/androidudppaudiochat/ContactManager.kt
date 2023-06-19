package com.youcefboukandoura.androidudppaudiochat

import android.util.Log
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.SocketTimeoutException

class ContactManager(name: String, private val broadcastIP: InetAddress) {
    private var BROADCAST = true
    private var LISTEN = true
    val contacts: HashMap<String, InetAddress>

    init {
        contacts = HashMap()
        listen()
        broadcastName(name, broadcastIP)
    }

    fun addContact(name: String, address: InetAddress) {
        // If the contact is not already known to us, add it
        if (!contacts.containsKey(name)) {
            Log.i(LOG_TAG, "Adding contact: $name")
            contacts[name] = address
            Log.i(LOG_TAG, "#Contacts: " + contacts.size)
            return
        }
        Log.i(LOG_TAG, "Contact already exists: $name")
        return
    }

    fun removeContact(name: String) {
        // If the contact is known to us, remove it
        if (contacts.containsKey(name)) {
            Log.i(LOG_TAG, "Removing contact: $name")
            contacts.remove(name)
            Log.i(LOG_TAG, "#Contacts: " + contacts.size)
            return
        }
        Log.i(LOG_TAG, "Cannot remove contact. $name does not exist.")
        return
    }

    fun bye(name: String) {
        // Sends a Bye notification to other devices
        val byeThread = Thread(
            Runnable {
                try {
                    Log.i(LOG_TAG, "Attempting to broadcast BYE notification!")
                    val notification = "BYE:$name"
                    val message = notification.toByteArray()
                    val socket = DatagramSocket()
                    socket.broadcast = true
                    val packet = DatagramPacket(message, message.size, broadcastIP, BROADCAST_PORT)
                    socket.send(packet)
                    Log.i(LOG_TAG, "Broadcast BYE notification!")
                    socket.disconnect()
                    socket.close()
                    return@Runnable
                } catch (e: SocketException) {
                    Log.e(LOG_TAG, "SocketException during BYE notification: $e")
                } catch (e: IOException) {
                    Log.e(LOG_TAG, "IOException during BYE notification: $e")
                }
            },
        )
        byeThread.start()
    }

    fun broadcastName(name: String, broadcastIP: InetAddress?) {
        // Broadcasts the name of the device at a regular interval
        Log.i(LOG_TAG, "Broadcasting started!")
        val broadcastThread = Thread(
            Runnable {
                try {
                    val request = "ADD:$name"
                    val message = request.toByteArray()
                    val socket = DatagramSocket()
                    socket.broadcast = true
                    val packet = DatagramPacket(message, message.size, broadcastIP, BROADCAST_PORT)
                    while (BROADCAST) {
                        socket.send(packet)
                        Log.i(LOG_TAG, "Broadcast packet sent: " + packet.address.toString())
                        Thread.sleep(BROADCAST_INTERVAL.toLong())
                    }
                    Log.i(LOG_TAG, "Broadcaster ending!")
                    socket.disconnect()
                    socket.close()
                    return@Runnable
                } catch (e: SocketException) {
                    Log.e(LOG_TAG, "SocketExceltion in broadcast: $e")
                    Log.i(LOG_TAG, "Broadcaster ending!")
                    return@Runnable
                } catch (e: IOException) {
                    Log.e(LOG_TAG, "IOException in broadcast: $e")
                    Log.i(LOG_TAG, "Broadcaster ending!")
                    return@Runnable
                } catch (e: InterruptedException) {
                    Log.e(LOG_TAG, "InterruptedException in broadcast: $e")
                    Log.i(LOG_TAG, "Broadcaster ending!")
                    return@Runnable
                }
            },
        )
        broadcastThread.start()
    }

    fun stopBroadcasting() {
        // Ends the broadcasting thread
        BROADCAST = false
    }

    fun listen() {
        // Create the listener thread
        Log.i(LOG_TAG, "Listening started!")
        val listenThread = Thread(object : Runnable {
            override fun run() {
                val socket: DatagramSocket
                socket = try {
                    DatagramSocket(BROADCAST_PORT)
                } catch (e: SocketException) {
                    Log.e(LOG_TAG, "SocketExcepion in listener: $e")
                    return
                }
                val buffer = ByteArray(BROADCAST_BUF_SIZE)
                while (LISTEN) {
                    listen(socket, buffer)
                }
                Log.i(LOG_TAG, "Listener ending!")
                socket.disconnect()
                socket.close()
                return
            }

            fun listen(socket: DatagramSocket, buffer: ByteArray?) {
                try {
                    // Listen in for new notifications
                    Log.i(LOG_TAG, "Listening for a packet!")
                    val packet = DatagramPacket(buffer, BROADCAST_BUF_SIZE)
                    socket.soTimeout = 15000
                    socket.receive(packet)
                    val data = String(buffer!!, 0, packet.length)
                    Log.i(LOG_TAG, "Packet received: $data")
                    val action = data.substring(0, 4)
                    if (action == "ADD:") {
                        // Add notification received. Attempt to add contact
                        Log.i(LOG_TAG, "Listener received ADD request")
                        addContact(data.substring(4, data.length), packet.address)
                    } else if (action == "BYE:") {
                        // Bye notification received. Attempt to remove contact
                        Log.i(LOG_TAG, "Listener received BYE request")
                        removeContact(data.substring(4, data.length))
                    } else {
                        // Invalid notification received
                        Log.w(LOG_TAG, "Listener received invalid request: $action")
                    }
                } catch (e: SocketTimeoutException) {
                    Log.i(LOG_TAG, "No packet received!")
                    if (LISTEN) {
                        listen(socket, buffer)
                    }
                    return
                } catch (e: SocketException) {
                    Log.e(LOG_TAG, "SocketException in listen: $e")
                    Log.i(LOG_TAG, "Listener ending!")
                    return
                } catch (e: IOException) {
                    Log.e(LOG_TAG, "IOException in listen: $e")
                    Log.i(LOG_TAG, "Listener ending!")
                    return
                }
            }
        })
        listenThread.start()
    }

    fun stopListening() {
        // Stops the listener thread
        LISTEN = false
    }

    companion object {
        private const val LOG_TAG = "ContactManager"
        const val BROADCAST_PORT = 50001 // Socket on which packets are sent/received
        private const val BROADCAST_INTERVAL = 10000 // Milliseconds
        private const val BROADCAST_BUF_SIZE = 1024
    }
}

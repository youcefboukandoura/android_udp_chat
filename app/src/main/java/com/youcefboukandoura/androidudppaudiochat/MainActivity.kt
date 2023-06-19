package com.youcefboukandoura.androidudppaudiochat

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException

class MainActivity : Activity() {
    private var contactManager: ContactManager? = null
    private var displayName: String? = null
    private var STARTED = false
    private var IN_CALL = false
    private var LISTEN = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.i(LOG_TAG, "UDPChat started")

        // START BUTTON
        // Pressing this buttons initiates the main functionality
        val btnStart = findViewById<Button>(R.id.buttonStart)
        btnStart.setOnClickListener { onStartButtonClick(btnStart) }

        // UPDATE BUTTON
        // Updates the list of reachable devices
        val btnUpdate = findViewById<Button>(R.id.buttonUpdate)
        btnUpdate.setOnClickListener { updateContactList() }

        // CALL BUTTON
        // Attempts to initiate an audio chat session with the selected device
        val buttonCall = findViewById<Button>(R.id.buttonCall)
        buttonCall.setOnClickListener { onClickButtonCall() }
    }

    private fun onClickButtonCall() {
        val radioGroup = findViewById<RadioGroup>(R.id.contactList)
        val selectedButton = radioGroup.checkedRadioButtonId
        if (selectedButton == -1) {
            // If no device was selected, present an error message to the user
            Log.w(LOG_TAG, "Warning: no contact selected")
            val alert = AlertDialog.Builder(this@MainActivity).create()
            alert.setTitle("Oops")
            alert.setMessage("You must select a contact first")
            alert.setButton(-1, "OK") { _, _ -> alert.dismiss() }
            alert.show()
            return
        }
        // Collect details about the selected contact
        val radioButton = findViewById<RadioButton>(selectedButton)
        val contact = radioButton.text.toString()
        val ip = contactManager!!.contacts[contact]
        IN_CALL = true

        // Send this information to the MakeCallActivity and start that activity
        val intent = Intent(this@MainActivity, MakeCallActivity::class.java)
        intent.putExtra(EXTRA_CONTACT, contact)
        var address = ip.toString()
        address = address.substring(1)
        intent.putExtra(EXTRA_IP, address)
        intent.putExtra(EXTRA_DISPLAYNAME, displayName)
        startActivity(intent)
    }

    private fun onStartButtonClick(btnStart: Button) {
        Log.i(LOG_TAG, "Start button pressed")
        STARTED = true
        val displayNameText = findViewById<EditText>(R.id.editTextDisplayName)
        displayName = displayNameText.text.toString()
        displayNameText.isEnabled = false
        btnStart.isEnabled = false
        val text = findViewById<TextView>(R.id.textViewSelectContact)
        text.visibility = View.VISIBLE
        val updateButton = findViewById<Button>(R.id.buttonUpdate)
        updateButton.visibility = View.VISIBLE
        val callButton = findViewById<Button>(R.id.buttonCall)
        callButton.visibility = View.VISIBLE
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        scrollView.visibility = View.VISIBLE
        contactManager = ContactManager(displayNameText.text.toString(), broadcastIp!!)
        startCallListener()
    }

    private fun updateContactList() {
        // Create a copy of the HashMap used by the ContactManager
        val contacts = contactManager!!.contacts
        // Create a radio button for each contact in the HashMap
        val radioGroup = findViewById<RadioGroup>(R.id.contactList)
        radioGroup.removeAllViews()
        for (name in contacts.keys) {
            val radioButton = RadioButton(baseContext)
            radioButton.text = name
            radioButton.setTextColor(Color.BLACK)
            radioGroup.addView(radioButton)
        }
        radioGroup.clearCheck()
    }

    private val broadcastIp: InetAddress?
        get() = // Function to return the broadcast address, based on the IP address of the device
            try {
                val wifiManager =
                    getSystemService(WIFI_SERVICE) as WifiManager
                val wifiInfo = wifiManager.connectionInfo
                val ipAddress = wifiInfo.ipAddress
                val addressString = toBroadcastIp(ipAddress)
                InetAddress.getByName(addressString)
            } catch (e: UnknownHostException) {
                Log.e(
                    LOG_TAG,
                    "UnknownHostException in getBroadcastIP: $e",
                )
                null
            }

    private fun toBroadcastIp(ip: Int): String {
        // Returns converts an IP address in int format to a formatted string
        return (ip and 0xFF).toString() + "." +
            (ip shr 8 and 0xFF) + "." +
            (ip shr 16 and 0xFF) + "." +
            "255"
    }

    private fun startCallListener() {
        // Creates the listener thread
        LISTEN = true
        val listener = Thread {
            try {
                // Set up the socket and packet to receive
                Log.i(LOG_TAG, "Incoming call listener started")
                val socket = DatagramSocket(LISTENER_PORT)
                socket.soTimeout = 1000
                val buffer = ByteArray(BUF_SIZE)
                val packet = DatagramPacket(buffer, BUF_SIZE)
                while (LISTEN) {
                    // Listen for incoming call requests
                    try {
                        Log.i(LOG_TAG, "Listening for incoming calls")
                        socket.receive(packet)
                        val data = String(buffer, 0, packet.length)
                        Log.i(
                            LOG_TAG,
                            "Packet received from " + packet.address + " with contents: " + data,
                        )
                        val action = data.substring(0, 4)
                        if (action == "CAL:") {
                            // Received a call request. Start the ReceiveCallActivity
                            val address = packet.address.toString()
                            val name = data.substring(4, packet.length)
                            val intent = Intent(this@MainActivity, ReceiveCallActivity::class.java)
                            intent.putExtra(EXTRA_CONTACT, name)
                            intent.putExtra(EXTRA_IP, address.substring(1))
                            IN_CALL = true
                            // LISTEN = false;
                            // stopCallListener();
                            startActivity(intent)
                        } else {
                            // Received an invalid request
                            Log.w(
                                LOG_TAG,
                                packet.address.toString() + " sent invalid message: " + data,
                            )
                        }
                    } catch (_: Exception) {
                    }
                }
                Log.i(LOG_TAG, "Call Listener ending")
                socket.disconnect()
                socket.close()
            } catch (e: SocketException) {
                Log.e(LOG_TAG, "SocketException in listener $e")
            }
        }
        listener.start()
    }

    private fun stopCallListener() {
        // Ends the listener thread
        LISTEN = false
    }

    public override fun onPause() {
        super.onPause()
        if (STARTED) {
            contactManager!!.bye(displayName!!)
            contactManager!!.stopBroadcasting()
            contactManager!!.stopListening()
            // STARTED = false;
        }
        stopCallListener()
        Log.i(LOG_TAG, "App paused!")
    }

    public override fun onStop() {
        super.onStop()
        Log.i(LOG_TAG, "App stopped!")
        stopCallListener()
        if (!IN_CALL) {
            finish()
        }
    }

    public override fun onRestart() {
        super.onRestart()
        Log.i(LOG_TAG, "App restarted!")
        IN_CALL = false
        STARTED = true
        contactManager = ContactManager(displayName!!, broadcastIp!!)
        startCallListener()
    }

    companion object {
        const val LOG_TAG = "UDPchat"
        private const val LISTENER_PORT = 50003
        private const val BUF_SIZE = 1024
        const val EXTRA_CONTACT = "CONTACT"
        const val EXTRA_IP = "IP"
        const val EXTRA_DISPLAYNAME = "DISPLAYNAME"
    }
}

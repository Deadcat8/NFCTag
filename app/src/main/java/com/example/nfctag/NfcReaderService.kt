package com.example.nfctag

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.compose.ui.graphics.colorspace.connect
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat.START_STICKY
import androidx.core.app.ServiceCompat.startForeground
import androidx.core.content.ContextCompat.getSystemService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.IOException
import java.security.Provider
import java.util.Arrays
import kotlin.text.equals

// NfcReaderService (The Reader):
// •The NfcReaderService runs on the device that is reading an NFC tag or communicating with
// an HCE-enabled device.
// •Its responsibility is to discover tags, connect to them using relevant technologies
// (like IsoDep), and send APDUs to the card and receive responses.
// •When your NfcReaderService wants to communicate with your HCE service, it constructs
// and sends the SELECT APDU containing the HCE service's AID using the transceive() method
// of the IsoDep object.

class NfcReaderService : Service() {
    // Define NFC-related variables
    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var pendingIntent: PendingIntent
    private lateinit var intentFilter: Array<IntentFilter>
    private lateinit var techList: Array<Array<String>>

    // HCE Specific variables
    private val HCE_AID = "F0010203040506"
    private val STATUS_WORD_SUCCESS = hexStringToByteArray("9000")

    // Binder to communicate with the Activity
    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        // Return this instance of NfcReaderService so clients can call public methods
        fun getService(): NfcReaderService = this@NfcReaderService
    }
    override fun onBind(intent: Intent?): IBinder {
        Log.d("NfcReaderService", "onBind called.")
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize NFC adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        // Check if NFC is available on the device
        val intent = Intent(this, HostActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        // Create a PendingIntent to be used with NFC intent filters
        pendingIntent = PendingIntent.getService(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        // Create an intent filter for the ACTION_TAG_DISCOVERED intent
        intentFilter = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
        techList = arrayOf(arrayOf(IsoDep::class.java.name))
        Log.d("NfcReaderService", "Service created with nfcAdapter: $nfcAdapter and pendingIntent: $pendingIntent")
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        //startForeground(1, createNotification())

        Log.d("NfcReaderService", "Service started")

        if (intent?.action == NfcAdapter.ACTION_TAG_DISCOVERED) {
            handleNfcIntent(intent)
        }

        return START_STICKY
    }

    private fun createNotification(): Notification {
        // Create a notification channel and set it to the foreground service
        val channelId = "nfc_reader_channel"
        // Create a notification builder
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "NFC Reader", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        // Build the notification
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NFC Reader Active")
            .setContentText("Waiting for nearby devices...")
            .build()
    }

    // Handle NFC Communication, Intents are passed to handleNfcIntent, Tags are passed to processDiscoveredTag
    private fun handleNfcIntent(intent: Intent) {
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        if (tag == null) {
            Log.w("NfcReaderService", "ACTION_TAG_DISCOVERED intent did not contain a Tag extra.")
            return
        }
        Log.d("NfcReaderService", "Tag discovered with ID: ${tag.id?: "N/A"}")
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            Log.d("NfcReaderService", "Tag supports NDEF technology. Attempting to read NDEF message.")
            val ndefMessage = readNdefTagSimple(ndef) // Call the simple NDEF read function
            if (ndefMessage != null) {
                triggerNdefMessageReadEvent(ndefMessage)
            } else {
                Log.d("NfcReaderService", "NDEF technology supported, but no NDEF message was read.")
            }
            return
        }
        val isoDep = IsoDep.get(tag)
        if (isoDep != null) {
            Log.d("NfcReaderService", "Tag supports ISO-DEP technology. Attempting to read peer device ID.")
            try {
                isoDep.connect()
                //Select by AID (F0010203040506)
                val response = isoDep.transceive(byteArrayOf(
                    0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(),
                    0x07.toByte(), 0xF0.toByte(), 0x01.toByte(), 0x02.toByte(),
                    0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte()
                ))
                val deviceId = String(response, Charsets.UTF_8)
                Log.d("NFC_SERVICE", "Sent Device ID: $deviceId")
                savePeerDeviceId(deviceId)
            } catch (e: Exception) {
                Log.e("NfcReaderService", "Error reading peer device ID: ${e.message}")
                e.printStackTrace()
            } finally {
                isoDep.close()
            }
            return
        }
        Log.d("NfcReaderService", "Discovered tag supports neither NDEF nor IsoDep.")
        return
    }
    fun processDiscoveredTag(tag: Tag?) {
        if (tag == null) {
            Log.w("NfcReaderService", "processDiscoveredTag received a null tag.")
            return
        }
        Log.d("NfcReaderService", "Processing discovered tag with ID: ${tag.id?.toHexString() ?: "N/A"}")

        // --- Check for NDEF technology first ---
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            Log.d("NfcReaderService", "Tag supports NDEF technology. Attempting to read NDEF message.")
            val ndefMessage = readNdefTagSimple(ndef) // Call the simple NDEF read function

            if (ndefMessage != null) {
                // Handle the read NDEF message, e.g., broadcast it
                triggerNdefMessageReadEvent(ndefMessage)
            } else {
                // Handle case where NDEF is supported but no message was read
                Log.d("NfcReaderService", "NDEF technology supported, but no NDEF message was read.")
            }
            return // Exit if handled as an NDEF tag
        }

        // --- If not NDEF, check for IsoDep (your existing logic adapted) ---
        val isoDep = IsoDep.get(tag)
        if (isoDep != null) {
            Log.d("NfcReaderService", "Tag supports IsoDep technology. Proceeding with IsoDep processing.")
            processIsoDepTag(tag, isoDep) // Delegate to a dedicated IsoDep processing method
            return // Exit after IsoDep processing
        }

        // --- If neither NDEF nor IsoDep ---
        Log.d("NfcReaderService", "Discovered tag supports neither NDEF nor IsoDep.")
    }

    private fun savePeerDeviceId(id: String) {
        val prefs = getSharedPreferences("known_devices", Context.MODE_PRIVATE)
        if (!prefs.contains(id)) {
            prefs.edit().putBoolean(id, true).apply()
            Log.d("NFC", "New device detected and stored: $id")
        } else {
            Log.d("NFC", "Known device reconnected: $id")
            //Caught!
        }
    }

    // Simple NDEF read function
    private fun readNdefTagSimple(ndef: Ndef): NdefMessage? {
        Log.d("NfcReaderService", "Attempting to read NDEF tag (simple).")
        var ndefMessage: NdefMessage? = null
        try {
            ndef.connect()
            // Use getNdefMessage() after connecting to get the latest message
            ndefMessage = ndef.getNdefMessage()

            if (ndefMessage != null) {
                Log.d("NfcReaderService", "NDEF message read successfully (simple).")
            } else {
                Log.d("NfcReaderService", "No NDEF message found on tag (simple).")
            }
        } catch (e: IOException) {
            Log.e("NfcReaderService", "Error reading NDEF tag (simple): ${e.message}")
        } catch (e: Exception) {
            Log.e("NfcReaderService", "Unexpected error reading NDEF tag (simple): ${e.message}")
        } finally {
            try {
                if (ndef.isConnected) {
                    ndef.close()
                    Log.d("NfcReaderService", "Closed NDEF connection (simple).")
                }
            } catch (e: IOException) {
                Log.e("NfcReaderService", "Error closing NDEF connection (simple): ${e.message}")
            }
        }
        return ndefMessage
    }
    private fun triggerNdefMessageReadEvent(ndefMessage: NdefMessage) {
        val intent = Intent("com.yourpackage.app.NDEF_MESSAGE_READ")
        intent.putExtra("ndefMessage", ndefMessage) // NdefMessage is Parcelable
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // IsoDep processing function
    private fun processIsoDepTag(detectedTag: Tag, isoDep: IsoDep) {
        try {
            isoDep.connect()
            Log.d("NfcReaderService", "IsoDep connection established.")

            // 1. Send the SELECT AID APDU to select the HCE application
            val selectAidApdu = buildSelectAidApdu(HCE_AID) // Assuming buildSelectAidApdu exists
            Log.d("NfcReaderService", "Sending SELECT AID APDU: ${selectAidApdu.toHexString()}")

            val selectResponse = isoDep.transceive(selectAidApdu)
            Log.d("NfcReaderService", "Received SELECT AID response: ${selectResponse.toHexString()}")

            // Check if the SELECT AID was successful (status word 9000)
            if (selectResponse.size >= 2 && Arrays.equals(selectResponse.copyOfRange(selectResponse.size - 2, selectResponse.size), STATUS_WORD_SUCCESS)) {
                Log.d("NfcReaderService", "SELECT AID successful. Proceeding with further commands.")

                // 2. If SELECT AID was successful, send your custom command to get the device ID
                // Assuming you have a defined APDU for getting the device ID, e.g., 00C00000
                val getDeviceIdApdu = hexStringToByteArray("00C00000") // Assuming hexStringToByteArray exists
                Log.d("NfcReaderService", "Sending GET DEVICE ID APDU: ${getDeviceIdApdu.toHexString()}")

                val deviceIdResponse = isoDep.transceive(getDeviceIdApdu)
                Log.d("NfcReaderService", "Received GET DEVICE ID response: ${deviceIdResponse.toHexString()}")

                // 3. Process the response for the device ID
                // Assuming the response structure is the device ID bytes followed by the status word (9000)
                if (deviceIdResponse.size >= 2 && Arrays.equals(deviceIdResponse.copyOfRange(deviceIdResponse.size - 2, deviceIdResponse.size), STATUS_WORD_SUCCESS)) {
                    val deviceIdBytes = deviceIdResponse.copyOfRange(0, deviceIdResponse.size - 2)
                    val deviceId = String(deviceIdBytes, Charsets.UTF_8)
                    Log.d("NfcReaderService", "Successfully read peer device ID: $deviceId")

                    // Save the device ID and trigger the event to notify the Activity
                    savePeerDeviceId(deviceId)
                    triggerHceDeviceIdEvent(deviceId)
                } else {
                    Log.e("NfcReaderService", "GET DEVICE ID command failed or returned invalid response.")
                }

            } else {
                Log.e("NfcReaderService", "SELECT AID command failed or returned invalid response.")
            }

        } catch (e: IOException) {
            Log.e("NfcReaderService", "IOException during IsoDep processing: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e("NfcReaderService", "Unexpected error during IsoDep processing: ${e.message}")
            e.printStackTrace()
        } finally {
            try {
                if (isoDep.isConnected) {
                    isoDep.close()
                    Log.d("NfcReaderService", "IsoDep connection closed.")
                }
            } catch (e: IOException) {
                Log.e("NfcReaderService", "Error closing IsoDep connection: ${e.message}")
            }
        }
    }
    private fun triggerHceDeviceIdEvent(deviceId: String) {
        // Create a new Intent with a specific action
        val intent = Intent("com.example.nfctag.HCE_DEVICE_ID_IDENTIFIED")
        // Put the device ID as an extra in the Intent
        intent.putExtra("deviceId", deviceId)
        // Send the broadcast using LocalBroadcastManager
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("NfcReaderService", "Broadcasted HCE_DEVICE_ID_IDENTIFIED with deviceId: $deviceId")
    }
    private fun buildSelectAidApdu(aid: String): ByteArray {
        // Convert the AID hex string to bytes
        val aidBytes = hexStringToByteArray(aid) // Assuming hexStringToByteArray exists
        // Lc (Length of command data) is the length of the AID in bytes
        val lc = aidBytes.size.toByte()

        // Construct the APDU byte array
        return byteArrayOf(
            0x00,          // CLA (Class byte)
            0xA4.toByte(), // INS (Instruction byte) - SELECT command (A4)
            0x04,          // P1 (Parameter 1) - Select by DF (Directory File) name (04)
            0x00,          // P2 (Parameter 2) - First or only occurrence, return FCI (00)
            lc,            // Lc (Length of command data) - Length of the AID
            *aidBytes      // Data - The AID bytes themselves (using the spread operator *)
        )
    }

    // Various Helper Functions
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
        }
        return data
    }
}

package com.example.nfctag

import DeviceIdManager.getOrCreateDeviceId
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.Arrays
import kotlin.text.contentEquals

// MyHostApduService (The Card):
// •The MyHostApduService runs on the device that is emulating a smart card.
// •Its primary responsibility is to process Application Protocol Data Units (APDUs) sent by a
// reader device.
// •When an NFC reader device (like your phone running NfcReaderService) selects a specific
// application on a smart card, it sends a SELECT APDU containing the Application Identifier (AID)
// of the target application.
// •The Android system, acting as the interface between the NFC hardware and your HCE service,
// intercepts the incoming APDUs and delivers them to the appropriate HostApduService if the AID
// matches one declared in its manifest.
// •The processCommandApdu(commandApdu: ByteArray, extras: Bundle?) method in your HostApduService
// is the entry point for receiving these APDUs.

class MyHostApduService : HostApduService() {
    // Define the Application Identifier (AID) for the service
    private val SERVICE_AID = "F0010203040506"
    // Define the success status word (SW1 SW2) for APDU responses
    private val STATUS_WORD_SUCCESS = hexStringToByteArray("9000")
    // Define an error status word for unknown commands (e.g., Instruction not supported)
    private val STATUS_WORD_INS_NOT_SUPPORTED = hexStringToByteArray("6D00")

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d("MyHostApduService", "Received APDU: ${commandApdu.toHexString()}")

        // Must run SELECT AID first to bind Host and Reader
        // Construct the expected SELECT AID APDU for your service's AID
        val selectAidApdu = buildSelectAidApdu(SERVICE_AID)
        if (commandApdu.contentEquals(selectAidApdu)) {
            Log.i("MyHostApduService", "SELECT AID APDU received. AID: $SERVICE_AID")
            // If the received APDU matches the SELECT AID APDU,
            // you should return the success status word (9000).
            // This indicates to the reader that your application has been selected.
            // After this, the reader will typically send subsequent commands.

            // Optional: Trigger a local event to notify the UI on the HCE device that the AID was selected.
            // This is the "connection established" indication from the card's perspective.
            notifyAidSelected()

            return STATUS_WORD_SUCCESS
        }

        // --- 2. Handle other commands AFTER AID selection ---
        // If the command is not the SELECT AID APDU, it should be a subsequent command
        // sent by the reader to interact with your application.
        // You would add checks for your custom APDU commands here.

        val getDeviceIdApdu = hexStringToByteArray("00C00000") // Example APDU for getting device ID
        if (commandApdu.contentEquals(getDeviceIdApdu)) {
            Log.i("MyHostApduService", "GET DEVICE ID APDU received.")
            val deviceId = getOrCreateDeviceId(this) // Replace with your actual device ID logic
            val deviceIdBytes = deviceId.toByteArray(Charsets.UTF_8)
            // Concatenate the device ID bytes with the success status word
            val response = deviceIdBytes + STATUS_WORD_SUCCESS
            Log.d("MyHostApduService", "Sending Device ID response: ${response.toHexString()}")
            return response
        }

        // --- 3. Handle unknown or unsupported commands ---
        // If the command is not the SELECT AID APDU and doesn't match any
        // of your expected subsequent commands, return an error status word.
        Log.w("MyHostApduService", "Unknown or unsupported APDU received.")
        return STATUS_WORD_INS_NOT_SUPPORTED // Return "Instruction not supported" status word
    }

    override fun onDeactivated(reason: Int) {
        Log.d("MyHostApduService", "NFC link deactivated. Reason: $reason")
    }

    private fun buildSelectAidApdu(aid: String): ByteArray {
        // APDU format: [CLA] [INS] [P1] [P2] [Lc] [Data] [Le] (Lc and Le are optional)
        // http://www.cardlogix.com/glossary/apdu-application-protocol-data-unit-smart-card/
        // SELECT by AID is typically: 00 A4 04 00 [Lc] [AID]
        val aidBytes = hexStringToByteArray(aid)
        val lc = aidBytes.size.toByte() // Length of the AID
        return byteArrayOf(
            0x00, // CLA (Class byte)
            0xA4.toByte(), // INS (Instruction byte) - SELECT command
            0x04, // P1 (Parameter 1) - Select by AID
            0x00, // P2 (Parameter 2) - First or only occurrence, return FCI
            lc,   // Lc (Length of command data) - Length of the AID
            *aidBytes // Data - The AID itself (using spread operator to include all bytes)
        )
    }

    private fun triggerNotification(title: String, message: String) {
        val channelId = "nfc_detected_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "NFC Events", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(1001, notification)
    }

    //Various Helper Functions.
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        // Ensure the length is even
        if (len % 2 != 0) {
            throw IllegalArgumentException("Hex string must have an even number of characters")
        }
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
        }
        return data
    }
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    // Example local broadcast event to notify the UI when AID is selected
    private fun notifyAidSelected() {
        val intent = Intent("com.yourpackage.app.HCE_AID_SELECTED")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}

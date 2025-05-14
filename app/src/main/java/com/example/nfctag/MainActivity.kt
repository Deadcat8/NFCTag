package com.example.nfctag

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.size
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.nfctag.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() , View.OnClickListener, NfcAdapter.ReaderCallback{
    // Binds Activity to XML
    lateinit var binding : ActivityMainBinding

    // NfcReaderService related variables
    private var nfcReaderService: NfcReaderService? = null
    private var isBound = false
    private var nfcAdapter: NfcAdapter? = null

    // Service connection listener
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as NfcReaderService.LocalBinder
            nfcReaderService = binder.getService()
            isBound = true
            Log.d("MainActivity", "NfcReaderService bound")

            // Once bound, you can potentially load initial data or perform setup
            // Example: Load known readers when bound
        }

        // Cleanup when Service Disconnected
        override fun onServiceDisconnected(name: ComponentName?) {
            nfcReaderService = null
            isBound = false
            Log.d("MainActivity", "NfcReaderService unbound")
        }
    }
    private val nfcEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                // Handle the event when an HCE device ID is identified
                "com.example.nfctag.HCE_DEVICE_ID_IDENTIFIED" -> {
                    val deviceId = intent.getStringExtra("deviceId")
                    if (deviceId != null) {
                        Log.i("MainActivity", "Received HCE_DEVICE_ID_IDENTIFIED: $deviceId")
                        // TODO: Trigger your main event or logic based on the HCE device ID
                    }
                }
                // Handle the event when an NDEF message is read
                "com.example.nfctag.NDEF_MESSAGE_READ" -> {
                    // NdefMessage is Parcelable, so you can get it directly
                    val ndefMessage: NdefMessage? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra("ndefMessage", NdefMessage::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra("ndefMessage")
                    }
                    if (ndefMessage != null) {
                        Log.i("MainActivity", "Received NDEF_MESSAGE_READ. Records: ${ndefMessage.records.size}")
                    } else {
                        Log.w("MainActivity", "Received NDEF_MESSAGE_READ but NdefMessage was null.")
                    }
                }
                // Handle the event when an unknown tag type is discovered
                "com.example.nfctag.UNKNOWN_TAG_DISCOVERED" -> {
                    val tagId = intent.getStringExtra("tagId") ?: "Unknown"
                    Log.w("MainActivity", "Received UNKNOWN_TAG_DISCOVERED: Tag ID: $tagId")
                }
                // Add handlers for other events from NfcReaderService (e.g., NDEF text, URI, etc.)
                "com.example.nfctag.NDEF_TEXT_READ" -> {
                    val text = intent.getStringExtra("text")
                    if (text != null) {
                        Log.i("MainActivity", "Received NDEF_TEXT_READ: $text")
                    }
                }
                "com.example.nfctag.NDEF_URI_READ" -> {
                    val uri = intent.getStringExtra("uri")
                    if (uri != null) {
                        Log.i("MainActivity", "Received NDEF_URI_READ: $uri")
                    }
                }
                // Add more event handlers as needed based on your service's broadcasts
                "com.example.nfctag.NDEF_READ_FAILED" -> {
                    val errorMessage = intent.getStringExtra("errorMessage") ?: "Unknown error"
                    Log.e("MainActivity", "Received NDEF_READ_FAILED: $errorMessage")
                }
                "com.example.nfctag.ISODEP_PROCESSING_FAILED" -> {
                    val errorMessage = intent.getStringExtra("errorMessage") ?: "Unknown error"
                    Log.e("MainActivity", "Received ISODEP_PROCESSING_FAILED: $errorMessage")
                }

                else -> {
                    Log.d("MainActivity", "Received unknown broadcast action: ${intent?.action}")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //enableEdgeToEdge()

        binding.btnMenuOptionHost.setOnClickListener(this)
        binding.btnMenuOptionJoin.setOnClickListener(this)
        binding.btnMenuOptionAboutUs.setOnClickListener(this)

        val filter = IntentFilter().apply {
            addAction("com.example.nfctag.HCE_DEVICE_ID_IDENTIFIED")
            addAction("com.example.nfctag.NDEF_MESSAGE_READ")
            addAction("com.example.nfctag.UNKNOWN_TAG_DISCOVERED")
            addAction("com.example.nfctag.NDEF_TEXT_READ")
            addAction("com.example.nfctag.NDEF_URI_READ")
            addAction("com.example.nfctag.NDEF_READ_FAILED")
            addAction("com.example.nfctag.ISODEP_PROCESSING_FAILED")
            // Add more actions here if your service broadcasts other events
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(nfcEventReceiver, filter)
        // Start the NfcReaderService ready to read the hosting device
        val serviceIntent = Intent(this, NfcReaderService::class.java)
        startService(serviceIntent)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        // Unregister the broadcast receiver when the Activity is destroyed
        LocalBroadcastManager.getInstance(this).unregisterReceiver(nfcEventReceiver)
        // Unbind from the service when the Activity is destroyed
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
    override fun onResume() {
        super.onResume()
        enableNfcReaderMode()
    }
    override fun onPause() {
        super.onPause()
        disableNfcReaderMode()
    }

    private fun enableNfcReaderMode() {
        if (nfcAdapter == null) {
            Log.w("MainActivity", "NFC adapter is null, cannot enable reader mode.")
            return
        }

        val options = Bundle()
        // Specify the NFC technologies you want to detect.
        // Include IsoDep for your HCE interaction.
        // Include Ndef for reading basic NDEF tags.
        nfcAdapter?.enableReaderMode(
            this, // The Activity that implements ReaderCallback
            this, // The ReaderCallback implementation (this Activity)
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            options // Optional bundle of options
        )
        Log.d("MainActivity", "NFC reader mode enabled.")
    }
    private fun disableNfcReaderMode() {
        if (nfcAdapter == null) {
            Log.w("MainActivity", "NFC adapter is null, cannot disable reader mode.")
            return
        }
        nfcAdapter?.disableReaderMode(this)
        Log.d("MainActivity", "NFC reader mode disabled.")
    }

    override fun onTagDiscovered(tag: Tag?) {
        Log.d("MainActivity", "Tag discovered in Activity's onTagDiscovered: ${tag?.id?.toHexString()}")
        // Pass the discovered tag to the NfcReaderService for processing
        if (isBound && nfcReaderService != null) {
            Log.d("MainActivity", "Passing discovered tag to NfcReaderService.")
            nfcReaderService?.processDiscoveredTag(tag)
        } else {
            Log.w("MainActivity", "NfcReaderService not bound or null, cannot process discovered tag.")
        }
    }

    override fun onClick(v: View?) {

        when(v?.id){
            R.id.btn_MenuOptionHost ->{
                val intent = Intent(this, HostActivity::class.java)
                startActivity(intent)
            }
            R.id.btn_MenuOptionJoin ->{
                val intent = Intent(this, JoinActivity::class.java)
                startActivity(intent)
            }
            R.id.btn_MenuOptionAboutUs ->{
                val intent = Intent(this, AboutusActivity::class.java)
                startActivity(intent)
            }
        }
    }

    // Various Helper Functions
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

}
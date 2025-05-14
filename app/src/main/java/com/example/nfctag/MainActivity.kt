package com.example.nfctag

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
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
import android.widget.ImageView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.example.nfctag.databinding.ActivityMainBinding
import com.bumptech.glide.Glide
import androidx.core.content.edit
import kotlin.text.clear

class MainActivity : AppCompatActivity() , View.OnClickListener, NfcAdapter.ReaderCallback {
    // Binds Activity to XML
    lateinit var binding : ActivityMainBinding
    private lateinit var backgroundGifImageView: ImageView

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
    //Nfc Event Receiver and Handler for all disclosed events
    private val nfcEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                // Handle the event when an HCE device ID is identified
                "com.example.nfctag.HCE_DEVICE_ID_IDENTIFIED" -> {
                    val deviceId = intent.getStringExtra("deviceId")
                    if (deviceId != null) {
                        Log.i("MainActivity", "Received HCE_DEVICE_ID_IDENTIFIED: $deviceId")
                        savePeerDeviceId(deviceId)
                    }
                }
                // Handle the event when an NDEF message is read
                "com.example.nfctag.NDEF_MESSAGE_READ" -> {
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

    //Hides alert Dialog
    private val showDialog = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()

        backgroundGifImageView = binding.backgroundGif
        Glide.with(this)
            .asGif()
            .load(R.drawable.appbackground)
            .into(backgroundGifImageView)

        binding.btnMenuOptionHost.setOnClickListener(this)
        binding.btnMenuOptionJoin.setOnClickListener(this)
        binding.btnMenuOptionAboutUs.setOnClickListener(this)
        binding.btnResetButton.setOnClickListener(this)
        val composeView = findViewById<ComposeView>(R.id.Main_compose_view)
        composeView.setContent {
            AlertContent(showDialog)
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

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
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

    }
    override fun onDestroy() {
        super.onDestroy()
         //Unregister the broadcast receiver when the Activity is destroyed
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
    private fun savePeerDeviceId(id: String) {
        val prefs = getSharedPreferences("known_devices", Context.MODE_PRIVATE)
        if (!prefs.contains(id)) {
            prefs.edit() { putBoolean(id, true) }
            Log.d("NFC", "New device detected and stored: $id")
        } else {
            Log.d("NFC", "Known device reconnected: $id")
            //TODO Notify User of being caught
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
            R.id.btn_resetButton ->{
                showDialog.value = true
            }
        }
    }

    @Composable
    fun AlertContent(showDialog: MutableState<Boolean>) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showDialog.value) {
                AlertDialog(
                    onDismissRequest = { showDialog.value = false },
                    title = { Text("Warning") },
                    text = { Text("Are you sure you want to continue? This will cancel the game and lose all current player data.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showDialog.value = false
                            clearSavedPeerDeviceIds(this@MainActivity)
                        }) {
                            Text("Yes")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDialog.value = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
    fun clearSavedPeerDeviceIds(context: Context) {
        // Get the SharedPreferences instance and editor
        val prefs: SharedPreferences = context.getSharedPreferences("known_devices", Context.MODE_PRIVATE)
        prefs.edit() {
            // Clear all entries in the editor
            clear()
            apply()
        }
        Log.d("SharedPreferences", "Cleared all saved peer device IDs from known_devices.")
    }

    // Various Helper Functions
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

}
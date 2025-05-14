import android.content.Context
import java.util.UUID
import androidx.core.content.edit
import android.util.Log

object DeviceIdManager {
    private const val PREFS_NAME = "nfc_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    /**
     * Creates a new device ID if one doesn't exist, or returns the existing one.
     */
    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit() { putString(KEY_DEVICE_ID, id) }
            Log.d("NFC_DeviceIDManager", "New device ID generated: $id")
        }
        else {
            Log.d("NFC_DeviceIDManager", "Known device reconnected: $id")
        }
        return id
    }
}
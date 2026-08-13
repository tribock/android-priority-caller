package com.prioritycaller.app

import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.prioritycaller.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val pickContactLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { readContact(it) }
            }
        }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val requestRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val granted = result.resultCode == RESULT_OK
            Toast.makeText(
                this,
                if (granted) "Call screening role granted" else "Role NOT granted",
                Toast.LENGTH_SHORT
            ).show()
            refreshStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ask for the basic runtime permissions up front.
        requestPermissionsLauncher.launch(
            arrayOf(
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.READ_CALL_LOG,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        )

        binding.btnPickContact.setOnClickListener { launchContactPicker() }
        binding.btnCallScreeningRole.setOnClickListener { requestCallScreeningRole() }
        binding.btnDndAccess.setOnClickListener { requestDndAccess() }
        binding.btnMiuiAutostart.setOnClickListener { openMiuiAutostartSettings() }
        binding.btnMiuiBattery.setOnClickListener { openMiuiBatterySettings() }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ---------- Contact picking ----------

    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        pickContactLauncher.launch(intent)
    }

    private fun readContact(contactUri: Uri) {
        // The picked URI points at a single phone row; resolve the contact ID
        // then pull EVERY number for that contact (mobile/home/work/etc).
        var contactId: String? = null
        var displayName: String? = null

        contentResolver.query(contactUri, null, null, null, null)?.use { cursor: Cursor ->
            if (cursor.moveToFirst()) {
                contactId = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                )
                displayName = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                )
            }
        }

        if (contactId == null) {
            Toast.makeText(this, "Could not read contact", Toast.LENGTH_SHORT).show()
            return
        }

        val numbers = mutableListOf<String>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                numbers.add(cursor.getString(col))
            }
        }

        if (numbers.isEmpty()) {
            Toast.makeText(this, "Contact has no phone numbers", Toast.LENGTH_SHORT).show()
            return
        }

        ContactPrefs.saveContact(this, displayName ?: "Unknown", numbers)
        refreshStatus()
        Toast.makeText(this, "Priority contact saved: $displayName", Toast.LENGTH_SHORT).show()
    }

    // ---------- Call screening role (required for CallScreeningService to fire) ----------

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                    Toast.makeText(this, "Already granted", Toast.LENGTH_SHORT).show()
                    return
                }
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                requestRoleLauncher.launch(intent)
            } else {
                Toast.makeText(this, "Call screening role not available on this device", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Requires Android 10+", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Do Not Disturb policy access ----------

    private fun requestDndAccess() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, "Already granted", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    // ---------- MIUI-specific screens (best-effort; layouts vary by MIUI version) ----------

    private fun openMiuiAutostartSettings() {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Couldn't open Autostart screen automatically — go to " +
                    "Security app > Permissions > Autostart and enable this app manually.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openMiuiBatterySettings() {
        try {
            val intent = Intent().apply {
                action = "miui.intent.action.POWER_HIDE_MODE_APP_LIST"
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Fallback: generic Android battery optimization screen
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            Toast.makeText(
                this,
                "Open Battery saver here and set it to 'No restrictions'.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---------- Status text ----------

    private fun refreshStatus() {
        val name = ContactPrefs.getContactName(this)
        binding.tvSelectedContact.text = if (name != null) {
            "Priority contact: $name"
        } else {
            "No priority contact selected"
        }

        val nm = getSystemService(NotificationManager::class.java)
        val dndGranted = nm.isNotificationPolicyAccessGranted
        val roleGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else false

        binding.tvStatus.text = "DND access: ${if (dndGranted) "granted" else "NOT granted"}\n" +
            "Call screening role: ${if (roleGranted) "granted" else "NOT granted"}\n" +
            "Complete steps 1-5 above, in order, then leave the app installed and " +
            "whitelisted in the background. Test with a real call once done."
    }
}

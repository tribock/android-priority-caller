package com.prioritycaller.app

import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.prioritycaller.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var contactsExpanded = false

    private val pickContactLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { readContact(it) }
            }
        }

    private val pickRingtoneLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.let {
                    IntentCompat.getParcelableExtra(
                        it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java
                    )
                }
                ContactPrefs.setRingtoneUri(this, uri)
            }
        }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openRingtonePicker()
            } else {
                Toast.makeText(
                    this,
                    "Storage permission is needed to use a custom ringtone file",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

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

        requestPermissionsLauncher.launch(
            arrayOf(
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        )

        binding.btnPickContact.setOnClickListener { launchContactPicker() }
        binding.btnCallScreeningRole.setOnClickListener { requestCallScreeningRole() }
        binding.btnDndAccess.setOnClickListener { requestDndAccess() }
        binding.btnMiuiAutostart.setOnClickListener { openMiuiAutostartSettings() }
        binding.btnMiuiBattery.setOnClickListener { openMiuiBatterySettings() }
        binding.contactsSectionHeader.setOnClickListener {
            contactsExpanded = !contactsExpanded
            applyContactsExpandedState(animate = true)
        }

        binding.btnPickRingtone.setOnClickListener { launchRingtonePicker() }
        binding.btnRingtoneInfo.setOnClickListener { showRingtoneInfo() }
        binding.btnResetRingtone.setOnClickListener { resetRingtone() }

        binding.switchPaused.isChecked = ContactPrefs.isEnabled(this@MainActivity)
        binding.switchPaused.setOnCheckedChangeListener { _, isChecked ->
            ContactPrefs.setEnabled(this@MainActivity, isChecked)
        }

        contactsExpanded = ContactPrefs.getAllContacts(this).isNotEmpty()
        applyContactsExpandedState(animate = false)
        refreshContactList()
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

        ContactPrefs.addContact(this, displayName ?: "Unknown", numbers)
        refreshContactList()
        Toast.makeText(this, "Added: $displayName", Toast.LENGTH_SHORT).show()
    }

    // ---------- Contact list UI ----------

    private fun applyContactsExpandedState(animate: Boolean) {
        binding.contactListSection.visibility = if (contactsExpanded) View.VISIBLE else View.GONE
        val targetRotation = if (contactsExpanded) 180f else 0f
        if (animate) {
            binding.ivExpandContacts.animate().rotation(targetRotation).setDuration(200).start()
        } else {
            binding.ivExpandContacts.rotation = targetRotation
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun refreshContactList() {
        binding.contactListContainer.removeAllViews()
        val contacts = ContactPrefs.getAllContacts(this)

        binding.tvContactCount.text = contacts.size.toString()
        binding.tvContactCount.visibility = if (contacts.isEmpty()) View.GONE else View.VISIBLE

        if (contacts.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.no_priority_contacts)
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.md_on_surface_variant))
                setPadding(0, dp(4), 0, dp(4))
            }
            binding.contactListContainer.addView(empty)
            return
        }

        for (contact in contacts) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_contact_row)
                setPadding(dp(14), dp(10), dp(10), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }
            val label = TextView(this).apply {
                text = contact.name
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.md_on_surface))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val removeBtn = ImageButton(this).apply {
                setImageResource(R.drawable.ic_close)
                contentDescription = getString(R.string.remove_contact_cd, contact.name)
                background = null
                setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.md_on_surface_variant))
                setOnClickListener {
                    ContactPrefs.removeContact(this@MainActivity, contact)
                    refreshContactList()
                }
            }
            row.addView(label)
            row.addView(removeBtn)
            binding.contactListContainer.addView(row)
        }
    }

    // ---------- Ringtone picking ----------

    private fun launchRingtonePicker() {
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, storagePermission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            openRingtonePicker()
        } else {
            requestStoragePermissionLauncher.launch(storagePermission)
        }
    }

    private fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ContactPrefs.getRingtoneUri(this@MainActivity))
        }
        pickRingtoneLauncher.launch(intent)
    }

    private fun resetRingtone() {
        ContactPrefs.setRingtoneUri(this, null)
        Toast.makeText(this, "Ringtone reset to default", Toast.LENGTH_SHORT).show()
    }

    private fun showRingtoneInfo() {
        val uri = ContactPrefs.getRingtoneUri(this)
        val name = if (uri != null) {
            RingtoneManager.getRingtone(this, uri)?.getTitle(this) ?: "Custom sound"
        } else {
            "Default"
        }
        Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
    }

    // ---------- Call screening role ----------

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

    // ---------- MIUI-specific screens ----------

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
        val nm = getSystemService(NotificationManager::class.java)
        val dndGranted = nm.isNotificationPolicyAccessGranted
        val roleGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else false

        binding.tvStatus.text = "DND access: ${if (dndGranted) "granted" else "NOT granted"}\n" +
            "Call screening role: ${if (roleGranted) "granted" else "NOT granted"}\n" +
            "Complete the steps above, in order, then leave the app and " +
            "test with a real call once done."
    }
}

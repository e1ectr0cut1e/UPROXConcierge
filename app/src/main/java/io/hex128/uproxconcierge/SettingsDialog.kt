package io.hex128.uproxconcierge

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.DialogFragment

class SettingsDialog : DialogFragment() {
    var settings: Settings? = null
    var onSaved: (() -> Unit)? = null
    var checkForUpdates: (() -> Unit)? = null
    var clearTrustedCertificates: (() -> Unit)? = null
    var trustedCertificatesAvailable = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity()
            .layoutInflater
            .inflate(R.layout.dialog_settings, null)

        val editUrl = view.findViewById<EditText>(R.id.editUrl)
        val editUser = view.findViewById<EditText>(R.id.editUsername)
        val editPassword = view.findViewById<EditText>(R.id.editPassword)
        val updateSwitch = view.findViewById<SwitchCompat>(R.id.updateSwitch)
        val clearTrustedCertificatesBtn = view.findViewById<Button>(R.id.clearTrustedCertificates)

        editUrl.setText(settings?.url)
        editUser.setText(settings?.user)
        editPassword.setText(settings?.password)
        updateSwitch.isChecked = settings?.isAutoUpdateCheckEnabled == true
        clearTrustedCertificatesBtn.visibility = if (trustedCertificatesAvailable) {
            View.VISIBLE
        } else {
            View.GONE
        }
        clearTrustedCertificatesBtn.setOnClickListener { _ ->
            clearTrustedCertificates?.invoke()
            clearTrustedCertificatesBtn.visibility = View.GONE
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                settings?.url = editUrl.text.toString()
                settings?.user = editUser.text.toString()
                settings?.password = editPassword.text.toString()
                settings?.isAutoUpdateCheckEnabled = updateSwitch.isChecked
                onSaved?.invoke()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.check_for_updates) { _, _ ->
                checkForUpdates?.invoke()
            }
            .create()
    }
}

package io.hex128.uproxconcierge

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.core.content.edit

class PropertiesDialog : DialogFragment() {

    var onSaved: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = requireActivity()
            .layoutInflater
            .inflate(R.layout.dialog_properties, null)

        val editUrl = view.findViewById<EditText>(R.id.editUrl)
        val editUser = view.findViewById<EditText>(R.id.editUsername)
        val editPassword = view.findViewById<EditText>(R.id.editPassword)

        val prefs = requireActivity()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)

        editUrl.setText(prefs.getString("url", ""))
        editUser.setText(prefs.getString("user", ""))
        editPassword.setText(prefs.getString("password", ""))

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->

                prefs.edit {
                    putString("url", editUrl.text.toString())
                        .putString("user", editUser.text.toString())
                        .putString("password", editPassword.text.toString())
                }

                onSaved?.invoke()

            }
            .setNegativeButton(R.string.cancel, null)
            .create()

    }

}
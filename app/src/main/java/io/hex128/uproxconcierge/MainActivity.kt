package io.hex128.uproxconcierge

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import java.security.MessageDigest
import java.security.cert.X509Certificate
import kotlin.collections.set


class MainActivity : AppCompatActivity() {
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var doorButtonLayout: LinearLayout
    private lateinit var snackbar: PersistentSnackbar
    private lateinit var uprox: UProxWeb
    private var updateAvailable = false

    private lateinit var settings: Settings
    val temporaryTrustedFingerprints: MutableMap<String, String> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        swipeRefreshLayout = this.findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener { loadDoors() }
        doorButtonLayout = findViewById(R.id.doorButtonLayout)
        snackbar = PersistentSnackbar.make(
            findViewById(R.id.layout), "", Snackbar.LENGTH_INDEFINITE
        )
        settings = Settings(this)
        loadDoors()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> showSettingsDialog()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun isConfigured(): Boolean {
        return settings.url.isNotBlank()
    }

    private fun setRefreshing(refreshing: Boolean) {
        runOnUiThread { swipeRefreshLayout.isRefreshing = refreshing }
    }

    private fun clearTrustedCertificates() {
        temporaryTrustedFingerprints.clear()
        settings.trustedFingerprints = setOf()
    }

    private fun showSettingsDialog() {
        val dialog = SettingsDialog()
        dialog.settings = settings
        dialog.onSaved = { loadDoors() }
        dialog.checkForUpdates = { checkForUpdates(verbose = true) }
        dialog.trustedCertificatesAvailable =
            settings.trustedFingerprints.isNotEmpty() or temporaryTrustedFingerprints.isNotEmpty()
        dialog.clearTrustedCertificates = { clearTrustedCertificates() }
        dialog.show(supportFragmentManager, Settings.SETTINGS_DIALOG_TAG)
    }

    private fun showOpenDoorFeedback(success: Boolean) {
        runOnUiThread {
            snackbar.setText(
                if (success) {
                    R.string.access_granted
                } else {
                    R.string.access_request_failed
                }
            )
            snackbar.setAction(null, null)
            snackbar.duration = Snackbar.LENGTH_SHORT
            snackbar.show()
        }
    }

    private fun createCertificateHash(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(cert.encoded)
            .joinToString(":") {
                "%02X".format(it)
            }
    }

    private fun validateCertificate(certificate: X509Certificate, onAccept: () -> Unit): Boolean {
        val trustedFingerprints: MutableMap<String, String> =
            settings.trustedFingerprints.mapNotNull {
                it
                    .split(" ", limit = 2)
                    .takeIf { parts -> parts.size == 2 }
                    ?.let { parts -> parts[0] to parts[1] }
            }.toMap().toMutableMap()
        val name = certificate.subjectDN.name
        val hash = createCertificateHash(certificate)
        if (trustedFingerprints[name] == hash) {
            return true
        } else if (temporaryTrustedFingerprints[name] == hash) {
            return true
        }
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(R.string.untrusted_certificate)
                .setMessage(getString(R.string.certificate_trust_prompt, name, hash))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.certificate_trust_and_save) { _, _ ->
                    trustedFingerprints[name] = hash
                    settings.trustedFingerprints = trustedFingerprints.entries.map {
                        "${it.key} ${it.value}"
                    }.toSet()
                    onAccept()
                }
                .setNeutralButton(R.string.certificate_trust_once) { _, _ ->
                    temporaryTrustedFingerprints[name] = hash
                    onAccept()
                }
                .show()
        }
        return false
    }

    private fun authenticate(onResult: (Exception?) -> Unit) {
        runOnUiThread {
            snackbar.setText(R.string.status_authorizing)
            snackbar.setAction(null, null)
            snackbar.duration = Snackbar.LENGTH_INDEFINITE
            snackbar.show()
        }
        uprox.authenticate(settings.user, settings.password) { sid, authException ->
            if (authException != null || sid.isNullOrBlank()) {
                val errorMessage = when {
                    authException != null -> authException.message
                    sid.isNullOrBlank() -> getString(R.string.status_empty_session_id)
                    else -> null
                }
                runOnUiThread {
                    snackbar.setText(
                        getString(
                            R.string.status_authorization_failed,
                            errorMessage ?: getString(R.string.status_unknown_error)
                        )
                    )
                    snackbar.setAction(R.string.action_retry) { loadDoors() }
                    snackbar.duration = Snackbar.LENGTH_INDEFINITE
                    snackbar.show()
                }
            }
            onResult(authException)
        }
    }

    private fun loadDoors() {
        if (!isConfigured()) {
            showSettingsDialog()
            return
        }
        uprox = UProxWeb(settings.url) { cert ->
            validateCertificate(cert) { loadDoors() }
        }
        setRefreshing(true)
        authenticate { authException ->
            if (authException != null) {
                setRefreshing(false)
                return@authenticate
            }
            runOnUiThread {
                snackbar.setText(R.string.status_retrieving_doors)
                snackbar.setAction(null, null)
                snackbar.duration = Snackbar.LENGTH_INDEFINITE
                snackbar.show()
            }

            uprox.fetchDoors { doorList, exception ->
                setRefreshing(false)
                if (exception != null) {
                    runOnUiThread {
                        snackbar.setText(
                            getString(
                                R.string.status_failed_retrieving_doors,
                                exception.message
                            )
                        )
                        snackbar.setAction(R.string.action_retry) { loadDoors() }
                        snackbar.duration = Snackbar.LENGTH_INDEFINITE
                        snackbar.show()
                    }
                    return@fetchDoors
                }
                try {
                    if (doorList != null) {
                        runOnUiThread {
                            doorButtonLayout.removeAllViews()
                            val sortedDoorList = (0 until doorList.length())
                                .map { doorList.getJSONObject(it) }
                                .sortedBy { it.getString("Name") }
                            for (door in sortedDoorList) {
                                val btn = Button(this)
                                btn.text = door.getString("Name")
                                btn.isEnabled = door.getInt("HealthStatus") != 2
                                btn.setOnClickListener { openDoor(door.getInt("Token")) }
                                doorButtonLayout.addView(btn)
                            }
                        }
                        idleSnackbar()
                        if (settings.isAutoUpdateCheckEnabled) {
                            checkForUpdates()
                        }
                    }
                } catch (exception: Exception) {
                    runOnUiThread {
                        snackbar.setText(
                            getString(
                                R.string.status_failed_parsing_doors,
                                exception.message
                            )
                        )
                        snackbar.setAction(R.string.action_retry) { loadDoors() }
                        snackbar.duration = Snackbar.LENGTH_INDEFINITE
                        snackbar.show()
                    }
                }
            }
        }
    }

    private fun openDoor(token: Int) {
        runOnUiThread {
            snackbar.setText(R.string.access_request_sent)
            snackbar.setAction(null, null)
            snackbar.duration = Snackbar.LENGTH_INDEFINITE
            snackbar.show()
        }
        uprox.openDoor(token) { openDoorException ->
            if (openDoorException == null) {
                showOpenDoorFeedback(true)
                return@openDoor
            }
            authenticate { authException ->
                if (authException == null) {
                    uprox.openDoor(token) { openDoorException ->
                        showOpenDoorFeedback(openDoorException == null)
                    }
                }
            }
        }
    }

    private fun checkForUpdates(install: Boolean = false, verbose: Boolean = false) {
        val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName
        val updater = Updater(this, currentVersion) { cert ->
            validateCertificate(cert) { checkForUpdates(install, verbose) }
        }
        if (verbose) {
            runOnUiThread {
                snackbar.setText(R.string.status_checking_for_updates)
                snackbar.setAction(R.string.cancel) {
                    updateAvailable = false
                    updater.cancel()
                    idleSnackbar()
                }
                snackbar.duration = Snackbar.LENGTH_INDEFINITE
                snackbar.show()
            }
        }
        updater.checkForUpdates { updateAvailable, version, url, exception ->
            this.updateAvailable = updateAvailable
            if (updateAvailable && version != null && url != null) {
                if (install) {
                    runOnUiThread {
                        snackbar.setText(R.string.status_downloading_update)
                        snackbar.setAction(R.string.cancel) {
                            updater.cancel()
                            idleSnackbar()
                        }
                        snackbar.duration = Snackbar.LENGTH_INDEFINITE
                        snackbar.show()
                    }
                    updater.downloadApk(version, url) { file, _ ->
                        if (file != null) {
                            updater.requestInstall(file)
                            this.updateAvailable = false
                            runOnUiThread {
                                snackbar.setText(R.string.status_installing_update)
                                snackbar.setAction(null, null)
                                snackbar.duration = Snackbar.LENGTH_LONG
                                snackbar.show()
                            }
                        } else {
                            runOnUiThread {
                                snackbar.setText(R.string.status_update_download_failed)
                                snackbar.setAction(R.string.action_retry) {
                                    checkForUpdates(install = true, verbose = true)
                                }
                                snackbar.duration = Snackbar.LENGTH_LONG
                                snackbar.show()
                            }
                        }
                    }
                } else {
                    runOnUiThread {
                        snackbar.setText(R.string.status_update_available)
                        snackbar.setAction(R.string.update) {
                            checkForUpdates(
                                install = true,
                                verbose = true
                            )
                        }
                        snackbar.duration = Snackbar.LENGTH_LONG
                        snackbar.show()
                    }
                }
            } else {
                runOnUiThread {
                    if (exception != null) {
                        snackbar.setText(R.string.status_update_check_failed)
                        snackbar.setAction(R.string.action_retry) {
                            checkForUpdates(
                                install,
                                verbose
                            )
                        }
                        snackbar.duration = Snackbar.LENGTH_LONG
                        snackbar.show()
                    } else if (verbose) {
                        snackbar.setText(R.string.status_already_latest_version)
                        snackbar.setAction(null, null)
                        snackbar.duration = Snackbar.LENGTH_LONG
                        snackbar.show()
                    }
                }
            }
        }
    }

    private fun idleSnackbar() {
        runOnUiThread {
            if (updateAvailable) {
                snackbar.setText(R.string.status_update_available)
                snackbar.setAction(R.string.update) {
                    checkForUpdates(
                        install = true,
                        verbose = true
                    )
                }
                snackbar.duration = Snackbar.LENGTH_LONG
                snackbar.show()
            } else {
                snackbar.dismiss()
                snackbar.setText("")
                snackbar.setAction(null, null)
                snackbar.duration = Snackbar.LENGTH_SHORT
            }
        }
    }
}

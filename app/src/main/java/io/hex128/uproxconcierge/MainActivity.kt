package io.hex128.uproxconcierge

import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar


class MainActivity : AppCompatActivity() {
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var doorButtonLayout: LinearLayout
    private lateinit var snackbar: PersistentSnackbar
    private lateinit var prefs: SharedPreferences
    private lateinit var uprox: UPROXWeb

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeRefreshLayout = this.findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener { loadDoors() }
        doorButtonLayout = findViewById(R.id.doorButtonLayout)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        snackbar = PersistentSnackbar.make(
            findViewById(R.id.layout), "", Snackbar.LENGTH_INDEFINITE
        )

        if (isConfigured()) {
            loadDoors()
        } else {
            showPropertiesDialog()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> showPropertiesDialog()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun isConfigured(): Boolean {
        return !prefs.getString("url", "").isNullOrBlank()
    }

    private fun setRefreshing(refreshing: Boolean) {
        runOnUiThread {
            swipeRefreshLayout.isRefreshing = refreshing
        }
    }

    private fun showPropertiesDialog() {
        val dialog = PropertiesDialog()
        dialog.onSaved = {
            loadDoors()
        }
        dialog.show(supportFragmentManager, "properties")
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

    private fun authenticate(onResult: (Exception?) -> Unit) {
        val user = prefs.getString("user", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        runOnUiThread {
            snackbar.setText(R.string.status_authorizing)
            snackbar.setAction(null, null)
            snackbar.duration = Snackbar.LENGTH_INDEFINITE
            snackbar.show()
        }
        uprox.authenticate(user, password) { sid, authException ->
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
                }
            }
            onResult(authException)
        }
    }

    private fun loadDoors() {
        val url = prefs.getString("url", "") ?: ""
        uprox = UPROXWeb(url)
        setRefreshing(true)
        authenticate { authException ->
            if (authException != null) {
                setRefreshing(false)
                return@authenticate
            }
            runOnUiThread {
                snackbar.setText(R.string.status_retrieving_doors)
                snackbar.setAction(null, null)
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
                                snackbar.dismiss()
                                snackbar.setText("")
                                snackbar.setAction(null, null)
                                snackbar.duration = Snackbar.LENGTH_SHORT
                            }
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
                    }
                }
            }
        }
    }

    private fun openDoor(token: Int) {
        snackbar.setText(R.string.access_request_sent)
        snackbar.setAction(null, null)
        snackbar.duration = Snackbar.LENGTH_SHORT
        snackbar.show()
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
}

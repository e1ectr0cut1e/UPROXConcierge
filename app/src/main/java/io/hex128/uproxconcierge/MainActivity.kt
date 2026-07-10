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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest


class MainActivity : AppCompatActivity() {
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var doorButtonLayout: LinearLayout
    private lateinit var snackbar: Snackbar
    private lateinit var prefs: SharedPreferences

    private val http = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeRefreshLayout = this.findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener { loadDoors() }
        doorButtonLayout = findViewById(R.id.doorButtonLayout)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        snackbar = Snackbar.make(
            findViewById(R.id.layout),
            "",
            Snackbar.LENGTH_INDEFINITE
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

    private fun showPropertiesDialog() {
        val dialog = PropertiesDialog()
        dialog.onSaved = {
            loadDoors()
        }
        dialog.show(supportFragmentManager, "properties")
    }

    private fun loadDoors() {
        val url = prefs.getString("url", "") ?: ""
        val user = prefs.getString("user", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        loadDoors(url, user, password)
    }

    fun authenticate(
        endpoint: String,
        username: String,
        password: String,
        onResult: (String?, Exception?) -> Unit
    ) {
        val url = "$endpoint/json/Authenticate"
        val hashedPassword = generatePasswordHash(password)
        val jsonBody = JSONObject().apply {
            put("UserName", username)
            put("PasswordHash", hashedPassword)
        }.toString()
        val requestBody = RequestBody.create(
            MediaType.parse("application/json"),
            jsonBody
        )
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                call.cancel()
                onResult(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body()?.string() ?: throw Exception("Empty response body")
                response.close()
                try {
                    val sid = JSONObject(body).optString("UserSID", "")
                    if (sid.isBlank()) {
                        onResult(null, Exception("Failed to get SID"))
                    } else {
                        onResult(sid, null)
                    }
                } catch (e: Exception) {
                    onResult(null, e)
                }
            }
        })
    }

    fun openDoor(
        endpoint: String,
        sid: String,
        token: Int,
        onResult: (Exception?) -> Unit
    ) {
        val jsonBody = JSONObject().apply {
            put("UserSID", sid)
            put("Token", token)
        }.toString()
        val requestBody = RequestBody.create(
            MediaType.parse("application/json"),
            jsonBody
        )
        val request = Request.Builder()
            .url("$endpoint/json/DoorAccessIn")
            .post(requestBody)
            .build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                call.cancel()
                onResult(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                onResult(null)
            }
        })
    }

    fun fetchDoors(
        endpoint: String,
        sid: String,
        onResult: (JSONArray?, Exception?) -> Unit
    ) {
        val jsonBody = JSONObject().apply {
            put("UserSID", sid)
            put("SubscriptionEnabled", false)
            put("Limit", 16)
            put("StartToken", 0)
        }.toString()
        val requestBody = RequestBody.create(
            MediaType.parse("application/json"),
            jsonBody
        )
        val request = Request.Builder()
            .url("$endpoint/json/DoorGetList")
            .post(requestBody)
            .build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                call.cancel()
                onResult(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body()?.string()
                response.close()
                try {
                    val obj = JSONObject(json ?: "")
                    val doorList = obj.getJSONArray("Door")
                    onResult(doorList, null)
                } catch (e: Exception) {
                    onResult(null, e)
                }
            }
        })
    }

    fun loadDoors(endpoint: String, user: String, pass: String) {
        runOnUiThread {
            swipeRefreshLayout.isRefreshing = true
            snackbar.setText(R.string.status_authorizing)
            snackbar.setAction(null, null)
            snackbar.duration = Snackbar.LENGTH_INDEFINITE
            snackbar.show()
        }
        authenticate(endpoint, user, pass) { sid, exception ->
            if (exception != null || sid.isNullOrBlank()) {
                val errorMessage = when {
                    exception != null -> exception.message
                    sid.isNullOrBlank() -> getString(R.string.status_empty_session_id)
                    else -> null
                }

                runOnUiThread {
                    swipeRefreshLayout.isRefreshing = false
                    snackbar.setText(
                        getString(
                            R.string.status_authorization_failed,
                            errorMessage ?: getString(R.string.status_unknown_error)
                        )
                    )
                    snackbar.setAction(R.string.action_retry) { loadDoors() }

                }
                return@authenticate
            }

            runOnUiThread {
                snackbar.setText(R.string.status_retrieving_doors)
                snackbar.setAction(null, null)
            }

            fetchDoors(endpoint, sid) { doorList, exception ->
                if (exception != null) {
                    runOnUiThread {
                        swipeRefreshLayout.isRefreshing = false
                        snackbar.setText(
                            getString(
                                R.string.status_failed_retrieving_doors,
                                exception.message
                            )
                        )
                        snackbar.setAction(R.string.action_retry, { loadDoors() })
                    }
                    return@fetchDoors
                }
                runOnUiThread {
                    swipeRefreshLayout.isRefreshing = false
                }
                try {
                    if (doorList != null) {
                        runOnUiThread {
                            doorButtonLayout.removeAllViews()
                            for (i in 0 until doorList.length()) {
                                val door = doorList.getJSONObject(i)
                                val btn = Button(this)
                                btn.text = door.getString("Name")
                                btn.isEnabled = door.getInt("HealthStatus") != 2
                                btn.setOnClickListener {
                                    snackbar.setText(R.string.access_request_sent)
                                    snackbar.setAction(null, null)
                                    snackbar.duration = Snackbar.LENGTH_SHORT
                                    snackbar.show()
                                    openDoor(
                                        endpoint,
                                        sid,
                                        door.getInt("Token"),
                                        { exception ->
                                            runOnUiThread {
                                                snackbar.setText(
                                                    if (exception == null) {
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
                                    )
                                }
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
                        snackbar.setAction(R.string.action_retry, { loadDoors() })
                    }
                }
            }
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
            .uppercase()
    }

    private fun generatePasswordHash(password: String): String {
        val step1 = md5(password)
        val step2 = md5(step1 + "F593B01C562548C6B7A31B30884BDE53")
        val step3 = md5(step2)
        return step3
    }
}

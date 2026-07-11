package io.hex128.uproxconcierge

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

class UPROXWeb {
    private val http = OkHttpClient()
    private var sid: String? = null
    private var endpoint: String

    constructor(endpoint: String) {
        this.endpoint = endpoint
    }

    fun authenticate(
        username: String, password: String, onResult: (String?, Exception?) -> Unit
    ) {
        val url = "$endpoint/json/Authenticate"
        val hashedPassword = generatePasswordHash(password)
        val jsonBody = JSONObject().apply {
            put("UserName", username)
            put("PasswordHash", hashedPassword)
        }.toString()
        val requestBody = RequestBody.create(
            MediaType.parse("application/json"), jsonBody
        )
        val request = Request.Builder().url(url).post(requestBody).build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                call.cancel()
                onResult(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body()?.string() ?: throw Exception("Empty response body")
                response.close()
                try {
                    sid = JSONObject(body).optString("UserSID", "")
                    if (sid.isNullOrBlank()) {
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
        token: Int, onResult: (Exception?) -> Unit
    ) {
        if (sid.isNullOrBlank()) {
            onResult(Exception("SID missing"))
        }
        val jsonBody = JSONObject().apply {
            put("UserSID", sid)
            put("Token", token)
        }.toString()
        val requestBody = RequestBody.create(
            MediaType.parse("application/json"), jsonBody
        )
        val request = Request.Builder().url("$endpoint/json/DoorAccessIn").post(requestBody).build()
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

    fun fetchDoors(onResult: (JSONArray?, Exception?) -> Unit) {
        if (sid.isNullOrBlank()) {
            onResult(null, Exception("SID missing"))
        }
        val jsonBody = JSONObject().apply {
            put("UserSID", sid)
            put("SubscriptionEnabled", false)
            put("Limit", 16)
            put("StartToken", 0)
        }.toString()
        val requestBody = RequestBody.create(
            MediaType.parse("application/json"), jsonBody
        )
        val request = Request.Builder().url("$endpoint/json/DoorGetList").post(requestBody).build()
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

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.uppercase()
    }

    private fun generatePasswordHash(password: String): String {
        val step1 = md5(password)
        val step2 = md5(step1 + "F593B01C562548C6B7A31B30884BDE53")
        val step3 = md5(step2)
        return step3
    }
}

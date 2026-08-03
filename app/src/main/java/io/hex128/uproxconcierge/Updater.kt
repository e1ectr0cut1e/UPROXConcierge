package io.hex128.uproxconcierge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersionOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.cert.X509Certificate

class Updater(
    private val context: Context,
    currentVersion: String?,
    onUntrustedCertificate: (X509Certificate) -> Boolean
) {
    companion object {
        const val REPO_NAME = "e1ectr0cut1e/UPROXConcierge"
        const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
    }

    private val githubAPILatestReleaseUrl =
        "https://api.github.com/repos/${REPO_NAME}/releases/latest"
    private val currentVersion = currentVersion?.toVersionOrNull()
    private val http = CompatOkHttpClientWInteractiveValidation(onUntrustedCertificate).build()

    init {
        context.externalCacheDir
            ?.listFiles()
            ?.filter { it.extension.equals("apk", ignoreCase = true) }
            ?.forEach(File::delete)
    }

    fun checkForUpdates(onCheckResult: (Boolean, Version?, URL?, Exception?) -> Unit) {
        if (currentVersion == null) {
            onCheckResult(false, null, null, null)
            return
        }
        val request = Request.Builder().url(githubAPILatestReleaseUrl).get().build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                call.cancel()
                onCheckResult(false, null, null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                check(response.isSuccessful)
                val body = response.body()?.string() ?: throw Exception("Empty response body")
                response.close()
                try {
                    val releaseData = JSONObject(body)
                    val latestVersion = releaseData.getString("name").toVersionOrNull()
                    if (latestVersion == null || latestVersion <= currentVersion) {
                        onCheckResult(false, null, null, null)
                        return
                    }
                    val assetList = releaseData.getJSONArray("assets")
                    for (i in 0 until assetList.length()) {
                        val assetData = assetList.getJSONObject(i)
                        if (assetData.getString("content_type") == APK_CONTENT_TYPE) {
                            onCheckResult(
                                true,
                                latestVersion,
                                URL(assetData.getString("browser_download_url")),
                                null
                            )
                            return
                        }
                    }
                } catch (e: Exception) {
                    onCheckResult(false, null, null, e)
                }
            }
        })
    }

    fun downloadApk(version: Version, url: URL, onDownload: (File?, Exception?) -> Unit) {
        try {
            http.newCall(
                Request.Builder().url(url).build()
            ).execute().use { response ->
                check(response.isSuccessful)
                val apkFile =
                    File(context.externalCacheDir, "${context.packageName}-${version}.apk")
                response.body()!!.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                onDownload(apkFile, null)
            }
        } catch (e: Exception) {
            onDownload(null, e)
        }
    }

    fun requestInstall(apkFile: File) {
        val uri: Uri
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        } else {
            uri = Uri.fromFile(apkFile)
        }

        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_CONTENT_TYPE)
            addFlags(flags)
        })
    }

    fun cancel() {
        http.dispatcher().cancelAll()
    }
}

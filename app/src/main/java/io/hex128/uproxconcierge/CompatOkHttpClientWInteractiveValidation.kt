package io.hex128.uproxconcierge

import android.annotation.SuppressLint
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class CompatOkHttpClientWInteractiveValidation(
    private val onUntrustedCertificate: (X509Certificate) -> Boolean
) {

    private class Tls12SocketFactory(
        private val delegate: SSLSocketFactory
    ) : SSLSocketFactory() {

        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        override fun createSocket(
            s: Socket,
            host: String,
            port: Int,
            autoClose: Boolean
        ): Socket =
            patch(delegate.createSocket(s, host, port, autoClose))

        override fun createSocket(
            host: String,
            port: Int
        ): Socket =
            patch(delegate.createSocket(host, port))

        override fun createSocket(
            host: String,
            port: Int,
            localHost: InetAddress,
            localPort: Int
        ): Socket =
            patch(delegate.createSocket(host, port, localHost, localPort))

        override fun createSocket(
            host: InetAddress,
            port: Int
        ): Socket =
            patch(delegate.createSocket(host, port))

        override fun createSocket(
            address: InetAddress,
            port: Int,
            localAddress: InetAddress,
            localPort: Int
        ): Socket =
            patch(delegate.createSocket(address, port, localAddress, localPort))

        override fun createSocket(): Socket =
            patch(delegate.createSocket())

        private fun patch(socket: Socket): Socket {
            if (socket is SSLSocket) {
                socket.enabledProtocols = arrayOf("TLSv1.2")
            }
            return socket
        }
    }

    @SuppressLint("CustomX509TrustManager")
    private class InteractiveTrustManager(
        private val onUntrustedCertificate: (X509Certificate) -> Boolean
    ) : X509TrustManager {
        private val defaultTrustManager: X509TrustManager = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        ).apply {
            init(null as KeyStore?)
        }.trustManagers.filterIsInstance<X509TrustManager>().first()

        override fun checkClientTrusted(
            chain: Array<X509Certificate>,
            authType: String
        ) {
            defaultTrustManager.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                defaultTrustManager.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                val cert = chain.first()
                if (!onUntrustedCertificate(cert)) {
                    throw e
                }
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            defaultTrustManager.acceptedIssuers
    }

    fun build(): OkHttpClient {
        val trustManager = InteractiveTrustManager(onUntrustedCertificate)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(
            null,
            arrayOf<TrustManager>(trustManager),
            SecureRandom()
        )

        return OkHttpClient.Builder()
            .sslSocketFactory(
                Tls12SocketFactory(sslContext.socketFactory),
                trustManager
            )
            .connectionSpecs(
                listOf(
                    ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS,
                    ConnectionSpec.CLEARTEXT
                )
            )
            .build()
    }
}

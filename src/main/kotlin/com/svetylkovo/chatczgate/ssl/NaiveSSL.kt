package com.svetylkovo.chatczgate.ssl

import javax.net.ssl.*


object NaiveSSL {

    val hostnameVerifier = HostnameVerifier { p0, p1 -> true }

    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(p0: Array<out java.security.cert.X509Certificate>?, p1: String?) {
        }

        override fun checkServerTrusted(p0: Array<out java.security.cert.X509Certificate>?, p1: String?) {
        }

        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
    }

    fun getSocketFactory(): SSLSocketFactory {
        // Create a trust manager that does not validate certificate chains
        val trustAllCerts = arrayOf<TrustManager>(trustManager)

        // Install the all-trusting trust manager
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        // Create an ssl socket factory with our all-trusting manager
        return sslContext.socketFactory
    }

}
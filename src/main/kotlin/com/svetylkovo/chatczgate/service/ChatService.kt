package com.svetylkovo.chatczgate.service

import com.svetylkovo.chatczgate.beans.Room
import com.svetylkovo.chatczgate.beans.User
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.net.CookieManager

interface ChatService {

    @GET("/api/user/{id}")
    fun getUserById(@Path("id") id: Int): User?

    @GET("/login")
    fun pingLoginPage()

    @POST("/json/getHeader")
    fun pingHeader()

    @POST("/json/getRoomUserTime")
    fun pingRoomUserTime(room: Room)

    companion object {
        fun obtain(): ChatService = Retrofit.Builder()
                .baseUrl("https://chat.cz")
                .client(
                    OkHttpClient.Builder()
                        .cookieJar(JavaNetCookieJar(CookieManager()))
                        .addInterceptor { chain ->
                            val request = chain.request()
                                    .newBuilder()
                                    .header("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.106 Safari/537.36")
                                    .build()
                            chain.proceed(request)
                        }
                        .build()
                )
                .build()
                .create(ChatService::class.java)
    }



}



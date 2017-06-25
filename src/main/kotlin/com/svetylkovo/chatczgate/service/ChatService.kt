package com.svetylkovo.chatczgate.service

import com.svetylkovo.chatczgate.beans.AnonymousLogin
import com.svetylkovo.chatczgate.beans.Login
import com.svetylkovo.chatczgate.beans.Room
import com.svetylkovo.chatczgate.beans.User
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.net.CookieManager

interface ChatService {
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

    @GET("/api/user/{uid}")
    fun getUserById(@Path("uid") id: Int): User?

    @GET("/login")
    fun pingLoginPage()

    @POST("/json/getHeader")
    fun pingHeader()

    @POST("/json/getRoomUserTime")
    fun pingRoomUserTime(room: Room)

    @POST("/json/getText")
    fun getRoomText(room: Room): String

    @GET("/api/rooms")
    fun getRoomList(): List<Room>

    @POST("/login")
    fun login(login: Login): ResponseBody

    @POST("/login")
    fun loginAnonymously(anonymousLogin: AnonymousLogin): ResponseBody


}



package com.svetylkovo.chatczgate.service

import com.svetylkovo.chatczgate.beans.AnonymousLogin
import com.svetylkovo.chatczgate.beans.Login
import com.svetylkovo.chatczgate.beans.Room
import com.svetylkovo.chatczgate.beans.RoomResponse
import com.svetylkovo.chatczgate.rest.ChatClient
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import java.net.CookieManager

class ChatService {

    private val client = Retrofit.Builder()
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
            .create(ChatClient::class.java)

    fun pingLoginPage() {
        client.pingLoginPage()
    }

    fun pingHeader() {
        client.pingHeader()
    }

    fun pingRoomUserTime(room: Room) {
        client.pingRoomUserTime(room)
    }

    fun getRoomText(room: Room) = client.getRoomText(room).bodyOrError()

    fun getRoomList() = client.getRoomList().bodyOrError()

    fun login(login: Login) = client.login(login).responseBodyString()

    fun loginAnonymously(anonymousLogin: AnonymousLogin) = client.loginAnonymously(anonymousLogin).responseBodyString()

    fun getUserById(uid: Int) = client.getUser(uid).bodyOrError()?.user

    fun getUserProfile(uid: Int) = client.getUserProfile(uid).bodyOrError()?.profile

}

fun <T> Call<T>.bodyOrError(): T? {
    val resp = this.execute()

    if (resp.isSuccessful) {
        return resp.body()
    }

    val errorBody = resp.errorBody()?.string()
    throw RuntimeException("Failed to parse the HTTP response: $errorBody")
}

fun Call<ResponseBody>.responseBodyString() = bodyOrError()?.string() ?: ""
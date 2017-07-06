package com.svetylkovo.chatczgate.service

import com.fasterxml.jackson.databind.JsonMappingException
import com.svetylkovo.chatczgate.beans.Gender
import com.svetylkovo.chatczgate.beans.Room
import com.svetylkovo.chatczgate.rest.ChatClient
import com.svetylkovo.chatczgate.ssl.NaiveSSL
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
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
//                            .addInterceptor(
//                                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
//                            )
                            .sslSocketFactory(NaiveSSL.getSocketFactory(), NaiveSSL.trustManager)
                            .hostnameVerifier(NaiveSSL.hostnameVerifier)
                            .build()
            )
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(ChatClient::class.java)

    fun pingLoginPage() {
        client.pingLoginPage().execute()
    }

    fun pingHeader() {
        client.pingHeader().execute()
    }

    fun pingRoomUserTime(room: Room) {
        client.pingRoomUserTime(room.roomId).execute()
    }

    fun getRoomText(room: Room) = client.getRoomText(room.roomId, room.chatIndex).bodyOrError()

    fun getRoomList() = client.getRoomList().bodyOrError()?.rooms

    fun login(email: String, password: String) = client.login(email, password).responseBodyString()

    fun loginAnonymously(user: String, gender: Gender) = client.loginAnonymously(user, gender.value).responseBodyString()

    fun getUserById(uid: Int) = client.getUser(uid).bodyOrError()?.user

    fun getUserProfile(uid: Int) = client.getUserProfile(uid).bodyOrError()?.profile

    fun logout() = client.logout().responseBodyString()

    fun join(room: Room) = client.join(room.name).responseBodyString()

    fun getRoomUsers(room: Room) = client.getRoomUsers(room.roomId).bodyOrError()?.users

    fun getRoomAdmins(room: Room) = client.getRoomAdmins(room.roomId).bodyOrError()?.admins

    fun part(room: Room) = client.part(room.roomId).responseBodyString()

    fun say(room: Room, msg: String) =
            client.sendRoomMessage(room.roomId, room.chatIndex, msg).bodyOrError()

    fun getRoomInfo(room: Room) = client.getRoomInfo(room.roomId).bodyOrError()?.room

}

private fun <T> Call<T>.bodyOrError(): T? {
    val resp = this.execute()
    if (resp.isSuccessful) {
        return resp.body()
    }

    val errorBody = resp.errorBody()?.string()
    throw RuntimeException("Failed to parse the HTTP response: $errorBody")
}

private fun Call<ResponseBody>.responseBodyString() = bodyOrError()?.string() ?: ""
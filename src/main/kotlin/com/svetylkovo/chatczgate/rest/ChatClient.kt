package com.svetylkovo.chatczgate.rest

import com.svetylkovo.chatczgate.beans.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ChatClient {

    @GET("/api/user/{uid}")
    fun getUser(@Path("uid") uid: Int): Call<RestResponse>

    @GET("/api/user/{uid}/profile")
    fun getUserProfile(@Path("uid") uid: Int): Call<RestResponse>

    @GET("/login")
    fun pingLoginPage(): Call<Void>

    @POST("/json/getHeader")
    fun pingHeader(): Call<Void>

    @POST("/json/getRoomUserTime")
    fun pingRoomUserTime(room: Room): Call<Void>

    @GET("/api/rooms")
    fun getRoomList(): Call<List<Room>>

    @POST("/login")
    fun login(login: Login): Call<ResponseBody>

    @POST("/login")
    fun loginAnonymously(anonymousLogin: AnonymousLogin): Call<ResponseBody>

    @POST("/json/getText")
    fun sendRoomMessage(roomId: Int, chatIndex: Int, text: String? = null, userIdTo: Int = 0): Call<RestResponse>

    @POST("/json/getText")
    fun getRoomText(roomId: Int, chatIndex: Int): Call<RoomResponse>

    @GET("/logout")
    fun logout(): Call<ResponseBody>

    @GET("/logout")
    fun join(name: String)

    @GET("/api/room/{id}/users")
    fun getRoomUsers(@Path("id") roomId: Int): Call<RestResponse>

    @GET("/api/room/{id}/admins")
    fun getRoomAdmins(@Path("id") roomId: Int): Call<RestResponse>

    @GET("/api/room/{id}")
    fun getRoomInfo(@Path("id") roomId: Int): Call<RestResponse>

    @GET("/leaveRoom/{id}")
    fun part(@Path("id") roomId: Int): Call<ResponseBody>

}



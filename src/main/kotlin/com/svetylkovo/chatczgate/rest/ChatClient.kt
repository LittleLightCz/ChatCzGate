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
    fun pingLoginPage()

    @POST("/json/getHeader")
    fun pingHeader()

    @POST("/json/getRoomUserTime")
    fun pingRoomUserTime(room: Room)

    @POST("/json/getText")
    fun getRoomText(room: Room): Call<RoomResponse>

    @GET("/api/rooms")
    fun getRoomList(): Call<List<Room>>

    @POST("/login")
    fun login(login: Login): Call<ResponseBody>

    @POST("/login")
    fun loginAnonymously(anonymousLogin: AnonymousLogin): Call<ResponseBody>

}



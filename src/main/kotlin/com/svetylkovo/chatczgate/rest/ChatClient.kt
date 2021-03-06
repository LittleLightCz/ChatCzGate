package com.svetylkovo.chatczgate.rest

import com.svetylkovo.chatczgate.beans.ChatHeader
import com.svetylkovo.chatczgate.beans.RestResponse
import com.svetylkovo.chatczgate.beans.StoredMessages
import com.svetylkovo.chatczgate.beans.StoredMessagesUsers
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface ChatClient {

    @GET("/api/user/{uid}")
    fun getUser(@Path("uid") uid: Int): Call<RestResponse>

    @GET("/api/user/{uid}/profile")
    fun getUserProfile(@Path("uid") uid: Int): Call<RestResponse>

    @GET("/login")
    fun pingLoginPage(): Call<Void>

    @GET("/msg")
    fun pingStoredMessagesPage(): Call<Void>

    @POST("/json/getHeader")
    fun getChatHeader(): Call<ChatHeader>

    @FormUrlEncoded
    @POST("/json/getMsgUserList")
    fun getStoredMessagesUsers(
            @Field("offset") offset: Int = 0,
            @Field("limit") limit: Int = 100
    ): Call<StoredMessagesUsers>

    @FormUrlEncoded
    @POST("/json/getMessage")
    fun getStoredMessages(@Field("userId") userId: Int): Call<StoredMessages>

    @FormUrlEncoded
    @POST("/json/getRoomUserTime")
    fun pingRoomUserTime(@Field("roomId") roomId: Int): Call<Void>

    @GET("/api/rooms")
    fun getRoomList(): Call<RestResponse>

    @FormUrlEncoded
    @POST("/login")
    fun login(@Field("email") email: String, @Field("password") password: String): Call<ResponseBody>

    @FormUrlEncoded
    @POST("/login")
    fun loginAnonymously(@Field("user") user: String, @Field("sex") sex: String): Call<ResponseBody>

    @FormUrlEncoded
    @POST("/json/getText")
    fun sendRoomMessage(
            @Field("roomId") roomId: Int,
            @Field("chatIndex") chatIndex: String,
            @Field("text") text: String? = null,
            @Field("userIdTo") userIdTo: Int = 0
    ): Call<RestResponse>

    @FormUrlEncoded
    @POST("/json/getText")
    fun getRoomText(
        @Field("roomId") roomId: Int,
        @Field("chatIndex") chatIndex: String
    ): Call<RestResponse>

    @FormUrlEncoded
    @POST("json/setRoomSetting")
    fun setRoomSettings(
        @Field("roomId") roomId: Int,
        @Field("description") description: String,
        @Field("password") password: String
    ): Call<ResponseBody>

    @GET("/logout")
    fun logout(): Call<ResponseBody>

    @GET("/{roomName}")
    fun join(@Path("roomName") name: String): Call<ResponseBody>

    @GET("/api/room/{id}/users")
    fun getRoomUsers(@Path("id") roomId: Int): Call<RestResponse>

    @GET("/api/room/{id}/admins")
    fun getRoomAdmins(@Path("id") roomId: Int): Call<RestResponse>

    @GET("/api/room/{id}")
    fun getRoomInfo(@Path("id") roomId: Int): Call<RestResponse>

    @GET("/leaveRoom/{id}")
    fun part(@Path("id") roomId: Int): Call<ResponseBody>

}



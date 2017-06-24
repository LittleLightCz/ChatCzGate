package com.svetylkovo.chatczgate.service

import com.svetylkovo.chatczgate.beans.User
import retrofit2.http.GET
import retrofit2.http.Path

interface ChatService {

    @GET("/api/user/{id}")
    fun getUserById(@Path("id") id: Int): User?

}



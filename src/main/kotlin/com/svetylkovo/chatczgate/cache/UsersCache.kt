package com.svetylkovo.chatczgate.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.svetylkovo.chatczgate.beans.User
import com.svetylkovo.chatczgate.service.ChatService
import retrofit2.Retrofit
import java.util.*

object UsersCache {

    private var users = ArrayList<User>()

    private val mapper = ObjectMapper()

    private val service = Retrofit.Builder()
                        .baseUrl("https://chat.cz")
                        .build().create(ChatService::class.java)

    @Synchronized
    fun getByNick(name: String) = users.find { it.name == name }

    @Synchronized
    fun getById(id: Int): User? {
        var user = users.find { it.id == id }

        if (user == null) {
            user = service.getUserById(id)
            if (user != null) {
                users.add(user)
                return user
            }
        }

        return user
    }

    @Synchronized
    fun addUser(user: User) {
        users.find { it.name == user.name } ?: users.add(user)
    }

    @Synchronized
    fun addUserFromJson(json: String) {
        users.add(mapper.readValue(json, User::class.java))
    }

    fun addUsers(users: List<User>) {
        users.forEach { addUser(it) }
    }

}



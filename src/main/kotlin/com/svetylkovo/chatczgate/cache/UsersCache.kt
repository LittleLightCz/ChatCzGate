package com.svetylkovo.chatczgate.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.svetylkovo.chatczgate.beans.User
import com.svetylkovo.chatczgate.service.ChatService
import java.util.*

object UsersCache {

    private var users = ArrayList<User>()

    private val mapper = ObjectMapper()

    private val service = ChatService()

    @Synchronized
    fun getByName(name: String) = users.find { it.nick == name }

    @Synchronized
    fun getByUid(uid: Int): User? {
        var user = users.find { it.uid == uid }

        if (user == null) {
            user = service.getUserById(uid)
            if (user != null) {
                users.add(user)
                return user
            }
        }

        return user
    }

    @Synchronized
    fun addUser(user: User) {
        users.find { it.nick == user.nick } ?: users.add(user)
    }

    @Synchronized
    fun addUserFromJson(json: String) {
        users.add(mapper.readValue(json, User::class.java))
    }

    fun addUsers(users: List<User>) {
        users.forEach { addUser(it) }
    }

}



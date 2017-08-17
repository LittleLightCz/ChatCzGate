package com.svetylkovo.chatczgate.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.svetylkovo.chatczgate.beans.User
import com.svetylkovo.chatczgate.service.ChatService

object UsersCache {

    private val service = ChatService()

    private val users = Caffeine.newBuilder().build(service::getUserById)

    @Synchronized
    fun getByName(name: String) = users.asMap().values.find { it.nick == name }

    @Synchronized
    fun getByUid(uid: Int): User? = users.get(uid)

}



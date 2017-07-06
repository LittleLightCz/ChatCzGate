package com.svetylkovo.chatczgate.beans

import java.util.*
import kotlin.collections.ArrayList

data class Room
(
        val roomId: Int,
        var name: String,
        var description: String,
        var usersCount: Int = 0,
        var operatorId: Int = -1,
        var admins: List<String> = ArrayList(),
        var chatIndex: String = "",
        var lastMessage: String = "",
        var timestamp: Long = Date().time
) {
    val users = ArrayList<User>()

    fun hasUser(user: User): Boolean {
        return users.find { it.uid == user.uid } != null
    }

    fun addUser(user: User) {
        users.add(user)
    }

    fun removeUser(user: User) {
        users.removeIf { it.uid == user.uid }
    }

    fun getUserByName(name: String) = users.find { it.nick == name }

    fun getUserByUid(uid: Int) = users.find { it.uid == uid }

}

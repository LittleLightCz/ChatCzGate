package com.svetylkovo.chatczgate.beans

import java.util.*

data class Room
(
        val roomId: Int,
        var name: String,
        var description: String,
        var usersCount: Int = 0,
        var operatorId: Int = -1,
        var admins: List<String>,
        var chatIndex: Int,
        var lastMessage: String = "",
        var timestamp: Long = Date().time
) {
    private var users = ArrayList<User>()

    fun hasUser(user: User): Boolean {
        return users.find { it.id == user.id } != null
    }

    fun addUser(user: User) {
        users.add(user)
    }

    fun removeUser(user: User) {
        users.removeIf { it.id == user.id }
    }

    fun getUserByName(name: String) = users.find { it.name == name }

}

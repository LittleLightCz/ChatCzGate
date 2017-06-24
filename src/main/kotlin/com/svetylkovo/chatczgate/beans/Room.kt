package com.svetylkovo.chatczgate.beans

import java.util.*

data class Room
(
        val id: Int,
        var name: String,
        var description: String,
        var usersCount: Int = 0,
        var operatorId: Int = -1,
        var admins: List<String>,
        var chatIndex: Int,
        var lastMessage: String = "",
        var timestamp: Long = Date().time
) {
    private var users = mutableListOf<User>()

    fun hasUser(user: User): Boolean {
        return findUser(user) != null
    }

    fun addUser(user: User) {
        users.add(user)
    }

    fun removeUser(user: User) {
        val foundUser = findUser(user)
        users.remove(foundUser)
    }

    private fun findUser(user: User) = findUserById(user.id)

    private fun findUserById(id: Int) = users.find { it.id == id }

}

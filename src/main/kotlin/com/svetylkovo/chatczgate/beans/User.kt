package com.svetylkovo.chatczgate.beans


data class User(
        val id: Int,
        var name: String,
        var gender: Gender,
        var anonymous: Boolean,
        var idle: Int,
        var adminId: Int,
        var karma: Int
)




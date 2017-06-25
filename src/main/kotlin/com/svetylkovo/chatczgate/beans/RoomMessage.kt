package com.svetylkovo.chatczgate.beans


data class RoomMessage(
        val s: String?,
        val t: String,
        val w: String?,
        val uid: Int,
        val to: Int,
        val user: User,
        val nick: String
) {

}
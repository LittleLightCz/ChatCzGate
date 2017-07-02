package com.svetylkovo.chatczgate.beans


data class User(
        var uid: Int,
        var nick: String,
        var gender: Gender,
        var anonymous: Boolean,
        var idle: Int,
        var adminId: Int,
        var karma: Int
) {
    var online: Boolean? = null
    var rooms: List<String>? = null

    fun getUrl() = "https://chat.cz/p/$nick"
}




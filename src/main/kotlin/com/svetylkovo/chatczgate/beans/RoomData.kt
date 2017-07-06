package com.svetylkovo.chatczgate.beans

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties
class RoomData {
    val index: String = ""
    val data: List<RoomMessage> = emptyList()
    val user: List<User> = emptyList()
}
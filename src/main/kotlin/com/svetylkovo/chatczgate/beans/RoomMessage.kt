package com.svetylkovo.chatczgate.beans

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties
class RoomMessage {
    var uid: Int = 0

    var r: String = ""
    var s: String = ""
    var t: String = ""
    var ts: Long = 0
    var w: String? = null

    var smile: List<String> = emptyList()

    var to: Int = 0
    var user: User? = null
    var nick: String = ""
}
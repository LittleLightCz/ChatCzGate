package com.svetylkovo.chatczgate.beans

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class RoomMessage {
    var uid: Int = 0

    var r: String = ""
    var s: String? = null
    var t: String = ""
    var ts: Long = 0
    var w: String? = null

    var to: Int = 0
    var user: User? = null
    var nick: String = ""
}
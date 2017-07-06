package com.svetylkovo.chatczgate.beans

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class User {
    @JsonProperty("id")
    var uid: Int = 0

    var nick: String = ""

    @JsonProperty("sex")
    var gender: Gender = Gender.MALE

    @JsonProperty("anonym")
    var anonymous: Boolean = true

    var idle: Int = 0
    var adminId: Int? = null
    var karma: Int = 0
    var timeAccess: String = "Unknown"
    var friend: Int = 0

    val profileUrl
        get() = "https://chat.cz/p/$nick"

    var online: Boolean? = null
    var rooms: List<String>? = null

    fun getUrl() = "https://chat.cz/p/$nick"
}




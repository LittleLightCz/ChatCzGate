package com.svetylkovo.chatczgate.beans

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class RestResponse {
    var status: Int = 0
    var statusMessage: String = ""

    var profile: Profile? = null
    var user: User? = null
    var users: List<User>? = null
    var admins: List<String>? = null
    var data: RoomData? = null

    var room: RoomInfo? = null
    val rooms: List<RoomsListItem>? = null
}

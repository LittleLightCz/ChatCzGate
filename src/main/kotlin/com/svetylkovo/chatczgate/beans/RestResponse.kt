package com.svetylkovo.chatczgate.beans


class RestResponse {
    var status: Int = 0
    var statusMessage: String = ""

    var profile: Profile? = null
    var user: User? = null
    var users: List<User>? = null
    var admins: List<String>? = null
    var data: RoomData? = null

    //TODO check
    var room: RoomInfo? = null
    val rooms: List<RoomsListItem>? = null
}

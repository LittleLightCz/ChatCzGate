package com.svetylkovo.chatczgate.beans

class RoomsListItem {
    private var adminUserId: Int = 0
    private var description: String = ""
    private var id: Int = 0
    var locked: String = ""
    var name: String = ""
    var permanent: Boolean = false
    private var userCount: Int = 0
    var web: String = ""

    fun toRoom() = Room(
            roomId = id,
            name = name,
            description = description,
            usersCount = userCount,
            operatorId = adminUserId
    )
}
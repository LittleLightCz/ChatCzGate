package com.svetylkovo.chatczgate.beans

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class RoomInfo {
    var adminUserId: String? = null
    var description: String? = null
    var operatorId: Int? = null
}
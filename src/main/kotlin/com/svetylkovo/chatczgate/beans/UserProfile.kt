package com.svetylkovo.chatczgate.beans

import java.util.*

data class UserProfile(
        var online: Boolean = false,
        var nick: String = "",
        var age: String = "",
        var anonymous: Boolean = true,
        var gender: Gender = Gender.MALE,
        var viewed: String = ""
) {
    var profile_image: String? = null
    var profile_url: String? = null
    var rooms = ArrayList<Room>()
}

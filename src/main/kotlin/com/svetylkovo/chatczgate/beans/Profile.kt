package com.svetylkovo.chatczgate.beans

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties
class Profile {
    var age: String? = null
    var profileViewCount: String? = null
    var imageUrl: String? = null
    var karmaLevel: Int? = null
}

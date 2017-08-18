package com.svetylkovo.chatczgate.beans

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class StoredMessagesUsers {

    @JsonProperty("data")
    var users: List<User>? = null
}
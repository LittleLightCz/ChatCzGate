package com.svetylkovo.chatczgate.beans

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class StoredMessages {

    @JsonProperty("data")
    var storedMessages: List<StoredMessage>? = null
}


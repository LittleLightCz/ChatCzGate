package com.svetylkovo.chatczgate.beans

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class ChatHeaderData {

    @JsonProperty("msg_count")
    var msgCount = 0

    @JsonProperty("request_count")
    var requestCount = 0
}
package com.svetylkovo.chatczgate.beans

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.text.SimpleDateFormat
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
class StoredMessage {
    val TIME_CREATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz"

    @JsonProperty("from_lo")
    var fromYourself = true

    @JsonProperty("user_id_hi")
    var userFromUid: Int? = null

    @JsonProperty("time_create")
    var timeCreate: String? = null

    @JsonProperty("whisp")
    var text = ""

    val date: Date by lazy {
        if (timeCreate != null) {
            SimpleDateFormat(TIME_CREATE_FORMAT, Locale.US).parse(timeCreate)
        } else {
            Date(0)
        }
    }
}
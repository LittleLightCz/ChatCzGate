package com.svetylkovo.chatczgate.beans

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.annotation.JsonCreator



enum class Gender(val value: String) {
    MALE("m"),
    FEMALE("f");

    @JsonCreator
    fun forValue(value: String) = Gender.valueOf(value)

    @JsonValue
    fun toValue() = value
}



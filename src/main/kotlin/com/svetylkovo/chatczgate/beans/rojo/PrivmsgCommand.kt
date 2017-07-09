package com.svetylkovo.chatczgate.beans.rojo

import com.svetylkovo.rojo.annotations.Group
import com.svetylkovo.rojo.annotations.Regex

@Regex("(.+?)\\s*:(.*)")
data class PrivmsgCommand(
        @Group(1)
        var target: String = "",
        @Group(2)
        var message: String = ""
)
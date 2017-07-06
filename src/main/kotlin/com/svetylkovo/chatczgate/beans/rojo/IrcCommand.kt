package com.svetylkovo.chatczgate.beans.rojo

import com.svetylkovo.rojo.annotations.Group
import com.svetylkovo.rojo.annotations.Regex

@Regex("(^\\w+)\\s*(.*)")
data class IrcCommand(
        @Group(1)
        var command: String = "",
        @Group(2)
        var args: String = ""
)
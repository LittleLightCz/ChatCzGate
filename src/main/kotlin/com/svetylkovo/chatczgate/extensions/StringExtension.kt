package com.svetylkovo.chatczgate.extensions


private val UNICODE_SPACE = "\u00A0"

fun String.toWhitespace() = this.replace(" ", UNICODE_SPACE)

fun String.fromWhitespace() = this.replace(UNICODE_SPACE, " ")

package com.svetylkovo.chatczgate.extensions

import org.apache.commons.lang3.StringEscapeUtils


private val UNICODE_SPACE = StringEscapeUtils.unescapeJava("\\xa0")

fun String.toWhitespace() = this.replace(" ", UNICODE_SPACE)

fun String.fromWhitespace() = this.replace(UNICODE_SPACE, " ")

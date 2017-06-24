package com.svetylkovo.chatczgate.events

import com.svetylkovo.chatczgate.beans.Room
import com.svetylkovo.chatczgate.beans.User


interface ChatEvent {

    fun newMessage(room: Room, user: User, text: String, whisper: Boolean)

    fun userJoined(room: User, user: User)

    fun userLeft(room: User, user: User)

    fun systemMessage(room: User, message: String)

    fun userMode(room: Room, user: User, mode: String)

    fun kicked(room: Room)
}
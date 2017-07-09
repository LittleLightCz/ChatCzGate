package com.svetylkovo.chatczgate.events

import com.svetylkovo.chatczgate.beans.Room
import com.svetylkovo.chatczgate.beans.User


interface ChatEvent {

    fun newMessage(room: Room, user: User, text: String, whisper: Boolean)

    fun userJoined(room: Room, user: User)

    fun userLeft(room: Room, user: User)

    fun systemMessage(room: Room, message: String)

    fun userMode(room: Room, user: User, mode: String)

    fun kicked(room: Room)
}
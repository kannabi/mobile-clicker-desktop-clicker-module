package com.kannabi.clicker.desktop.clicker.poko

import com.fasterxml.jackson.annotation.JsonValue

enum class ClickerHeader(@get:JsonValue val header: String) {
    CONNECT("CONNECT"),
    DISCONNECT("DISCONNECT"),
    OK("OK"),
    SWITCH_PAGE("SWITCH_PAGE"),
    UPDATE_META("UPDATE_META")
}
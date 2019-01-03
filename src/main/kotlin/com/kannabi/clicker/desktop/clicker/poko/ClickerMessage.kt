package com.kannabi.clicker.desktop.clicker.poko

data class ClickerMessage(
    var header: ClickerHeader,
    var body: String,
    var features: MutableMap<String, String>
)


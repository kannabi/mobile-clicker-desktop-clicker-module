package com.kannabi.clicker.desktop.clicker.poko

sealed class ClickerEvent

object ConnectionOpen : ClickerEvent()

object ConnectionClose : ClickerEvent()

object ClickerBroken : ClickerEvent()

data class PageSwitch(val page: Int): ClickerEvent()
package com.kannabi.clicker.desktop.clicker

import io.reactivex.Observable

interface MobileConnectionListener {
    fun startListening(): Observable<MobileClicker>

    fun stopListening()
}
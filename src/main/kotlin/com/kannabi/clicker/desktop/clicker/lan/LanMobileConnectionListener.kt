package com.kannabi.clicker.desktop.clicker.lan

import com.kannabi.clicker.desktop.clicker.MobileClicker
import com.kannabi.clicker.desktop.clicker.MobileConnectionListener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.kannabi.clicker.desktop.clicker.poko.ClickerHeader
import com.kannabi.clicker.desktop.clicker.poko.ClickerMessage
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Observable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LanMobileConnectionListener(
    private val logger: Logger = LoggerFactory.getLogger(LanMobileConnectionListener::class.java.simpleName)
) : MobileConnectionListener {

    private var broadcastPort = 8841
    private lateinit var onSubscribeUdpBroadcast: OnSubscribeUdpListener
    private val objectMapper = ObjectMapper().registerModule(KotlinModule())

    override fun stopListening() {
        onSubscribeUdpBroadcast.stop()
    }

    override fun startListening(): Observable<MobileClicker> =
        Flowable.create(
            OnSubscribeUdpListener(broadcastPort).also(::onSubscribeUdpBroadcast::set),
            BackpressureStrategy.LATEST
        )
        .doOnNext { logger.info("connection listener receive ${String(it.data)}") }
        .map {
            objectMapper.readValue(String(it.data), ClickerMessage::class.java)
                .apply {
                    features["address"] = it.address.hostAddress
                }
        }
        .retry()
        .distinctUntilChanged()
        .filter { it.header == ClickerHeader.CONNECT }
        .map {
            LanMobileClicker(
                it.features["address"]!!, it.features["port"]?.toInt()!!, it.body
            ) as MobileClicker
        }
        .retry()
        .toObservable()
}
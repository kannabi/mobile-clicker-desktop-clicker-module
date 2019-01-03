package com.kannabi.clicker.desktop.clicker.lan

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.kannabi.clicker.desktop.clicker.MobileClicker
import com.kannabi.clicker.desktop.clicker.poko.*
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException

class LanMobileClicker(
    var inetAddress: String,
    var port: Int,
    var clickerName: String,
    private val logger: Logger = LoggerFactory.getLogger(LanMobileClicker::class.java.simpleName)
) : MobileClicker {

    private var clickerPort = 17710
    private val objectMapper by lazy { jacksonObjectMapper() }
    private lateinit var rxSocketWrapper: RxSocketWrapper

    private val eventsSubject = BehaviorSubject.create<ClickerEvent>()

    private val compositeDisposable = CompositeDisposable()

    override fun init(maxPage: Int, sessionId: String): Observable<ClickerEvent> {
        val udpPoller = UdpPoller()
        val id = udpPoller.poll(
            getMessage(
                ClickerHeader.OK,
                "$clickerPort",
                mutableMapOf(
                    "sessionId" to sessionId,
                    "maxPage" to maxPage.toString()
                )
            ).toByteArray(),
            port,
            InetAddress.getByName(inetAddress)
        )

        ServerSocket().use {
            it.reuseAddress = true
            it.bind(InetSocketAddress(clickerPort))
            rxSocketWrapper = RxSocketWrapper(it.accept())
        }

        subscribeToSocketData(rxSocketWrapper.inputObservable)
        udpPoller.remove(id)
        udpPoller.clear()

        eventsSubject.onNext(ConnectionOpen)
        return eventsSubject.hide()
    }

    override fun getName() = clickerName

    private fun subscribeToSocketData(inputObservable: Observable<String>) {
        compositeDisposable.add(
            inputObservable
                .doOnNext { logger.info("Receive from socket input $it") }
                .map { objectMapper.readValue(it, ClickerMessage::class.java) }
                .retry()
                .subscribeOn(Schedulers.io())
                .subscribe(::processClickerMessage, { disconnect() }, ::waitForReconnect)
        )
    }

    private fun processClickerMessage(clickerMessage: ClickerMessage) {
        when (clickerMessage.header) {
            ClickerHeader.SWITCH_PAGE -> eventsSubject.onNext(PageSwitch(clickerMessage.body.toInt()))
            ClickerHeader.DISCONNECT -> disconnect()
            else -> Unit
        }
    }

    private fun waitForReconnect() {
        rxSocketWrapper.close()
        try {
            ServerSocket().use {
                it.reuseAddress = true
                it.bind(InetSocketAddress(clickerPort))
                it.soTimeout = 15000
                rxSocketWrapper = RxSocketWrapper(it.accept())
                subscribeToSocketData(rxSocketWrapper.inputObservable)
                eventsSubject.onNext(ConnectionOpen)
            }
        } catch (timeoutException: SocketTimeoutException) {
            disconnect()
        }
    }

    override fun switchToPage(pageNumber: Int) {
        rxSocketWrapper.sendData(
            getMessage(ClickerHeader.SWITCH_PAGE, pageNumber.toString(), mutableMapOf())
        )
    }

    override fun updateMeta(maxPage: Int, tinySlides: Map<String, String>) {
        rxSocketWrapper.sendData(
            getMessage(
                ClickerHeader.UPDATE_META, "",
                mutableMapOf<String, String>().apply {
                    put("maxPage", maxPage.toString())
                    putAll(tinySlides)
                }
            )
        )
    }

    private fun getMessage(header: ClickerHeader, body: String, features: MutableMap<String, String>) =
        objectMapper.writeValueAsString(ClickerMessage(header, body, features))

    override fun disconnect() {
        rxSocketWrapper.close()
        compositeDisposable.clear()
        eventsSubject.onNext(ConnectionClose)
    }
}
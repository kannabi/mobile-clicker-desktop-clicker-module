package com.kannabi.clicker.desktop.clicker.lan

import io.reactivex.FlowableEmitter
import io.reactivex.FlowableOnSubscribe
import io.reactivex.disposables.Disposables
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean


internal class OnSubscribeUdpListener(
    private val port: Int,
    private val logger: Logger = LoggerFactory.getLogger(OnSubscribeUdpListener::class.java.simpleName)
) : FlowableOnSubscribe<DatagramPacket> {
    private val emitters = ConcurrentLinkedQueue<FlowableEmitter<DatagramPacket>>()

    private var socket: DatagramSocket? = null
    private val isListening = AtomicBoolean(false)
    private val lock = Any()

    override fun subscribe(emitter: FlowableEmitter<DatagramPacket>) {
        synchronized(lock) {
            if (!emitter.isCancelled) {
                if (emitters.isEmpty()) {
                    socket = getSocket()
                }
                emitters.add(emitter)

                emitter.setDisposable(Disposables.fromAction {
                    emitters.remove(emitter)
                    if (emitters.isEmpty()) {
                        isListening.set(false)
                    }
                })
            }
        }

        if (!isListening.get()) {
            startListening()
        }
    }

    private fun startListening() {
        isListening.set(true)
        val receiveData = ByteArray(2048)
        var datagramPacket = DatagramPacket(receiveData, receiveData.size)
        while (isListening.get()) {
            try {
                socket?.receive(datagramPacket)
            } catch (e: SocketException) {
                emitters.forEach { it.onComplete() }
            } catch (e: Throwable) {
                logger.trace("Something went wrong in OnSubscribeUdpListener", e)
                emitError(e)
            }
            emit(datagramPacket)
            datagramPacket = DatagramPacket(receiveData, receiveData.size)
        }
    }

    private fun emit(datagramPacket: DatagramPacket) {
        for (emitter in emitters) {
            emitter.onNext(datagramPacket)
        }
    }

    private fun emitError(throwable: Throwable) {
        for (emitter in emitters) {
            emitter.onError(throwable)
        }
    }

    fun stop() {
        isListening.set(false)
        socket?.close()
        socket = null
    }

    private fun getSocket() =
        DatagramSocket(port).apply {
            broadcast = true
            reuseAddress = true
        }
}
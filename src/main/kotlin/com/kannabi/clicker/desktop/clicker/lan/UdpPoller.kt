package com.kannabi.clicker.desktop.clicker.lan

import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong

internal class UdpPoller(
    private val logger: Logger = LoggerFactory.getLogger(UdpPoller::class.java.simpleName)
) {
    private val socket = DatagramSocket()
    private val lastId = AtomicLong(-1)
    private val messageMap: ConcurrentMap<Long, DatagramPacket> = ConcurrentHashMap()
    private var pollerDisposable: Disposable? = null

    fun poll(message: ByteArray, port: Int, address: InetAddress): Long {
        val id = lastId.incrementAndGet()
        messageMap[id] = DatagramPacket(message, message.size, address, port)

        if (messageMap.size == 1) {
            pollerDisposable =
                Single.fromCallable {
                    while (messageMap.isNotEmpty()) {
                        messageMap.forEach { _, packet ->
                            socket.send(packet)
                        }
                        Thread.sleep(500)
                    }
                }.subscribeOn(Schedulers.io()).subscribe({
                    pollerDisposable?.dispose()
                    pollerDisposable = null
                }, {
                    logger.trace("", it)
                })
        }
        return id
    }

    fun remove(id: Long){
        messageMap.remove(id)
    }

    fun clear() {
        messageMap.clear()
        socket.close()
    }
}
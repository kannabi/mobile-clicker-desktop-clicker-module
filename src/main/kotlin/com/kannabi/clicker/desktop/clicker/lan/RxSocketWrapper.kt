package com.kannabi.clicker.desktop.clicker.lan

import io.reactivex.BackpressureStrategy
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.Socket
import java.net.SocketException

internal class RxSocketWrapper(
    private val socket: Socket,
    private var bufferSize: Int = 2048,
    private val logger: Logger = LoggerFactory.getLogger(RxSocketWrapper::class.java.simpleName)
) : Closeable {

    private val MESSAGE_END: ByteArray = byteArrayOf(-1)
    private val EMPTY_BYTE: Byte = -2

    private val compositeDisposable = CompositeDisposable()

    private val outputStream = socket.getOutputStream()
    private val inputStream = socket.getInputStream()

    private val lock = Any()
    private val dataSubject = PublishSubject.create<String>()
        get() = synchronized(lock) { return field }

    val inputObservable: Observable<String> by lazy {
        compositeDisposable.add(
            Completable.fromCallable {
                try {
                    val data = ByteArray(bufferSize)
                    data.fill(EMPTY_BYTE)
                    while (inputStream.read(data) != -1) {
                        processData(data)
                        data.fill(EMPTY_BYTE)
                    }
                } catch (e: SocketException) {
                    dataSubject.onComplete()
                } catch (e: Exception) {
                    dataSubject.onError(e)
                }
            }
            .subscribeOn(Schedulers.io())
            .subscribe(dataSubject::onComplete, dataSubject::onError)
        )

        return@lazy dataSubject.hide().observeOn(Schedulers.io())
    }

    private val stringBuilder = StringBuilder()

    private fun processData(data: ByteArray) {
        var previousIndex = 0
        data.forEachIndexed { i, it ->
            when (it) {
                MESSAGE_END[0] -> {
                    stringBuilder.append(String(data, previousIndex, i - previousIndex))
                    dataSubject.onNext(stringBuilder.toString())
                    stringBuilder.setLength(0)
                    previousIndex = i + 1
                }
                EMPTY_BYTE -> previousIndex = i
            }
        }
        if (previousIndex != data.lastIndex) {
            stringBuilder.append(String(data, previousIndex, bufferSize - previousIndex))
        }
    }

    private var sendSubject = PublishSubject.create<String>()
    private var sendDisposable: Disposable? = null


    fun sendData(data: String, completeCallback: (() -> Unit)? = null) {
        if (sendDisposable == null) {
            sendDisposable =
                    sendSubject.hide().toFlowable(BackpressureStrategy.BUFFER)
                        .observeOn(Schedulers.io())
                        .subscribe({
                            synchronized(outputStream) {
                                outputStream.write(it.toByteArray().plus(MESSAGE_END[0]))
                                outputStream.flush()
                            }
                            completeCallback?.invoke()
                        }, {
                            logger.trace("", it)
                        })
        }

        sendSubject.onNext(data)
    }

    override fun close() {
        inputStream.close()
        outputStream.close()
        socket.close()
        compositeDisposable.clear()
    }
}
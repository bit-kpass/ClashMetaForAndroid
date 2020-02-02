package com.github.kr328.clash.core

import android.content.Context
import bridge.Bridge
import bridge.EventPoll
import com.github.kr328.clash.core.event.EventStream
import com.github.kr328.clash.core.event.LogEvent
import com.github.kr328.clash.core.model.General
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.Traffic
import com.github.kr328.clash.core.transact.DoneCallbackImpl
import com.github.kr328.clash.core.transact.ProxyCollectionImpl
import com.github.kr328.clash.core.transact.ProxyGroupCollectionImpl
import java.io.File
import java.io.InputStream
import java.util.concurrent.CompletableFuture

object Clash {
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized)
            return
        initialized = true

        val bytes = context.assets.open("Country.mmdb")
            .use(InputStream::readBytes)

        Bridge.loadMMDB(bytes)
    }

    fun start() {
        Bridge.reset()
    }

    fun stop() {
        Bridge.reset()
    }

    fun startTunDevice(
        fd: Int,
        mtu: Int,
        dns: String
    ) {
        Bridge.startTunDevice(fd.toLong(), mtu.toLong(), dns)
    }

    fun stopTunDevice() {
        Bridge.stopTunDevice()
    }

    fun loadProfile(path: File, baseDir: File): CompletableFuture<Unit> {
        return DoneCallbackImpl().apply {
            Bridge.loadProfileFile(path.absolutePath, baseDir.absolutePath, this)
        }
    }

    fun downloadProfile(url: String, output: File, baseDir: File) {
        Bridge.downloadProfileAndCheck(url, output.absolutePath, baseDir.absolutePath)
    }

    fun copyProfile(fd: Int, output: File, baseDir: File) {
        Bridge.readProfileAndCheck(fd.toLong(), output.absolutePath, baseDir.absolutePath)
    }

    fun saveProfile(data: ByteArray, output: File, baseDir: File) {
        Bridge.saveProfileAndCheck(data, output.absolutePath, baseDir.absolutePath)
    }

    fun moveProfile(source: File, target: File, baseDir: File) {
        Bridge.moveProfileAndCheck(source.absolutePath, target.absolutePath, baseDir.absolutePath)
    }

    fun queryProxyGroups(): List<ProxyGroup> {
        return ProxyGroupCollectionImpl().also { Bridge.queryAllProxyGroups(it) }
            .filterNotNull()
            .map { group ->
                ProxyGroup(group.name,
                    Proxy.Type.fromString(group.type),
                    group.delay,
                    group.current,
                    ProxyCollectionImpl().also { pc ->
                        group.queryAllProxies(pc)
                    }.filterNotNull().map {
                        Proxy(it.name, Proxy.Type.fromString(it.type), it.delay)
                    })
            }
    }

    fun setSelectedProxy(name: String, selected: String): Boolean {
        return Bridge.setSelectedProxy(name, selected)
    }

    fun startHealthCheck(name: String): CompletableFuture<Unit> {
        return DoneCallbackImpl().apply {
            Bridge.startUrlTest(name, this)
        }
    }

    fun queryGeneral(): General {
        val t = Bridge.queryGeneral()

        return General(
            General.Mode.fromString(t.mode),
            t.httpPort.toInt(), t.socksPort.toInt(), t.redirectPort.toInt()
        )
    }

    fun queryTrafficEvent(): Traffic {
        val data = Bridge.queryTraffic()

        return Traffic(data.upload, data.download)
    }

    fun queryBandwidth(): Long {
        return Bridge.queryBandwidth()
    }

    fun openLogEvent(): EventStream<LogEvent> {
        return object : EventStream<LogEvent>() {
            val log = Bridge.pollLogs { level, payload ->
                send(LogEvent(LogEvent.Level.fromString(level), payload))
            }

            override fun onClose() {
                log.stop()
            }
        }
    }
}
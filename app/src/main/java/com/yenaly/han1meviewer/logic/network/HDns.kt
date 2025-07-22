package com.yenaly.han1meviewer.logic.network

import android.util.Log
import android.widget.Toast
import com.yenaly.han1meviewer.HANIME_ALTER_HOSTNAME
import com.yenaly.han1meviewer.HANIME_MAIN_HOSTNAME
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.yenaly_libs.utils.showShortToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Dns
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2024/03/10 010 17:01
 */
class HDns : Dns {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val dnsMap = mutableMapOf<String, List<InetAddress>>()
    private var sortedIpsCache = emptyList<InetAddress>()

    private val useBuiltInHosts = Preferences.useBuiltInHosts
    private val isPingTest = Preferences.isPingTest

    init {
        if (useBuiltInHosts) {
            dnsMap[HANIME_MAIN_HOSTNAME] = listOf(
                "104.25.254.167", "172.67.75.184", "172.64.229.154",
                "2606:4700:8dd1::2a46:47f8"
            )
            dnsMap[HANIME_ALTER_HOSTNAME] = listOf(
                "104.25.254.167", "172.67.75.184", "172.64.229.154",
                "2606:4700:8dd1::2a46:47f8"
            )
        }
    }

    companion object {

        /**
         * 添加DNS
         */
        private operator fun MutableMap<String, List<InetAddress>>.set(
            host: String, ips: List<String>,
        ) {
            this[host] = ips.map {
                InetAddress.getByAddress(host, InetAddress.getByName(it).address)
            }
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        if (sortedIpsCache.isNotEmpty()) return sortedIpsCache
        val dns = dnsMap[hostname]
        if (!useBuiltInHosts || dns.isNullOrEmpty()) return Dns.SYSTEM.lookup(hostname)
        if (!isPingTest) return dns
        CoroutineScope(Dispatchers.Main).launch {
            showShortToast(R.string.ping_test)
        }
        val ipLatencies = runBlocking {
            dns.map { ip ->
                scope.async {
                    ip to pingTest(ip.hostAddress!!)
                }
            }.awaitAll()
        }
        val sortedIps = ipLatencies
            .sortedBy { (_, latency) -> latency }
            .map { (ip, _) -> ip }
        this.sortedIpsCache = sortedIps
        return sortedIps
    }


    private suspend fun pingTest(ip: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, 80), 300)
                }
                System.currentTimeMillis() - startTime
            } catch (e: Exception) {
                Long.MAX_VALUE
            }
        }
    }
}
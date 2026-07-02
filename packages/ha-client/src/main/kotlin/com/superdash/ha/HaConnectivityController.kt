package com.superdash.ha

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.superdash.core.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val log = Log("HaConnectivityController")

/** Manages the lifecycle of the Home Assistant WebSocket connection.
 *  Started from AppWiring; stopped on application termination. */
class HaConnectivityController(
    private val context: Context,
    private val haClient: HaWebSocketClient,
    private val tokenStore: HaTokenStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var tokenJob: Job? = null

    fun start() {
        log.i("starting connection manager")

        if (tokenJob?.isActive != true) {
            tokenJob =
                scope.launch {
                    tokenStore.tokensFlow.collect { tokens ->
                        if (tokens != null) {
                            haClient.connect()
                        } else {
                            haClient.disconnect()
                        }
                    }
                }
        }
        installNetworkReconnect()
    }

    private fun installNetworkReconnect() {
        if (networkCallback != null) {
            return
        }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val cb =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    log.i("network available, force reconnect")
                    haClient.forceReconnect()
                }
            }
        cm.registerDefaultNetworkCallback(cb)
        networkCallback = cb
    }

    fun stop() {
        log.i("stopping connection manager")
        tokenJob?.cancel()
        tokenJob = null
        haClient.disconnect()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        networkCallback?.let { cm?.unregisterNetworkCallback(it) }
        networkCallback = null
    }
}

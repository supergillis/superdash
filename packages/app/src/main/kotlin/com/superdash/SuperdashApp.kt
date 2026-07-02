package com.superdash

import android.app.Application
import com.superdash.core.log.Log

private val log = Log("SuperdashApp")

class SuperdashApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        log.i("onCreate")
        graph.haConnectivityController.start()
        graph.esphome.start()
        graph.doorbellWatcher.start()
        graph.screenStateProvider.start()
    }

    // NOTE: Application.onTerminate only fires in the emulator, so on a real
    // device this never runs. We accept that tradeoff: there is no reliable
    // "process is about to be killed" signal on Android, and the previous
    // ProcessLifecycleOwner.onStop wiring (commit bc3e8c8c) had two fatal
    // problems: (1) onStop runs on the main thread and AppGraph.shutdown
    // blocks on suspending STT engine close, freezing the UI for seconds,
    // and (2) onStop fires on every backgrounding (Recents/home), not just
    // process death — permanently destroying ttsPlayer with no re-create.
    // Process exit reclaims all native resources, so leaking on shutdown
    // is harmless in practice.
    override fun onTerminate() {
        log.i("onTerminate")
        graph.shutdown()
        super.onTerminate()
    }
}

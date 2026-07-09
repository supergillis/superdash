package com.superdash

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.superdash.core.log.Log
import com.superdash.ha.HaOAuthInterceptor
import com.superdash.ha.JsBridge
import com.superdash.ha.exchangeAndSaveAuthCode
import com.superdash.kiosk.BatteryOptimizationPrompt
import com.superdash.kiosk.KioskService
import com.superdash.kiosk.KioskWindowController
import com.superdash.kiosk.SidebarAction
import com.superdash.kiosk.SidebarShortcut
import com.superdash.kiosk.boot.BootLauncher
import com.superdash.kiosk.bus.ActivityCommand
import com.superdash.kiosk.bus.ActivityCommandQueue
import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import com.superdash.kiosk.emitsUserTouchedFromSidebar
import com.superdash.kiosk.touchesIdleFromSidebar
import com.superdash.kiosk.ui.AppState
import com.superdash.kiosk.ui.FirstRunForm
import com.superdash.kiosk.ui.KioskOverlayState
import com.superdash.kiosk.ui.KioskOverlays
import com.superdash.kiosk.ui.KioskWebView
import com.superdash.kiosk.ui.SidebarRailLayout
import com.superdash.kiosk.ui.VoiceOverlay
import com.superdash.screensaver.ScreensaverHost
import com.superdash.settings.SettingsActivity
import com.superdash.theme.SuperdashTheme
import com.superdash.voice.VoiceService
import kotlinx.coroutines.launch

private val log = Log("MainActivity")

class MainActivity : AppCompatActivity() {
    private lateinit var graph: AppGraph
    private lateinit var eventBus: KioskEventBus
    private lateinit var kioskWindow: KioskWindowController
    private val jsBridge = JsBridge()
    private lateinit var oauthInterceptor: HaOAuthInterceptor
    private lateinit var tapDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.i("onCreate")
        val app = application as SuperdashApp
        graph = app.graph
        eventBus = graph.eventBus
        val settings = graph.settings
        kioskWindow = KioskWindowController(this, settings)
        tapDetector =
            GestureDetector(
                this,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        graph.idleController.touch()
                        return false
                    }

                    override fun onLongPress(e: MotionEvent) {
                        graph.idleController.touch()
                    }
                },
                null,
            )

        oauthInterceptor =
            HaOAuthInterceptor(
                haBaseUrl = {
                    graph.haUrlFlow.value
                        ?.takeIf { it.isNotBlank() }
                },
                onAuthCode = { code -> handleAuthCode(app, code) },
            )

        val launchedFromBoot = intent.getBooleanExtra(BootLauncher.EXTRA_LAUNCHED_FROM_BOOT, false)
        lifecycleScope.launch { kioskWindow.apply(launchedFromBoot, settings.snapshot()) }

        KioskService.start(this)

        lifecycleScope.launch { BatteryOptimizationPrompt.maybePromptOnce(this@MainActivity, graph.kioskSettings) }

        installActivityCommandHandler(graph.activityCommandQueue)

        val mainViewModel =
            ViewModelProvider(this, MainViewModel.Factory(graph))[MainViewModel::class.java]
        val haMediaSource = graph.haMediaSource
        val screensaverContent: @Composable () -> Unit = {
            ScreensaverHost(
                settings = graph.screensaverSettings,
                immichAlbumFlow = graph.immichSettings.album,
                immichCatalogTtlHoursFlow = graph.immichSettings.catalogTtlHours,
                haClient = graph.haClient,
                haMediaSource = haMediaSource,
                immichClient = graph.immichClient,
                immichCatalogStore = graph.immich.catalogStore,
                onImmichSourceCreated = { graph.immich.registerSource(it) },
                imageLoader = graph.imageLoader,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val bearerTokenProvider: suspend () -> String? = graph.tokenStore::loadAccessToken
        val fetchHlsUrl: suspend (String) -> String = { entity -> graph.cameraStreamSource.fetchHlsUrl(entity) }
        val onCloseDoorbell: () -> Unit = { graph.doorbellOverlayController.close() }
        val onCancelVoice: () -> Unit = graph.voiceCoordinator::stopAll

        setContent {
            SuperdashTheme {
                MainScreen(
                    viewModel = mainViewModel,
                    oauthInterceptor = oauthInterceptor,
                    bridge = jsBridge,
                    screensaverContent = screensaverContent,
                    bearerTokenProvider = bearerTokenProvider,
                    fetchHlsUrl = fetchHlsUrl,
                    onCloseDoorbell = onCloseDoorbell,
                    onCancelVoice = onCancelVoice,
                    onVoiceServiceShouldRunChange = { shouldRun ->
                        if (shouldRun) {
                            VoiceService.start(this@MainActivity, shouldRun = shouldRun)
                        } else {
                            VoiceService.stop(this@MainActivity)
                        }
                    },
                    onSubmitUrl = { url ->
                        lifecycleScope.launch {
                            settings.setHaUrl(url)
                            // Clear any stale tokens so submitting the first-run / reauth
                            // form always (re)starts OAuth. Without this, "Sign in again"
                            // in the NeedsReauth state just re-saved the same URL and did
                            // nothing (tokens stayed, state stayed NeedsReauth). Tokens are
                            // already null on first run, so this is a no-op there.
                            graph.tokenStore.clear()
                        }
                    },
                    onSidebarPinnedChange = { value ->
                        lifecycleScope.launch {
                            graph.sidebarSettings.setPinned(value)
                        }
                    },
                    onSidebarShortcut = { shortcut -> handleSidebarShortcut(shortcut) },
                )
            }
        }
    }

    private fun installActivityCommandHandler(queue: ActivityCommandQueue) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                queue.commands.collect { command ->
                    when (command) {
                        is ActivityCommand.RefreshWebView -> jsBridge.send("""{"type":"reload"}""")
                        is ActivityCommand.RestartApp -> restartAppFromActivity()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        graph.idleController.resume()
        val launchedFromBoot = intent.getBooleanExtra(BootLauncher.EXTRA_LAUNCHED_FROM_BOOT, false)
        lifecycleScope.launch {
            kioskWindow.apply(launchedFromBoot, graph.settings.snapshot())
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            // Reset the idle timer on any touch interaction. The GestureDetector
            // only fires on tap/long-press, so swipes and scrolls would otherwise
            // let the screensaver start mid-interaction. ACTION_DOWN begins every
            // gesture, so resetting here covers them all.
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                graph.idleController.touch()
            }
            tapDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        graph.idleController.touch()
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        graph.idleController.pause()
        super.onPause()
    }

    private fun restartAppFromActivity() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            log.w("RestartApp command but getLaunchIntentForPackage returned null")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        finishAffinity()
        startActivity(launchIntent)
    }

    private fun handleSidebarShortcut(shortcut: SidebarShortcut) {
        val action = shortcut.action
        if (action.emitsUserTouchedFromSidebar()) {
            eventBus.emit(KioskEvent.UserTouched)
        }
        if (action.touchesIdleFromSidebar()) {
            graph.idleController.touch()
        }
        when (action) {
            SidebarAction.OpenSettings -> startActivity(Intent(this, SettingsActivity::class.java))
            SidebarAction.ReloadDashboard ->
                lifecycleScope.launch {
                    graph.activityCommandQueue.submit(ActivityCommand.RefreshWebView)
                }
            SidebarAction.ShowScreensaver -> graph.idleController.forceIdle()
            SidebarAction.DismissScreensaver -> Unit
            is SidebarAction.SetNightModeActive ->
                lifecycleScope.launch {
                    graph.sleepController.setNightModeActive(action.active)
                }
            is SidebarAction.OpenDashboardPath ->
                lifecycleScope.launch {
                    graph.kioskSettings.setDashboardPath(action.path)
                }
        }
    }

    private fun handleAuthCode(app: SuperdashApp, code: String) {
        val haUrl = app.graph.haUrlFlow.value ?: return
        lifecycleScope.launch {
            exchangeAndSaveAuthCode(
                httpClient = app.graph.httpClient,
                tokenStore = app.graph.tokenStore,
                haUrl = haUrl,
                code = code,
            )
        }
    }
}

@Composable
private fun MainScreen(
    viewModel: MainViewModel,
    oauthInterceptor: HaOAuthInterceptor,
    bridge: JsBridge,
    screensaverContent: @Composable () -> Unit,
    bearerTokenProvider: suspend () -> String?,
    fetchHlsUrl: suspend (String) -> String,
    onCloseDoorbell: () -> Unit,
    onCancelVoice: () -> Unit,
    onVoiceServiceShouldRunChange: (Boolean) -> Unit,
    onSubmitUrl: (String) -> Unit,
    onSidebarPinnedChange: (Boolean) -> Unit,
    onSidebarShortcut: (SidebarShortcut) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shouldRunVoiceService by viewModel.voiceServiceShouldRun.collectAsStateWithLifecycle()

    LaunchedEffect(shouldRunVoiceService) {
        onVoiceServiceShouldRunChange(shouldRunVoiceService)
    }
    MainContent(
        state = state,
        oauthInterceptor = oauthInterceptor,
        bridge = bridge,
        screensaverContent = screensaverContent,
        bearerTokenProvider = bearerTokenProvider,
        fetchHlsUrl = fetchHlsUrl,
        onCloseDoorbell = onCloseDoorbell,
        onSubmitUrl = onSubmitUrl,
        onCancelVoice = onCancelVoice,
        onSidebarPinnedChange = onSidebarPinnedChange,
        onSidebarShortcut = onSidebarShortcut,
    )
}

@Composable
private fun MainContent(
    state: MainUiState,
    oauthInterceptor: HaOAuthInterceptor,
    bridge: JsBridge,
    screensaverContent: @Composable () -> Unit,
    bearerTokenProvider: suspend () -> String?,
    fetchHlsUrl: suspend (String) -> String,
    onCloseDoorbell: () -> Unit,
    onSubmitUrl: (String) -> Unit,
    onCancelVoice: () -> Unit,
    onSidebarPinnedChange: (Boolean) -> Unit,
    onSidebarShortcut: (SidebarShortcut) -> Unit,
) {
    var sidebarOpen by remember(state.sidebar.position) { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        SidebarRailLayout(
            position = state.sidebar.position,
            pinned = state.sidebar.pinned,
            edgeHandle = state.sidebar.edgeHandle,
            open = sidebarOpen,
            idle = state.isIdle,
            showLabels = state.sidebar.showLabels,
            nightModeActive = state.nightModeActive,
            shortcuts = state.sidebar.shortcuts,
            onOpen = { sidebarOpen = true },
            onDismiss = { sidebarOpen = false },
            onPinnedChange = onSidebarPinnedChange,
            onShortcutClick = { shortcut ->
                onSidebarShortcut(shortcut)
                if (!state.sidebar.pinned) {
                    sidebarOpen = false
                }
            },
            content = {
                MainKioskContent(
                    state = state,
                    oauthInterceptor = oauthInterceptor,
                    bridge = bridge,
                    onSubmitUrl = onSubmitUrl,
                )
            },
            overlays = {
                KioskOverlays(
                    state =
                        KioskOverlayState(
                            doorbellState = state.doorbellState,
                            doorbellAutoCloseSec = state.doorbellAutoCloseSec,
                            haBaseUrl = state.haBaseUrl,
                            isIdle = state.isIdle,
                        ),
                    bearerTokenProvider = bearerTokenProvider,
                    fetchHlsUrl = fetchHlsUrl,
                    onCloseDoorbell = onCloseDoorbell,
                    onTapScreensaver = {},
                    screensaverContent = screensaverContent,
                )
                VoiceOverlay(
                    state = state.voiceState,
                    onCancel = onCancelVoice,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }
}

@Composable
private fun MainKioskContent(
    state: MainUiState,
    oauthInterceptor: HaOAuthInterceptor,
    bridge: JsBridge,
    onSubmitUrl: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val appState = state.appState) {
                AppState.Loading -> CircularProgressIndicator()
                AppState.NeedsSetup -> FirstRunForm(onSubmit = onSubmitUrl)
                is AppState.NeedsReauth ->
                    FirstRunForm(
                        onSubmit = onSubmitUrl,
                        initialUrl = appState.haUrl,
                        banner = stringResource(R.string.error_sign_in_failed, appState.reason),
                    )
                is AppState.Configured ->
                    KioskWebView(
                        haUrl = appState.haUrl,
                        dashboardPath = state.dashboardPath,
                        tokens = state.tokens,
                        oauthInterceptor = oauthInterceptor,
                        bridge = bridge,
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        }
    }
}

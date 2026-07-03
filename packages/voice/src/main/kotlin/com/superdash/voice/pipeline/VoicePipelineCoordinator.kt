package com.superdash.voice.pipeline

import com.superdash.core.log.Log
import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.executors.TtsPlay
import com.superdash.voice.stt.RecognitionUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private val log = Log("Coordinator")

private const val WAKE_BLIP_MS = 200L
private const val FAILED_HOLD_MS = 3_000L
private const val ACTION_COMPLETE_HOLD_MS = 3_000L
const val DEFAULT_ASSIST_RUN_TIMEOUT_MS = 60_000L

/** Drives Idle → WakeFired → Recording → Processing → Speaking → Idle.
 *  Pure dependency injection: everything that touches the network or audio
 *  hardware is passed in, so this class is unit-testable with fakes. */
class VoicePipelineCoordinator(
    private val voiceProviderRunner: VoiceProviderRunner,
    private val ttsPlayer: TtsPlay,
    private val bus: KioskEventBus,
    private val responseModes: Flow<VoiceResponseMode> = flowOf(VoiceResponseMode.Speak),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val assistRunTimeoutMs: Long = DEFAULT_ASSIST_RUN_TIMEOUT_MS,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        scope.launch {
            bus.events.filterIsInstance<KioskEvent.DoorbellRingStarted>().collect {
                stopAll()
            }
        }
    }

    private val mutableState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = mutableState.asStateFlow()

    private val mutableRunResults =
        MutableSharedFlow<VoiceRunResult>(
            replay = 16,
            extraBufferCapacity = 16,
        )
    val runResults: SharedFlow<VoiceRunResult> = mutableRunResults.asSharedFlow()

    // Synchronizes the activeJob read/swap across threads: onWake fires from the
    // VoiceCaptureLoop on Dispatchers.IO while stopAll fires from the UI thread.
    // The critical sections never suspend, so a JVM monitor is sufficient and
    // avoids forcing callers into a coroutine just to swap a Job reference.
    private val transitionLock = Any()
    private var activeJob: Job? = null
    private var activeRunId: VoiceRunId? = null
    private var failedRunId: VoiceRunId? = null

    // Generation counter gates state mutations against the active run. Each
    // onWake bumps generation; stopAll resets activeGeneration so any callback
    // from a cancelled job that races past coroutine cancellation becomes a
    // no-op instead of overwriting Idle with Failed.
    private var generation: Long = 0L
    private var activeGeneration: Long = 0L

    /** Queues the wake job and returns it so the caller can tie the command
     *  stream's lifetime to the run: the stream is closed when this job
     *  completes, which reclaims it even when the provider never collects
     *  (e.g. provider-missing) without relying on a wall-clock timeout that
     *  a slow first provider load would trip. */
    fun onWake(context: VoiceRunContext, audio: Flow<ShortArray>): Job {
        log.i("onWake (queueing job)", "word" to context.wakeWord, "runId" to context.id.value)
        bus.emit(KioskEvent.WakeWordDetected(context.wakeWord))
        val ourGeneration =
            synchronized(transitionLock) {
                activeJob?.cancel()
                generation += 1L
                activeGeneration = generation
                activeRunId = context.id
                failedRunId = null
                generation
            }
        val job = scope.launch(start = CoroutineStart.LAZY) { runWakeJob(context, ourGeneration, audio) }
        job.invokeOnCompletion {
            synchronized(transitionLock) {
                if (activeJob === job) {
                    activeJob = null
                    activeRunId = null
                }
            }
        }
        synchronized(transitionLock) {
            activeJob = job
        }
        job.start()
        return job
    }

    fun stopAll() {
        synchronized(transitionLock) {
            activeJob?.cancel()
            activeRunId = null
            activeGeneration = 0L
            generation += 1L
            ttsPlayer.stop()
            mutableState.value = VoiceState.Idle
        }
    }

    private suspend fun runWakeJob(
        context: VoiceRunContext,
        runGeneration: Long,
        audio: Flow<ShortArray>,
    ) {
        try {
            coroutineScope {
                transitionIfActive(runGeneration, "state=WakeFired", "word" to context.wakeWord) {
                    VoiceState.WakeFired(context.wakeWord)
                }
                val wakeBlipJob =
                    launch {
                        delay(WAKE_BLIP_MS)
                        if (mutableState.value is VoiceState.WakeFired) {
                            transitionIfActive(runGeneration, "state=Recording") { VoiceState.Recording() }
                        }
                    }
                try {
                    log.i("starting assistRunner")
                    runAssistWithTimeout(context, runGeneration, audio)
                } finally {
                    wakeBlipJob.cancel()
                }
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                throw throwable
            }
            // Mirror previous behavior: catch broadly so any unexpected failure
            // surfaces a Failed state. AssistErrorException and TimeoutCancellation
            // are already handled inside runAssistWithTimeout and never propagate here.
            log.w("pipeline failed", throwable)
            val reason = throwable.message ?: "unknown"
            failAndEmit(context, runGeneration, reason, emptyList())
        }
    }

    /** Drives the assist event flow until it completes, errors, or times out.
     *  TimeoutCancellationException and AssistErrorException are caught here so
     *  the post-error Failed→Idle hold is not part of the timed region. */
    private suspend fun runAssistWithTimeout(
        context: VoiceRunContext,
        runGeneration: Long,
        audio: Flow<ShortArray>,
    ) {
        var providerTrace: List<VoiceProviderAttempt> = emptyList()
        var ttsTerminalState: VoiceRunTerminalState? = null
        var lastTranscript = ""
        var sawActionComplete = false
        try {
            // 60s ceiling against a wedged pipeline that never sends RunEnd/Error.
            withTimeout(assistRunTimeoutMs) {
                voiceProviderRunner.run(context.providerSelection, audio).collect { runEvent ->
                    when (runEvent) {
                        is VoiceProviderRunEvent.Action -> {
                            log.i("assist_event", "type" to runEvent.event.javaClass.simpleName)
                            runEvent.event.transcriptOrNull()?.let { lastTranscript = it }
                            if (runEvent.event is VoiceActionEvent.ActionComplete) {
                                sawActionComplete = true
                            }
                            val newTerminalState =
                                handleVoiceActionEvent(runEvent.event, runGeneration, lastTranscript)
                            if (newTerminalState != null) {
                                ttsTerminalState = newTerminalState
                            }
                        }
                        is VoiceProviderRunEvent.AttemptFinished -> {
                            providerTrace = providerTrace + runEvent.attempt
                        }
                    }
                }
            }
            log.i("assistRunner flow ended", "sawActionComplete" to sawActionComplete)
            if (!isCurrentGeneration(runGeneration)) {
                return
            }
            emitRunResultIfActive(
                context = context,
                terminalState = ttsTerminalState ?: VoiceRunTerminalState.Completed(lastTranscript),
                providerTrace = providerTrace,
            )
            if (ttsTerminalState != null) {
                return
            }
            if (sawActionComplete) {
                holdActionCompleteThenIdle(runGeneration)
            } else {
                transitionIfActive(runGeneration, "state=Idle (post-collect)") { VoiceState.Idle }
            }
        } catch (throwable: TimeoutCancellationException) {
            log.w("assist run timed out", null, "timeoutMs" to assistRunTimeoutMs)
            failAndEmit(context, runGeneration, "assist run timed out", providerTrace)
        } catch (throwable: AssistErrorException) {
            failAndEmit(context, runGeneration, throwable.reason, providerTrace)
        }
    }

    private suspend fun holdActionCompleteThenIdle(runGeneration: Long) {
        when (responseModes.first()) {
            VoiceResponseMode.Visual -> {
                delay(ACTION_COMPLETE_HOLD_MS)
                if (mutableState.value is VoiceState.ActionComplete) {
                    transitionIfActive(runGeneration, "state=Idle (after visual action hold)") { VoiceState.Idle }
                }
            }
            VoiceResponseMode.Silent,
            VoiceResponseMode.Speak,
            -> {
                transitionIfActive(runGeneration, "state=Idle (after action complete without TTS)") { VoiceState.Idle }
            }
        }
    }

    /** Handles a single assist event. Throws [AssistErrorException] for Error events
     *  to break out of the surrounding collect. return@collect alone would not, since
     *  MutableSharedFlow is hot and never completes on its own. */
    private suspend fun handleVoiceActionEvent(
        event: VoiceActionEvent,
        runGeneration: Long,
        currentTranscript: String,
    ): VoiceRunTerminalState? =
        when (event) {
            is VoiceActionEvent.Recognition -> {
                val (newState, logMessage, logField) = recordingOrProcessingState(event.update)
                transitionIfActive(runGeneration, logMessage, logField) { newState }
                null
            }
            is VoiceActionEvent.ActionComplete -> {
                val transcript = event.transcript ?: currentTranscript
                transitionIfActive(runGeneration, "state=ActionComplete", "transcript" to transcript) {
                    VoiceState.ActionComplete(transcript, event.response)
                }
                null
            }
            is VoiceActionEvent.TtsReady -> {
                transitionIfActive(runGeneration, "state=Speaking", "ttsUrl" to event.mediaUrl) {
                    VoiceState.Speaking(event.mediaUrl, currentTranscript)
                }
                try {
                    ttsPlayer.play(event.mediaUrl)
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) {
                        throw throwable
                    }
                    log.w("TTS playback failed", throwable)
                }
                transitionIfActive(runGeneration, "state=Idle (after TTS)") { VoiceState.Idle }
                VoiceRunTerminalState.Speaking(currentTranscript)
            }
            is VoiceActionEvent.Error -> {
                log.w("assist_event Error", null, "code" to event.code, "message" to event.message)
                throw AssistErrorException("${event.code}: ${event.message}")
            }
            else -> {
                // RunStart, RunEnd, Other. Informational.
                null
            }
        }

    /** Sets Failed synchronously, then schedules the Failed→Idle clear on the
     *  coordinator's own scope so the hold survives even if the calling job is
     *  cancelled by stopAll() or replaced by a new wake. */
    private fun failTransition(
        context: VoiceRunContext,
        runGeneration: Long,
        reason: String,
    ) {
        if (!transitionIfActive(runGeneration, "state=Failed", "reason" to reason) { VoiceState.Failed(reason) }) {
            return
        }
        synchronized(transitionLock) {
            failedRunId = context.id
        }
        scope.launch {
            delay(FAILED_HOLD_MS)
            val shouldClear =
                synchronized(transitionLock) {
                    failedRunId == context.id && activeGeneration == runGeneration
                }
            if (shouldClear && mutableState.value is VoiceState.Failed) {
                mutableState.value = VoiceState.Idle
                synchronized(transitionLock) {
                    if (failedRunId == context.id) {
                        failedRunId = null
                    }
                }
            }
        }
    }

    private suspend fun failAndEmit(
        context: VoiceRunContext,
        runGeneration: Long,
        reason: String,
        providerTrace: List<VoiceProviderAttempt>,
    ) {
        if (!isCurrentGeneration(runGeneration)) {
            return
        }
        failTransition(context, runGeneration, reason)
        emitRunResultIfActive(
            context = context,
            terminalState = VoiceRunTerminalState.Failed(reason),
            providerTrace = providerTrace,
        )
    }

    private suspend fun emitRunResultIfActive(
        context: VoiceRunContext,
        terminalState: VoiceRunTerminalState,
        providerTrace: List<VoiceProviderAttempt>,
    ) {
        val isActive =
            synchronized(transitionLock) {
                activeRunId == context.id
            }
        if (!isActive) {
            return
        }
        mutableRunResults.emit(
            VoiceRunResult(
                context = context,
                terminalState = terminalState,
                providerTrace = providerTrace,
            ),
        )
    }

    private fun isCurrentGeneration(runGeneration: Long): Boolean =
        synchronized(transitionLock) {
            activeGeneration == runGeneration
        }

    /** Single funnel for every state write driven by an in-flight run. Checks
     *  the generation under the same lock that publishes it, so cancelled jobs
     *  cannot overwrite Idle after stopAll(). Returns whether the write fired. */
    private fun transitionIfActive(
        runGeneration: Long,
        logMessage: String,
        vararg fields: Pair<String, Any?>,
        nextState: () -> VoiceState,
    ): Boolean {
        synchronized(transitionLock) {
            if (activeGeneration != runGeneration) {
                return false
            }
            mutableState.value = nextState()
        }
        if (fields.isEmpty()) {
            log.i(logMessage)
        } else {
            log.i(logMessage, *fields)
        }
        return true
    }

    private class AssistErrorException(
        val reason: String,
    ) : RuntimeException(reason)
}

private data class StateTransition(
    val state: VoiceState,
    val logMessage: String,
    val logField: Pair<String, Any?>,
)

private fun recordingOrProcessingState(update: RecognitionUpdate): StateTransition =
    when (update) {
        is RecognitionUpdate.Partial ->
            StateTransition(
                state = VoiceState.Recording(partialTranscript = update.text),
                logMessage = "state=Recording",
                logField = "partialTranscript" to update.text,
            )
        is RecognitionUpdate.Final ->
            StateTransition(
                state = VoiceState.Processing(update.text),
                logMessage = "state=Processing",
                logField = "transcript" to update.text,
            )
    }

private fun VoiceActionEvent.transcriptOrNull(): String? =
    when (this) {
        is VoiceActionEvent.Recognition -> update.text
        is VoiceActionEvent.ActionComplete -> transcript
        else -> null
    }

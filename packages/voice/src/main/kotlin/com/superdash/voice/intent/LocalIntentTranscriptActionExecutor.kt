package com.superdash.voice.intent

import com.superdash.core.log.Log
import com.superdash.voice.action.TranscriptActionExecutor
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.pipeline.VoiceProviderProvenance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

private val log = Log("LocalIntent")

class LocalIntentTranscriptActionExecutor(
    private val enabled: suspend () -> Boolean,
    private val recognizer: LocalIntentRecognizer,
    private val dispatcher: LocalIntentActionDispatcher,
    private val fallbackExecutor: TranscriptActionExecutor,
) : TranscriptActionExecutor {
    override fun execute(transcript: String): Flow<VoiceActionEvent> =
        flow {
            if (!enabled()) {
                val result =
                    LocalIntentRecognitionResult(
                        status = LocalIntentStatus.Disabled,
                        transcript = transcript,
                    )
                log.i("disabled; using HA text", "transcript" to transcript)
                emit(VoiceActionEvent.ProviderProvenance(result.toProvenance(fallbackReason = "disabled")))
                fallbackExecutor.execute(transcript).collect { event -> emit(event) }
                return@flow
            }

            val result = recognizer.recognize(transcript)
            val action = result.action
            if (result.status != LocalIntentStatus.Matched || action == null) {
                log.i(
                    "no local match; using HA text",
                    "status" to result.status,
                    "transcript" to transcript,
                )
                emit(VoiceActionEvent.ProviderProvenance(result.toProvenance(fallbackReason = result.status.name)))
                fallbackExecutor.execute(transcript).collect { event -> emit(event) }
                return@flow
            }

            val call = (action as? LocalIntentAction.ServiceCall)?.call
            log.i(
                "matched local intent",
                "intentId" to result.intentId,
                "domain" to call?.domain,
                "service" to call?.service,
                "entityTarget" to
                    call
                        ?.target
                        ?.entityId
                        .orEmpty()
                        .joinToString(","),
                "areaTarget" to
                    call
                        ?.target
                        ?.areaId
                        .orEmpty()
                        .joinToString(","),
            )
            emit(VoiceActionEvent.ProviderProvenance(result.toProvenance(fallbackReason = null)))
            val updatedAction =
                when (action) {
                    is LocalIntentAction.ServiceCall -> action.copy(transcript = transcript)
                    is LocalIntentAction.SkillInvocation -> action.copy(transcript = transcript)
                }
            var sawCompletion = false
            var firstError: VoiceActionEvent.Error? = null
            dispatcher.dispatch(updatedAction).collect { event ->
                when (event) {
                    is VoiceActionEvent.ActionComplete -> {
                        sawCompletion = true
                        log.i("direct action completed", "transcript" to transcript)
                        emit(event)
                    }
                    is VoiceActionEvent.Error -> {
                        if (!sawCompletion && firstError == null) {
                            firstError = event
                        } else {
                            emit(event)
                        }
                    }
                    else -> {
                        emit(event)
                    }
                }
            }
            val unhandledError = firstError
            if (unhandledError != null && !sawCompletion) {
                log.w(
                    "direct action failed; using HA text",
                    null,
                    "code" to unhandledError.code,
                    "message" to unhandledError.message,
                )
                emit(VoiceActionEvent.ProviderProvenance(result.toProvenance(fallbackReason = unhandledError.code)))
                fallbackExecutor.execute(transcript).collect { event -> emit(event) }
            }
        }

    private fun LocalIntentRecognitionResult.toProvenance(
        fallbackReason: String?,
    ): VoiceProviderProvenance.LocalIntent {
        val call = (action as? LocalIntentAction.ServiceCall)?.call
        val skillId = (action as? LocalIntentAction.SkillInvocation)?.skillId
        return VoiceProviderProvenance.LocalIntent(
            status = status,
            transcript = transcript,
            intentId = intentId ?: skillId,
            confidence = confidence,
            threshold = threshold,
            directActionDomain = call?.domain,
            directActionService = call?.service,
            directActionTarget = call?.target?.entityId?.joinToString(","),
            fallbackReason = fallbackReason,
        )
    }
}

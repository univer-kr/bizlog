package kr.univer.bizlog.gateway.pleos

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kr.univer.bizlog.gateway.VoiceGateway
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

private const val TAG = "BizLog"

/**
 * Pleos TextToSpeech + SpeechToText, 둘 다 ON_DEVICE(비밀값 불필요, 음성이 차 밖으로 나가지 않음).
 * demoAudio = true면 마이크 대신 assets/demo_answer.pcm(16kHz, 16bit, mono)을 STT에 넣는다.
 * 에뮬레이터 마이크가 꺼져 있을 때 같은 STT 엔진으로 시연하기 위한 장치.
 * 리스너는 initialize 성공 뒤에만 붙일 수 있다(그 전에 붙이면 SDK가 크래시, 2026-11-12 실측).
 */
class PleosVoiceGateway(private val context: Context, private val demoAudio: () -> Boolean) : VoiceGateway {
    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null
    private var spoken: CompletableDeferred<Boolean>? = null
    private var heard: CompletableDeferred<String?>? = null

    private val ttsEvents = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() { spoken?.complete(true) }
        override fun onStop() { spoken?.complete(false) }
        override fun onError(errorType: TtsError) { Log.w(TAG, "TTS 오류 $errorType"); spoken?.complete(false) }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private val sttEvents = object : ResultListener {
        override fun onUpdated(stt: String, completed: Boolean) { if (completed) heard?.complete(stt.trim().ifEmpty { null }) }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onEndedRecognition() = Unit
        override fun onError(errorType: SttError) { Log.w(TAG, "STT 오류 $errorType"); heard?.complete(null) }
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private suspend fun ttsReady(): TextToSpeech? = tts ?: withTimeoutOrNull(5_000) {
        suspendCancellableCoroutine { c ->
            val t = TextToSpeech(context, TtsMode.ON_DEVICE)
            t.initialize(object : TtsInit {
                override fun onSuccess() { t.addEventListener(ttsEvents); tts = t; c.resume(t) }
                override fun onFailure(errorType: TtsError) { Log.w(TAG, "TTS 초기화 실패 $errorType"); c.resume(null) }
            })
        }
    }

    private suspend fun sttReady(): SpeechToText? = stt ?: withTimeoutOrNull(5_000) {
        suspendCancellableCoroutine { c ->
            val s = SpeechToText(context, SttMode.ON_DEVICE)
            s.initialize(object : SttInit {
                override fun onSuccess() { s.addListener(sttEvents); stt = s; c.resume(s) }
                override fun onFailure(errorType: SttError) { Log.w(TAG, "STT 초기화 실패 $errorType"); c.resume(null) }
            })
        }
    }

    override suspend fun speak(text: String): Boolean {
        val t = ttsReady() ?: return false
        val done = CompletableDeferred<Boolean>().also { spoken = it }
        t.speak(text, true)
        return withTimeoutOrNull(10_000) { done.await() } ?: false
    }

    override suspend fun listenOnce(): String? {
        val s = sttReady() ?: return null
        val result = CompletableDeferred<String?>().also { heard = it }
        if (demoAudio()) withContext(Dispatchers.IO) {
            val pcm = context.assets.open("demo_answer.pcm").use { it.readBytes() }
            for (i in pcm.indices step 3200) { s.sendAudio(pcm.copyOfRange(i, minOf(i + 3200, pcm.size))); Thread.sleep(100) } // 실시간 속도로
            s.completeAudioSend()
        } else s.request()
        val text = withTimeoutOrNull(12_000) { result.await() }
        if (text == null) runCatching { s.stop() }
        return text
    }

    override fun release() {
        runCatching { tts?.removeEventListener(ttsEvents); tts?.release() }
        runCatching { stt?.removeListener(sttEvents); stt?.release() }
    }
}

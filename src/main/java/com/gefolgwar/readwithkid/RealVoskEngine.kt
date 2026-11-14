package com.gefolgwar.readwithkid

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.Executors

/** Loads the Vosk model from [modelDir], which must hold an installed model (see [ModelManager]). */
class RealVoskEngine(private val modelDir: File) : VoskEngine {

    @Volatile private var _listener: VoskEngineListener? = null
    @Volatile private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var isRecording = false
    @Volatile private var isPaused = false
    @Volatile private var released = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Single background thread for model loading, grammar recognizer creation and model release,
    // so these never run on the main thread and never overlap.
    private val worker = Executors.newSingleThreadExecutor()

    // Guards [session] and [pendingRecognizer]: a grammar recognizer built for an old session is discarded.
    private val lock = Any()
    private var session = 0
    private var pendingRecognizer: Recognizer? = null

    override fun loadModel() {
        worker.execute {
            try {
                // A model deleted and downloaded again is loaded anew
                model?.close()
                model = null
                model = Model(modelDir.absolutePath)
                postToListener { it.onModelLoaded() }
            } catch (e: Exception) {
                postToListener { it.onError("Model load failed: ${e.message}") }
            }
        }
    }

    override fun start(mode: RecognitionMode, grammar: List<String>?) {
        val m = model ?: run {
            _listener?.onError("Model not loaded")
            return
        }

        synchronized(lock) {
            session++
            pendingRecognizer?.close()
            pendingRecognizer = null
        }
        recognizer?.close()
        recognizer = if (mode == RecognitionMode.GRAMMAR_CONSTRAINED && grammar != null) {
            Recognizer(m, SAMPLE_RATE, JSONArray(grammar).toString())
        } else {
            Recognizer(m, SAMPLE_RATE)
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            postToListener { it.onError("AudioRecord init failed") }
            return
        }

        isRecording = true
        isPaused = false
        audioRecord?.startRecording()

        recordingThread = Thread {
            val buffer = ShortArray(bufferSize / 2)
            while (isRecording) {
                takePendingRecognizer()?.let { next ->
                    recognizer?.close()
                    recognizer = next
                }
                if (isPaused) {
                    Thread.sleep(50)
                    continue
                }
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    val byteBuffer = ByteArray(read * 2)
                    for (i in 0 until read) {
                        byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                    }
                    val rec = recognizer ?: continue
                    if (rec.acceptWaveForm(byteBuffer, byteBuffer.size)) {
                        val result = rec.result
                        postToListener { it.onResult(result) }
                    } else {
                        val partial = rec.partialResult
                        postToListener { it.onPartialResult(partial) }
                    }
                }
            }
        }.apply { start() }
    }

    override fun updateGrammar(grammar: List<String>) {
        val m = model ?: return
        val requestedFor = synchronized(lock) { session }
        worker.execute {
            val next = Recognizer(m, SAMPLE_RATE, JSONArray(grammar).toString())
            synchronized(lock) {
                if (session == requestedFor) {
                    pendingRecognizer?.close()
                    pendingRecognizer = next
                } else {
                    next.close()
                }
            }
        }
    }

    override fun stop() {
        isRecording = false
        recordingThread?.join(1000)
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        synchronized(lock) {
            session++
            pendingRecognizer?.close()
            pendingRecognizer = null
        }
        recognizer?.close()
        recognizer = null
    }

    override fun pause(paused: Boolean) {
        isPaused = paused
    }

    override fun release() {
        released = true
        stop()
        mainHandler.removeCallbacksAndMessages(null)
        _listener = null
        // Queued after any in-flight load or grammar build, so the model is never closed while in use.
        worker.execute {
            model?.close()
            model = null
        }
        worker.shutdown()
    }

    override fun setListener(listener: VoskEngineListener) {
        _listener = listener
    }

    private fun takePendingRecognizer(): Recognizer? = synchronized(lock) {
        pendingRecognizer.also { pendingRecognizer = null }
    }

    private fun postToListener(action: (VoskEngineListener) -> Unit) {
        mainHandler.post {
            if (!released) _listener?.let(action)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16000.0f
    }
}

package com.gefolgwar.readwithkid

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream

class RealVoskEngine : VoskEngine {

    private var _listener: VoskEngineListener? = null
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var isRecording = false
    @Volatile private var isPaused = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun init(context: Context) {
        Thread {
            try {
                val modelDir = copyModelFromAssets(context)
                model = Model(modelDir)
                mainHandler.post { _listener?.onModelLoaded() }
            } catch (e: Exception) {
                mainHandler.post { _listener?.onError("Model load failed: ${e.message}") }
            }
        }.start()
    }

    override fun start(mode: RecognitionMode, grammar: List<String>?) {
        val m = model ?: run {
            _listener?.onError("Model not loaded")
            return
        }

        recognizer?.close()
        recognizer = if (mode == RecognitionMode.GRAMMAR_CONSTRAINED && grammar != null) {
            val grammarJson = JSONArray(grammar).toString()
            Recognizer(m, 16000.0f, grammarJson)
        } else {
            Recognizer(m, 16000.0f)
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
            mainHandler.post { _listener?.onError("AudioRecord init failed") }
            return
        }

        isRecording = true
        isPaused = false
        audioRecord?.startRecording()

        recordingThread = Thread {
            val buffer = ShortArray(bufferSize / 2)
            while (isRecording) {
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
                        mainHandler.post { _listener?.onResult(result) }
                    } else {
                        val partial = rec.partialResult
                        mainHandler.post { _listener?.onPartialResult(partial) }
                    }
                }
            }
        }.apply { start() }
    }

    override fun stop() {
        isRecording = false
        recordingThread?.join(1000)
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recognizer?.close()
        recognizer = null
    }

    override fun pause(paused: Boolean) {
        isPaused = paused
    }

    override fun release() {
        stop()
        model?.close()
        model = null
    }

    override fun setListener(listener: VoskEngineListener) {
        _listener = listener
    }

    private fun copyModelFromAssets(context: Context): String {
        val modelDir = File(context.filesDir, "model-uk")
        if (modelDir.exists() && modelDir.list()?.isNotEmpty() == true) {
            return modelDir.absolutePath
        }
        modelDir.mkdirs()
        copyAssetDir(context, "model-uk", modelDir)
        return modelDir.absolutePath
    }

    private fun copyAssetDir(context: Context, assetPath: String, targetDir: File) {
        val assets = context.assets.list(assetPath) ?: return
        if (assets.isEmpty()) {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(File(targetDir, "")).use { output ->
                    input.copyTo(output)
                }
            }
            return
        }
        for (asset in assets) {
            val subAssetPath = "$assetPath/$asset"
            val subList = context.assets.list(subAssetPath)
            if (subList != null && subList.isNotEmpty()) {
                val subDir = File(targetDir, asset)
                subDir.mkdirs()
                copyAssetDir(context, subAssetPath, subDir)
            } else {
                context.assets.open(subAssetPath).use { input ->
                    FileOutputStream(File(targetDir, asset)).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

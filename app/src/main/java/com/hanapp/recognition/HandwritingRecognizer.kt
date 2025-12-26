package com.hanapp.recognition

import android.util.Log
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink

class HandwritingRecognizer {
    private var recognizer: DigitalInkRecognizer? = null
    private var model: DigitalInkRecognitionModel? = null

    init {
        val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hans")
        if (modelIdentifier != null) {
            model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
            val options = DigitalInkRecognizerOptions.builder(model!!).build()
            recognizer = DigitalInkRecognition.getClient(options)
            
            // 确保模型已下载
            val remoteModelManager = RemoteModelManager.getInstance()
            remoteModelManager.download(model!!, DownloadConditions.Builder().build())
                .addOnSuccessListener { Log.i("Handwriting", "Model downloaded") }
                .addOnFailureListener { e -> Log.e("Handwriting", "Model download failed", e) }
        }
    }

    fun recognize(ink: Ink, onResult: (List<String>) -> Unit) {
        recognizer?.recognize(ink)
            ?.addOnSuccessListener { result ->
                val candidates = result.candidates.map { it.text }
                onResult(candidates)
            }
            ?.addOnFailureListener { e ->
                Log.e("Handwriting", "Recognition failed", e)
                onResult(emptyList())
            }
    }
}

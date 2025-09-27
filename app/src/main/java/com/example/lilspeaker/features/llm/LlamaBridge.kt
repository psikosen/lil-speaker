package com.example.lilspeaker.features.llm

object LlamaBridge {
    init {
        try {
            System.loadLibrary("lilspeaker-llama")
        } catch (t: Throwable) {
            throw IllegalStateException("Unable to load llama backend", t)
        }
    }

    external fun initBackend()
    external fun loadModel(path: String, nCtx: Int): Long
    external fun createContext(modelHandle: Long, nThreads: Int, nCtx: Int): Long
    external fun beginCompletion(
        ctxHandle: Long,
        prompt: String,
        temperature: Float,
        minP: Float,
        repetitionPenalty: Float,
        nThreads: Int
    ): Long
    external fun pullToken(sessionHandle: Long): String?
    external fun releaseSession(sessionHandle: Long)
    external fun releaseContext(ctxHandle: Long)
    external fun unloadModel(modelHandle: Long)
    external fun shutdown()
}

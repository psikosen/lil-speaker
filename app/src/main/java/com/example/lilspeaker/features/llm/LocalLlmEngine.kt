package com.example.lilspeaker.features.llm

import com.example.lilspeaker.core.logging.AppLogger
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class LocalLlmEngine(
    private val bridge: LlamaBridge = LlamaBridge,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val backendInitialised = AtomicBoolean(false)
    private val modelHandle = AtomicLong(0L)
    private val contextHandle = AtomicLong(0L)
    private val threadCount = AtomicInteger(0)

    suspend fun ensureLoaded(modelPath: File, maxContext: Int, performanceProfile: Int) {
        withContext(dispatcher) {
            if (!backendInitialised.get()) {
                bridge.initBackend()
                backendInitialised.set(true)
            }
            val absolutePath = modelPath.absolutePath
            if (!modelPath.exists()) {
                throw IllegalStateException("Model not found at $absolutePath")
            }
            if (modelHandle.get() == 0L) {
                val handle = bridge.loadModel(absolutePath, maxContext)
                modelHandle.set(handle)
            }
            if (contextHandle.get() == 0L) {
                val threads = Runtime.getRuntime().availableProcessors() - performanceProfile
                val ctxHandle = bridge.createContext(
                    modelHandle = modelHandle.get(),
                    nThreads = threads.coerceAtLeast(1),
                    nCtx = maxContext
                )
                threadCount.set(threads.coerceAtLeast(1))
                contextHandle.set(ctxHandle)
            }
        }
    }

    fun stream(request: LlmGenerationRequest): Flow<String> = flow {
        val ctx = contextHandle.get()
        if (ctx == 0L) {
            throw IllegalStateException("LLM context not loaded")
        }
        AppLogger.i(
            sourceClass = "LocalLlmEngine",
            function = "stream",
            systemSection = "llm_request",
            message = "Starting stream"
        )
        val sessionHandle = bridge.beginCompletion(
            ctxHandle = ctx,
            prompt = request.prompt,
            temperature = request.settings.temperature,
            minP = request.settings.minP,
            repetitionPenalty = request.settings.repetitionPenalty,
            nThreads = threadCount.get().coerceAtLeast(1)
        )
        try {
            while (true) {
                val next = bridge.pullToken(sessionHandle) ?: break
                emit(next)
            }
        } finally {
            bridge.releaseSession(sessionHandle)
        }
    }

    suspend fun unload() {
        withContext(dispatcher) {
            val ctxHandle = contextHandle.getAndSet(0L)
            if (ctxHandle != 0L) {
                bridge.releaseContext(ctxHandle)
            }
            val model = modelHandle.getAndSet(0L)
            if (model != 0L) {
                bridge.unloadModel(model)
            }
            threadCount.set(0)
            if (backendInitialised.getAndSet(false)) {
                bridge.shutdown()
            }
        }
    }
}

package com.example.epubnoveltranslator.data.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/** Owns the LiteRT-LM engine and its active text conversation. */
class LiteRtLmSession(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    fun isLoaded(): Boolean = engine != null && conversation != null

    suspend fun loadModelSession(modelPath: String): Result<Unit> =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val modelFile = File(modelPath)
                require(modelFile.isFile && modelFile.length() > 0L) {
                    "Model file is empty or missing."
                }

                close()
                val newEngine = Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath
                    )
                )

                try {
                    // This parses and initializes the .litertlm container. It is the
                    // authoritative validation step; a copied file alone is not enough.
                    newEngine.initialize()
                    check(newEngine.isInitialized()) {
                        "LiteRT-LM could not initialize this .litertlm model."
                    }
                    val newConversation = newEngine.createConversation()
                    engine = newEngine
                    conversation = newConversation
                    Result.success(Unit)
                } catch (error: Throwable) {
                    if (newEngine.isInitialized()) newEngine.close()
                    throw error
                }
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }

    fun generateResponseStream(prompt: String): Flow<String> = flow {
        val activeConversation = conversation
            ?: throw IllegalStateException("LiteRT-LM session is not initialized.")

        activeConversation.sendMessageAsync(prompt).collect { message ->
            emit(message.toString())
        }
    }.flowOn(Dispatchers.IO)

    /** Starts an independent turn without reloading the model weights. */
    suspend fun startNewConversation(): Result<Unit> =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val activeEngine = engine
                    ?: throw IllegalStateException("LiteRT-LM engine is not initialized.")
                try {
                    conversation?.close()
                } catch (_: Throwable) {
                    // A prior stream may already have released the conversation.
                }
                conversation = null
                conversation = activeEngine.createConversation()
                Result.success(Unit)
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }

    fun close() {
        try {
            conversation?.close()
        } catch (_: Throwable) {
            // Releasing an already-closed native resource is harmless for callers.
        } finally {
            conversation = null
            try {
                engine?.close()
            } catch (_: Throwable) {
                // The engine may not have finished initialization after a failed load.
            }
            engine = null
        }
    }
}

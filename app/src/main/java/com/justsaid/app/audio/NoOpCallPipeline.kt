package com.justsaid.app.audio

import com.justsaid.app.core.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 standalone pipeline. There is no STT/LLM yet, so the only contractual
 * obligation it honors is deleting the raw wav (Constitution A1) so the full
 * toggle -> capture -> disconnect flow is exercisable before Phase 3 exists.
 *
 * Phase 3/4 replace this Hilt binding with the real pipeline.
 */
@Singleton
class NoOpCallPipeline @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CallPipeline {

    override suspend fun process(call: RecordedCall) {
        withContext(ioDispatcher) {
            // Radioactive-audio rule: the buffer never outlives the pipeline step.
            if (call.wavFile.exists()) call.wavFile.delete()
        }
    }
}

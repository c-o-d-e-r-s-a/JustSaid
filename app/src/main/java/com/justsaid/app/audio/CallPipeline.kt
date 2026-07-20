package com.justsaid.app.audio

/**
 * Entry point Phase 2 calls once a call ends with LISTEN on. Phase 3/4 implement
 * the real STT -> LLM -> summary consumer. The implementation OWNS the wav file
 * lifecycle and MUST delete [RecordedCall.wavFile] when done (Constitution A1).
 */
interface CallPipeline {
    suspend fun process(call: RecordedCall)
}

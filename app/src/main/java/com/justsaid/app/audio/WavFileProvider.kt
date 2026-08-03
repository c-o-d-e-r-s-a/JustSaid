package com.justsaid.app.audio

import java.io.File

/**
 * Supplies a fresh, private wav path for a new capture. Production returns a file in
 * `context.cacheDir` (never external storage — Constitution A2); tests return a temp file.
 * Injecting this keeps [com.justsaid.app.session.CaptureSessionController] free of
 * Android types for JVM testing.
 */
fun interface WavFileProvider {
    fun newWavFile(): File
}

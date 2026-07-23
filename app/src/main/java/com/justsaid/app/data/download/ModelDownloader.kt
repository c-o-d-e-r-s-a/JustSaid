package com.justsaid.app.data.download

import android.content.Context
import com.justsaid.app.core.IoDispatcher
import com.justsaid.app.core.JustSaidResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Per-file lifecycle within a download run. */
enum class FileState { PENDING, DOWNLOADING, VERIFYING, DONE, FAILED }

/** Whole-run lifecycle. Terminal values are [SUCCESS] and [FAILED]. */
enum class OverallState { RUNNING, SUCCESS, FAILED }

/** Snapshot of one model file's progress. */
data class FileProgress(
    val id: String,
    val fileName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val state: FileState,
)

/**
 * Immutable progress snapshot emitted by [ModelDownloader.download]. The final
 * emission carries [OverallState.SUCCESS] or [OverallState.FAILED] (+ [error]),
 * which is the practical equivalent of a terminal [JustSaidResult] for the UI.
 */
data class DownloadProgress(
    val files: List<FileProgress>,
    val overallBytesDownloaded: Long,
    val overallTotalBytes: Long,
    val overallState: OverallState,
    val error: String? = null,
)

/** Emit at most one progress update per this many bytes, to avoid UI churn. */
private const val PROGRESS_STEP_BYTES = 1_000_000L
private const val BUFFER_BYTES = 64 * 1024

/**
 * Resumable, checksum-verified downloader for model weights. The ONLY networking
 * component in the app (AGENTS.md Prime Directive #1).
 *
 * Guarantees:
 * - Writes only to app-private `filesDir/models/` (never external storage).
 * - Resumes partial downloads via `.part` files + HTTP `Range`; verifies SHA-256
 *   before an atomic rename to the final name.
 * - Single-flight: concurrent callers are serialized so two runs never write the
 *   same `.part`; re-entry after failure resumes instead of restarting.
 */
@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val runLock = Mutex()

    private val modelsDir: File
        get() = File(context.filesDir, ModelCatalog.MODELS_DIR)

    /**
     * Downloads [specs] that are not already present, emitting progress as it goes.
     * Cold flow: collection starts the work; cancellation stops it and leaves
     * `.part` files in place for a later resume.
     */
    fun download(specs: List<ModelSpec>): Flow<DownloadProgress> = channelFlow {
        runLock.withLock {
            modelsDir.mkdirs()

            val overallTotal = specs.sumOf { it.sizeBytes }
            val progress = specs.associateTo(LinkedHashMap()) { spec ->
                val done = File(modelsDir, spec.fileName).exists()
                spec.id to FileProgress(
                    id = spec.id,
                    fileName = spec.fileName,
                    bytesDownloaded = if (done) spec.sizeBytes else 0L,
                    totalBytes = spec.sizeBytes,
                    state = if (done) FileState.DONE else FileState.PENDING,
                )
            }

            suspend fun emit(state: OverallState, error: String? = null) {
                send(
                    DownloadProgress(
                        files = specs.map { progress.getValue(it.id) },
                        overallBytesDownloaded = progress.values.sumOf { it.bytesDownloaded },
                        overallTotalBytes = overallTotal,
                        overallState = state,
                        error = error,
                    )
                )
            }

            emit(OverallState.RUNNING)

            for (spec in specs) {
                if (File(modelsDir, spec.fileName).exists()) continue

                val result = downloadOne(spec) { downloaded, total, state ->
                    progress[spec.id] = FileProgress(
                        id = spec.id,
                        fileName = spec.fileName,
                        bytesDownloaded = downloaded,
                        totalBytes = if (total > 0) total else spec.sizeBytes,
                        state = state,
                    )
                    emit(OverallState.RUNNING)
                }

                if (result is JustSaidResult.Failure) {
                    progress[spec.id] = progress.getValue(spec.id).copy(state = FileState.FAILED)
                    emit(OverallState.FAILED, result.reason)
                    return@withLock
                }

                progress[spec.id] = progress.getValue(spec.id)
                    .copy(bytesDownloaded = spec.sizeBytes, state = FileState.DONE)
            }

            // Drop the unused RAM-tier sibling (e.g. 3B after a 1B download) so
            // low-RAM phones reclaim ~2 GB of private storage.
            deleteUnusedLlmSiblings(keep = specs)
            emit(OverallState.SUCCESS)
        }
    }.flowOn(ioDispatcher)

    /** Deletes LLM artifacts that are not in the just-required set. */
    private fun deleteUnusedLlmSiblings(keep: List<ModelSpec>) {
        val keepNames = keep.mapTo(HashSet()) { it.fileName }
        for (llm in ModelCatalog.ALL_LLMS) {
            if (llm.fileName in keepNames) continue
            File(modelsDir, llm.fileName).delete()
            File(modelsDir, llm.fileName + ".part").delete()
        }
    }

    /**
     * Downloads a single file with resume + checksum verification.
     * [onProgress] receives (bytesDownloaded, totalBytes, state).
     */
    private suspend fun downloadOne(
        spec: ModelSpec,
        onProgress: suspend (Long, Long, FileState) -> Unit,
    ): JustSaidResult<Unit> {
        val finalFile = File(modelsDir, spec.fileName)
        val partFile = File(modelsDir, spec.fileName + ".part")

        return try {
            var existing = if (partFile.exists()) partFile.length() else 0L
            val digest = MessageDigest.getInstance("SHA-256")

            if (existing >= spec.sizeBytes && spec.sizeBytes > 0) {
                seedDigestFromFile(partFile, digest, spec.sizeBytes)
                return verifyAndFinalize(spec, partFile, finalFile, digest, onProgress)
            }

            val requestBuilder = Request.Builder().url(spec.url)
            if (existing > 0) requestBuilder.header("Range", "bytes=$existing-")

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 416) {
                    seedDigestFromFile(partFile, digest, existing)
                    return verifyAndFinalize(spec, partFile, finalFile, digest, onProgress)
                }
                if (!response.isSuccessful) {
                    return JustSaidResult.Failure("HTTP ${response.code} for ${spec.fileName}")
                }

                val serverIgnoredRange = existing > 0 && response.code == 200
                if (serverIgnoredRange) {
                    partFile.delete()
                    existing = 0L
                } else if (existing > 0) {
                    seedDigestFromFile(partFile, digest, existing)
                }

                val body = response.body ?: return JustSaidResult.Failure("Empty body for ${spec.fileName}")
                val contentLength = body.contentLength()
                val total = if (contentLength > 0) existing + contentLength else spec.sizeBytes

                onProgress(existing, total, FileState.DOWNLOADING)

                RandomAccessFile(partFile, "rw").use { raf ->
                    raf.seek(existing)
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        var downloaded = existing
                        var lastEmitted = existing
                        while (true) {
                            // Cancelling mid-stream throws here and skips verification,
                            // leaving the .part file intact for a later resume.
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            if (downloaded - lastEmitted >= PROGRESS_STEP_BYTES) {
                                onProgress(downloaded, total, FileState.DOWNLOADING)
                                lastEmitted = downloaded
                            }
                        }
                    }
                }
            }

            verifyAndFinalize(spec, partFile, finalFile, digest, onProgress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            JustSaidResult.Failure("Download failed for ${spec.fileName}: ${e.message}", e)
        }
    }

    private suspend fun verifyAndFinalize(
        spec: ModelSpec,
        partFile: File,
        finalFile: File,
        digest: MessageDigest,
        onProgress: suspend (Long, Long, FileState) -> Unit,
    ): JustSaidResult<Unit> {
        onProgress(partFile.length(), spec.sizeBytes, FileState.VERIFYING)

        val actual = digest.digest().toHex()
        if (!actual.equals(spec.sha256, ignoreCase = true)) {
            partFile.delete()
            return JustSaidResult.Failure("Checksum mismatch for ${spec.fileName}")
        }
        if (!partFile.renameTo(finalFile)) {
            return JustSaidResult.Failure("Could not finalize ${spec.fileName}")
        }
        onProgress(spec.sizeBytes, spec.sizeBytes, FileState.DONE)
        return JustSaidResult.Success(Unit)
    }

    /** Feeds the first [length] bytes of [file] into [digest] (used when resuming). */
    private fun seedDigestFromFile(file: File, digest: MessageDigest, length: Long) {
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            var remaining = length
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read == -1) break
                digest.update(buffer, 0, read)
                remaining -= read
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

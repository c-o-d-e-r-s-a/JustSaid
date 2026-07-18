package com.justsaid.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justsaid.app.data.download.DownloadProgress
import com.justsaid.app.data.download.FileState
import com.justsaid.app.data.download.ModelCatalog
import com.justsaid.app.data.download.ModelDownloader
import com.justsaid.app.data.download.OverallState
import com.justsaid.app.data.repo.SettingsRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row in the download list. */
data class FileRow(
    val name: String,
    val percent: Int,
    val downloadedMb: String,
    val totalMb: String,
    val state: FileState,
)

/** Immutable state the download screen renders. */
data class DownloadUiState(
    val files: List<FileRow> = emptyList(),
    val overallPercent: Int = 0,
    val overallDownloadedMb: String = "0.0",
    val overallTotalMb: String = "0.0",
    val isComplete: Boolean = false,
    val isError: Boolean = false,
)

/** Drives the model download, exposing progress and success/error for the UI. */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloader: ModelDownloader,
    private val settingsRepo: SettingsRepo,
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        start()
    }

    /** (Re)starts the download. Resumes from `.part` files if a previous attempt failed. */
    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(isError = false)
            val lock = settingsRepo.sttLanguageLock.first()
            val specs = ModelCatalog.requiredFor(lock)
            downloader.download(specs).collect { progress ->
                _state.value = progress.toUiState()
            }
        }
    }

    fun retry() = start()

    private fun DownloadProgress.toUiState(): DownloadUiState = DownloadUiState(
        files = files.map { f ->
            FileRow(
                name = f.fileName,
                percent = percentOf(f.bytesDownloaded, f.totalBytes),
                downloadedMb = mb(f.bytesDownloaded),
                totalMb = mb(f.totalBytes),
                state = f.state,
            )
        },
        overallPercent = percentOf(overallBytesDownloaded, overallTotalBytes),
        overallDownloadedMb = mb(overallBytesDownloaded),
        overallTotalMb = mb(overallTotalBytes),
        isComplete = overallState == OverallState.SUCCESS,
        isError = overallState == OverallState.FAILED,
    )

    private fun percentOf(downloaded: Long, total: Long): Int =
        if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0

    private fun mb(bytes: Long): String = String.format("%.1f", bytes / 1_000_000.0)
}

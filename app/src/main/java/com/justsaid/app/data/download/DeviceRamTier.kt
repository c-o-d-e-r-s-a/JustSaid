package com.justsaid.app.data.download

/**
 * Phone RAM bucket used only to pick which summarizer weights to download.
 * Pure data — no Android types — so JVM tests can drive [selectRamTier] directly.
 */
enum class DeviceRamTier {
    /** ≤ 4 GiB total RAM: mid/low-end phones (e.g. Galaxy A14). Prefer the 1B LLM. */
    LIGHT,
    /** > 4 GiB: enough headroom for the default 3B summarizer. */
    STANDARD,
}

/**
 * Maps reported total RAM to a [DeviceRamTier]. Threshold is inclusive on the light
 * side so a phone that advertises exactly 4 GiB still gets the safer model — peak
 * RSS during generation sits well above the weights file size.
 */
fun selectRamTier(totalRamBytes: Long): DeviceRamTier =
    if (totalRamBytes <= LIGHT_RAM_CEILING_BYTES) DeviceRamTier.LIGHT else DeviceRamTier.STANDARD

/** 4 GiB in bytes. */
internal const val LIGHT_RAM_CEILING_BYTES: Long = 4L * 1024 * 1024 * 1024

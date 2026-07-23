package com.justsaid.app.core

/**
 * Reports the device's total RAM. Injected so [com.justsaid.app.data.download.selectRamTier]
 * stays pure and unit-testable without Android framework types.
 */
fun interface DeviceRamInfo {
    /** Total physical RAM in bytes (not "available" — that fluctuates). */
    fun totalRamBytes(): Long
}

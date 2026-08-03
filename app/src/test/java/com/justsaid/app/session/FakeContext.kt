package com.justsaid.app.session

import android.content.Context
import android.content.ContextWrapper
import java.io.File

/** Minimal [Context] that returns a fixed cache directory for JVM tests. */
internal class FakeContext(private val cache: File) : ContextWrapper(null) {
  override fun getCacheDir(): File = cache
  override fun getApplicationContext(): Context = this
}

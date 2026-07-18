package com.justsaid.app.ui.theme

import androidx.compose.ui.graphics.Color

// High-contrast palette (WCAG AA, >=4.5:1 for text on its background). Colors are
// intentionally simple and bold for older users; no low-contrast greys for text.

// Light
val DeepBlue = Color(0xFF0B3D91)        // primary; white text on this = ~9.6:1
val DeepBlueDark = Color(0xFF082B66)
val OnPrimaryLight = Color(0xFFFFFFFF)
val BackgroundLight = Color(0xFFFFFFFF)
val OnBackgroundLight = Color(0xFF111111)  // near-black on white = ~19:1
val SurfaceLight = Color(0xFFF4F6FB)
val OnSurfaceLight = Color(0xFF111111)
val ErrorLight = Color(0xFFB00020)
val OnErrorLight = Color(0xFFFFFFFF)

// Dark
val LightBlue = Color(0xFF9EC1FF)       // primary on dark; black text on this = high contrast
val OnPrimaryDark = Color(0xFF00204D)
val BackgroundDark = Color(0xFF000000)
val OnBackgroundDark = Color(0xFFF2F2F2)
val SurfaceDark = Color(0xFF121212)
val OnSurfaceDark = Color(0xFFF2F2F2)
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)

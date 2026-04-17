// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Keel dark financial theme.
 *
 * Color semantics:
 *  - primary (teal-green): positive actions, credits, "money" accents
 *  - secondary: secondary interactive elements
 *  - tertiary (amber): WARNING severity insights
 *  - error: CRITICAL severity insights
 *  - background / surface: deep-navy dark palette for reduced eye-strain
 */
val KeelColorScheme = darkColorScheme(
    primary   = Color(0xFF00C896), // teal-green — "money green"
    secondary = Color(0xFF4CAF9A),
    tertiary  = Color(0xFFFFB74D), // amber — warnings
    background = Color(0xFF0D1117), // deep dark
    surface   = Color(0xFF161B22),
    error     = Color(0xFFCF6679),
)

/**
 * Root Material3 theme for the Keel app.
 *
 * Always dark — this is an on-device financial agent; a dark theme is the
 * default and the only variant shipped in v1.
 */
@Composable
fun KeelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KeelColorScheme,
        content = content,
    )
}

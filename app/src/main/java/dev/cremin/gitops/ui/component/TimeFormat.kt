package dev.cremin.gitops.ui.component

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Compact relative age, e.g. "3h", "5d", "2mo". Empty when unknown. */
fun relativeAge(createdAt: Instant?, now: Instant = Clock.System.now()): String {
    if (createdAt == null) return ""
    val seconds = (now - createdAt).inWholeSeconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3_600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3_600}h"
        seconds < 86_400 * 30 -> "${seconds / 86_400}d"
        else -> "${seconds / (86_400 * 30)}mo"
    }
}

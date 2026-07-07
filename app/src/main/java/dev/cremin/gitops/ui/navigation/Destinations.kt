package dev.cremin.gitops.ui.navigation

import android.net.Uri

/** Navigation routes. PR ids contain `/` and `#`, so they're URL-encoded in the path. */
object Destinations {
    const val LOGIN = "login"
    const val LIST = "list"
    const val SETTINGS = "settings"

    const val ARG_PR_ID = "prId"
    const val DETAIL = "detail/{$ARG_PR_ID}"

    fun detail(prId: String): String = "detail/${Uri.encode(prId)}"
}

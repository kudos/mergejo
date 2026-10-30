package dev.cremin.mergejo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.cremin.mergejo.ui.navigation.AppNavHost
import dev.cremin.mergejo.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** PR to open from a tapped notification, until the nav host has shown it. */
    private val pendingPrId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingPrId.value = intent.getStringExtra(EXTRA_PR_ID)
        enableEdgeToEdge()
        setContent {
            val openPrId by pendingPrId.collectAsStateWithLifecycle()
            AppTheme {
                AppNavHost(
                    openPrId = openPrId,
                    onPrOpened = { pendingPrId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingPrId.value = intent.getStringExtra(EXTRA_PR_ID)
    }

    companion object {
        const val EXTRA_PR_ID = "dev.cremin.mergejo.extra.PR_ID"
    }
}

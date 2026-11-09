package dev.cremin.mergejo.ui.screen.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography

/**
 * The detail screen's text roles. Every piece of text on the screen uses one of these, so the
 * hierarchy stays at five sizes:
 *
 * - [title]: the PR title
 * - [section]: section headers
 * - [item]: the heading of a card or row (package, release, collapsible summary)
 * - [body]: prose and values, including rendered markdown
 * - [secondary]: metadata about something else (repo, branch, dates, labels), in [secondaryColor]
 *
 * Colour carries meaning only: [secondaryColor] for metadata, `error` for problems, and `primary`
 * is left to things you can tap. Monospace ([code]) marks literal values (versions, branches,
 * IDs, tool output) and keeps the size of the role it's used in.
 */
object DetailText {
    val title: TextStyle @Composable get() = MaterialTheme.typography.titleLarge
    val section: TextStyle @Composable get() = MaterialTheme.typography.titleMedium
    val item: TextStyle @Composable get() = MaterialTheme.typography.titleSmall
    val body: TextStyle @Composable get() = MaterialTheme.typography.bodyMedium
    val secondary: TextStyle @Composable get() = MaterialTheme.typography.bodySmall

    val secondaryColor: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

    /** [style] in monospace, without the body styles' letter spacing, which spreads code out. */
    fun code(style: TextStyle): TextStyle = style.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.sp)

    /**
     * Markdown in the same roles. The renderer's defaults put text at `bodyLarge` and headings at
     * the display sizes, which would outsize the screen's own section headers.
     */
    @Composable
    fun markdown(): MarkdownTypography = markdownTypography(
        h1 = section,
        h2 = item,
        h3 = item,
        h4 = item,
        h5 = item,
        h6 = item,
        text = body,
        code = code(secondary),
        quote = body,
        paragraph = body,
        ordered = body,
        bullet = body,
        list = body,
    )
}

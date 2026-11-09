package dev.cremin.mergejo.ui.screen.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
import dev.cremin.mergejo.R
import dev.cremin.mergejo.domain.model.CommentBlock
import dev.cremin.mergejo.domain.model.PrComment
import dev.cremin.mergejo.ui.component.relativeAge

/** Shown only when there's something to show: comments, or an error loading them. */
@Composable
fun CommentsSection(state: CommentsState) {
    when (state) {
        CommentsState.Loading -> Unit
        is CommentsState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider()
            SectionHeader(stringResource(R.string.detail_comments))
            Text(
                stringResource(R.string.detail_comments_failed, state.message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        is CommentsState.Loaded -> if (state.comments.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider()
                SectionHeader(stringResource(R.string.detail_comments))
                state.comments.forEach { Comment(it) }
            }
        }
    }
}

@Composable
private fun Comment(comment: PrComment) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val age = relativeAge(comment.updatedAt ?: comment.createdAt)
        Text(
            stringResource(if (comment.edited) R.string.comment_meta_edited else R.string.comment_meta, comment.author, age),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        comment.blocks.forEachIndexed { index, block ->
            when (block) {
                is CommentBlock.Prose -> Markdown(
                    content = block.markdown,
                    typography = commentTypography(),
                    modifier = Modifier.fillMaxWidth(),
                )
                is CommentBlock.Details -> DetailsCard(block, key = "${comment.id}/$index")
            }
        }
    }
}

@Composable
private fun DetailsCard(block: CommentBlock.Details, key: String) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val summary = remember(block.summary) { boldSpans(block.summary) }
            Text(
                summary.ifEmpty { AnnotatedString(stringResource(R.string.comment_details)) },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
            )
        }
        if (expanded) {
            HorizontalDivider()
            if (block.code != null) {
                // Tool output lines up in columns, so scroll it sideways rather than wrap it.
                SelectionContainer {
                    Text(
                        block.code,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.sp, // the body styles' tracking makes wide output wider
                        softWrap = false,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            } else if (block.markdown.isNotBlank()) {
                Markdown(
                    content = block.markdown,
                    typography = commentTypography(),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
        }
    }
}

/**
 * The renderer's defaults start headings at the display styles, so a bot's `### Heading` would
 * outsize the screen's own section headers. Keep them at title sizes.
 */
@Composable
private fun commentTypography(): MarkdownTypography {
    val type = MaterialTheme.typography
    return markdownTypography(
        h1 = type.titleLarge,
        h2 = type.titleMedium,
        h3 = type.titleSmall,
        h4 = type.titleSmall,
        h5 = type.labelLarge,
        h6 = type.labelLarge,
    )
}

/** `a **b** c` → "a b c" with "b" bold. */
private fun boldSpans(text: String): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
    }
}

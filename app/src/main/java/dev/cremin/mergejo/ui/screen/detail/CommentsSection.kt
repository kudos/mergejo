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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
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
                style = DetailText.secondary,
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
            style = DetailText.secondary,
            color = DetailText.secondaryColor,
        )
        comment.blocks.forEachIndexed { index, block ->
            when (block) {
                is CommentBlock.Prose -> Markdown(
                    content = block.markdown,
                    typography = DetailText.markdown(),
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
                style = DetailText.body,
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
                        style = DetailText.code(DetailText.secondary),
                        softWrap = false,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            } else if (block.markdown.isNotBlank()) {
                Markdown(
                    content = block.markdown,
                    typography = DetailText.markdown(),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
        }
    }
}

/** `a **b** c` → "a b c" with "b" bold. */
private fun boldSpans(text: String): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
    }
}

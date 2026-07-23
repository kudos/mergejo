package dev.cremin.mergejo.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.cremin.mergejo.domain.model.CiStatus
import dev.cremin.mergejo.domain.model.UpdateType

private val Red = Color(0xFFD32F2F)
private val Amber = Color(0xFFF9A825)
private val Green = Color(0xFF388E3C)
private val Purple = Color(0xFF7B1FA2)

@Composable
private fun LabelPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun UpdateTypeChip(updateType: UpdateType, modifier: Modifier = Modifier) {
    val (label, color) = when (updateType) {
        UpdateType.MAJOR -> "major" to Red
        UpdateType.MINOR -> "minor" to Amber
        UpdateType.PATCH -> "patch" to Green
        UpdateType.DIGEST -> "digest" to Purple
        UpdateType.PIN -> "pin" to Purple
        UpdateType.OTHER -> "update" to MaterialTheme.colorScheme.secondary
    }
    LabelPill(text = label, color = color, modifier = modifier)
}

@Composable
fun SecurityChip(modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = Red,
            modifier = Modifier.padding(end = 2.dp).size(14.dp),
        )
        LabelPill(text = "security", color = Red)
    }
}

@Composable
fun RebasePendingChip(modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Icon(
            imageVector = Icons.Outlined.MergeType,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(end = 2.dp).size(14.dp),
        )
        LabelPill(text = "rebasing", color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
fun CiStatusBadge(status: CiStatus, modifier: Modifier = Modifier) {
    data class Spec(val icon: ImageVector, val tint: Color, val desc: String)
    val spec = when (status) {
        CiStatus.SUCCESS -> Spec(Icons.Filled.CheckCircle, Green, "CI passing")
        CiStatus.FAILURE -> Spec(Icons.Filled.Cancel, Red, "CI failing")
        CiStatus.PENDING -> Spec(Icons.Filled.Schedule, Amber, "CI pending")
        CiStatus.NONE -> Spec(
            Icons.Filled.RemoveCircleOutline,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "No CI",
        )
    }
    Icon(
        imageVector = spec.icon,
        contentDescription = spec.desc,
        tint = spec.tint,
        modifier = modifier.size(18.dp),
    )
}

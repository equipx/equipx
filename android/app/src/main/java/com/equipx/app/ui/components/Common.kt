package com.equipx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.equipx.app.ui.theme.EquipXColors

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = EquipXColors.Accent, strokeWidth = 3.dp)
    }
}

/** Error with icon, message and a retry action. */
@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong",
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(EquipXColors.DangerTint), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = EquipXColors.Danger, modifier = Modifier.size(32.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = EquipXColors.TextSecondary, textAlign = TextAlign.Center)
            if (onRetry != null) PrimaryButton("Try again", onClick = onRetry, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Empty state with icon, message and an optional action button. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(EquipXColors.AccentTint), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = EquipXColors.Accent, modifier = Modifier.size(30.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = EquipXColors.TextSecondary, textAlign = TextAlign.Center)
            }
            if (actionLabel != null && onAction != null) PrimaryButton(actionLabel, onClick = onAction, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Section title with an optional count chip and trailing action text. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    topPadding: androidx.compose.ui.unit.Dp = 28.dp,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = topPadding, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge)
        if (count != null) Pill(count.toString())
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

/** Rounded numbered badge, used for part numbers and step numbers. */
@Composable
fun NumberBadge(
    number: Int,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Box(
        modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) EquipXColors.Accent else EquipXColors.AccentTint),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (highlighted) Color.White else EquipXColors.Accent,
        )
    }
}

/** Circle with the student's initials. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val initials = name.trim().split(' ', '.', '@').filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        modifier.size(size).clip(CircleShape).background(EquipXColors.Accent),
        contentAlignment = Alignment.Center,
    ) { Text(initials, style = MaterialTheme.typography.titleMedium, color = Color.White) }
}

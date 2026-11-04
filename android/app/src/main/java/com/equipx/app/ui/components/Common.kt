package com.equipx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.equipx.app.ui.theme.EquipXColors

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = EquipXColors.Accent, strokeWidth = 2.dp)
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong",
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodySmall, color = EquipXColors.TextSecondary, textAlign = TextAlign.Center)
            if (onRetry != null) SecondaryButton("Try again", onClick = onRetry)
        }
    }
}

@Composable
fun EmptyState(title: String, modifier: Modifier = Modifier, message: String? = null) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = EquipXColors.TextSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Small uppercase-free section label used between blocks on detail screens. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, count: Int? = null) {
    Text(
        if (count != null) "$text  $count" else text,
        modifier = modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = EquipXColors.TextSecondary,
    )
}

/** Square badge with a number, used for part numbers and step numbers. */
@Composable
fun NumberBadge(
    number: Int,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .size(28.dp)
            .clip(shape)
            .background(if (highlighted) EquipXColors.Accent else EquipXColors.Surface)
            .border(1.dp, if (highlighted) EquipXColors.Accent else EquipXColors.Border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) Color.White else EquipXColors.Text,
        )
    }
}

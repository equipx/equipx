package com.equipx.app.ui.gamification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.equipx.app.data.ActivityTracker
import com.equipx.app.domain.Badge
import kotlinx.coroutines.delay
import com.equipx.app.ui.theme.EquipXColors

/** App-level toast shown at the top whenever badges unlock; queues several badges and shows them one by one. */
@Composable
fun BadgeToastHost(tracker: ActivityTracker, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<Badge?>(null) }
    val pending by tracker.pendingBadges.collectAsState()
    val head = pending.firstOrNull()
    // The queue lives in the tracker (app lifetime): after activity recreation the head badge is simply shown again.
    LaunchedEffect(head) {
        if (head != null) {
            current = head
            delay(3000)
            current = null
            delay(300)
            tracker.acknowledgeBadge(head)
        }
    }
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = current != null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            val b = current
            if (b != null) BadgeToastCard(b)
        }
    }
}

@Composable
private fun BadgeToastCard(badge: Badge) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, EquipXColors.Border, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(badgeIcon(badge), contentDescription = null, tint = EquipXColors.Accent)
        Column {
            Text("Badge unlocked", style = MaterialTheme.typography.labelMedium, color = EquipXColors.TextSecondary)
            Text(badge.title, style = MaterialTheme.typography.titleSmall, color = EquipXColors.Text)
        }
    }
}

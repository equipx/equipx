package com.equipx.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.equipx.app.ui.theme.Accent
import com.equipx.app.ui.theme.EquipXColors

val CardShape = RoundedCornerShape(16.dp)
val TileShape = RoundedCornerShape(12.dp)

/**
 * Elevated card: white surface, soft shadow and a 1dp hairline so it still reads on low-contrast screens.
 * Clickable when [onClick] is given.
 */
@Composable
fun EquipXCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = Color.White,
    elevation: Dp = 2.dp,
    shape: RoundedCornerShape = CardShape,
    borderColor: Color = EquipXColors.Border,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = containerColor,
            shadowElevation = elevation,
            border = BorderStroke(1.dp, borderColor),
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            shadowElevation = elevation,
            border = BorderStroke(1.dp, borderColor),
            content = content,
        )
    }
}

/** List row: tinted icon tile + title + subtle meta, at least 56dp tall. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
    accent: Accent = Accent(EquipXColors.AccentTint, EquipXColors.Accent),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(TileShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) leading()
        else if (icon != null) IconTile(icon, accent, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta != null) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = EquipXColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(EquipXColors.Border))
}

/** Small rounded chip. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = EquipXColors.Surface,
    contentColor: Color = EquipXColors.TextSecondary,
) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
        maxLines = 1,
    )
}

/** Chip tinted with a category/accent colour. */
@Composable
fun AccentChip(text: String, accent: Accent, modifier: Modifier = Modifier) =
    Pill(text, modifier, background = accent.tint, contentColor = accent.strong)

/** Safety callout: light yellow with a left bar. */
@Composable
fun Callout(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(TileShape)
            .background(EquipXColors.Callout),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(EquipXColors.CalloutBar))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = TileShape,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp, disabledElevation = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = EquipXColors.Accent,
            contentColor = Color.White,
            disabledContainerColor = EquipXColors.Border,
            disabledContentColor = EquipXColors.TextSecondary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Box(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else EquipXColors.TextSecondary)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = TileShape,
        border = BorderStroke(1.dp, EquipXColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = EquipXColors.Text),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = EquipXColors.Accent)
            Box(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Top bar for pushed screens: bold title, optional back button, no hairline (the canvas colour separates it). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipXTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    containerColor: Color = EquipXColors.Canvas,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor, scrolledContainerColor = containerColor),
    )
}

/** Large title block used at the top of the bottom-nav tabs. */
@Composable
fun TabTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = EquipXColors.TextSecondary)
    }
}

/** Rounded linear progress bar with animated fill. */
@Composable
fun EquipXProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = EquipXColors.Accent,
    trackColor: Color = EquipXColors.Border,
    height: Dp = 8.dp,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(500), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(trackColor)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(RoundedCornerShape(50)).background(color))
    }
}

/** Circular progress ring with centred [content] (e.g. a percentage). */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    stroke: Dp = 5.dp,
    color: Color = EquipXColors.Accent,
    trackColor: Color = EquipXColors.Border,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val inset = s / 2
            val arc = Size(this.size.width - s, this.size.height - s)
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(s))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arc, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

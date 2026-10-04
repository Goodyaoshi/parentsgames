package com.goodyaoshi.parentsgames.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography

@Composable
fun GameCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(theme.radii.lg))
            .background(theme.color.bgSurface)
            .border(0.5.dp, theme.color.borderSubtle, RoundedCornerShape(theme.radii.lg))
            .clickable(onClick = onClick)
            .padding(theme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .clip(RoundedCornerShape(theme.radii.md))
                .background(theme.color.bgSurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            illustration()
        }
        Spacer(Modifier.height(theme.spacing.sm))
        Text(title, color = theme.color.textPrimary, style = typo.titleSm)
        Text(subtitle, color = theme.color.textSecondary, style = typo.labelMd)
    }
}

@Composable
fun ContinueBanner(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.radii.lg))
            .background(theme.color.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.md, vertical = theme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(theme.color.primaryPressed),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = theme.color.textInverse, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(theme.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = theme.color.textInverse.copy(alpha = 0.85f), style = typo.labelMd)
            Text(subtitle, color = theme.color.textInverse, style = typo.titleSm)
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(theme.radii.md))
            .background(theme.color.bgSurfaceMuted)
            .padding(theme.spacing.md),
    ) {
        Text(label, color = theme.color.textSecondary, style = typo.labelMd)
        Spacer(Modifier.height(theme.spacing.xxs))
        Text(value, color = theme.color.textPrimary, style = typo.numericMd)
    }
}

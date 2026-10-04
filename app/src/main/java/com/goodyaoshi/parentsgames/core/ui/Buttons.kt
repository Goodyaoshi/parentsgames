package com.goodyaoshi.parentsgames.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when {
        !enabled -> theme.color.bgSurfaceMuted
        pressed -> theme.color.primaryPressed
        else -> theme.color.primary
    }
    val fg = if (enabled) theme.color.textInverse else theme.color.textDisabled

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 72.dp)
            .clip(RoundedCornerShape(theme.radii.md))
            .background(bg)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = theme.spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        leading?.let {
            Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(theme.spacing.xs))
        }
        Text(text, color = fg, style = typo.labelLg)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val borderColor = if (pressed) theme.color.primaryPressed else theme.color.primary
    val fg = if (enabled) theme.color.primary else theme.color.textDisabled

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(theme.radii.md))
            .background(Color.Transparent)
            .border(1.5.dp, borderColor, RoundedCornerShape(theme.radii.md))
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = theme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = typo.labelLg)
    }
}

@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(theme.radii.md))
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = theme.color.primary, style = typo.labelMd)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bgColor: Color? = null,
    iconTint: Color? = null,
) {
    val theme = LocalAppTheme.current
    val bg = bgColor ?: theme.color.bgSurfaceMuted
    val tint = iconTint ?: theme.color.primaryPressed
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

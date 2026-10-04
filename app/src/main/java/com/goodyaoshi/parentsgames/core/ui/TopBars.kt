package com.goodyaoshi.parentsgames.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography

@Composable
fun HomeTopBar(
    greeting: String,
    onRecordsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = theme.spacing.lg, vertical = theme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("欢迎回来", color = theme.color.textSecondary, style = typo.labelMd)
            Spacer(Modifier.height(2.dp))
            Text(greeting, color = theme.color.textPrimary, style = typo.displayMd)
        }
        CircleIconButton(
            icon = Icons.Outlined.EmojiEvents,
            contentDescription = "查看战绩",
            onClick = onRecordsClick,
        )
        Spacer(Modifier.width(theme.spacing.xs))
        CircleIconButton(
            icon = Icons.Outlined.Settings,
            contentDescription = "打开设置",
            onClick = onSettingsClick,
        )
    }
}

@Composable
fun GameTopBar(
    title: String,
    subtitle: String?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    rightContent: @Composable () -> Unit = {},
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = theme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            onClick = onBackClick,
        )
        Spacer(Modifier.width(theme.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = theme.color.textPrimary, style = typo.titleMd)
            subtitle?.let {
                Text(it, color = theme.color.textSecondary, style = typo.labelMd)
            }
        }
        rightContent()
    }
}

@Composable
fun SimpleTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = theme.spacing.md),
        contentAlignment = Alignment.CenterStart,
    ) {
        CircleIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            onClick = onBackClick,
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(title, color = theme.color.textPrimary, style = typo.titleMd)
        }
    }
}

package com.goodyaoshi.parentsgames.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography
import androidx.compose.foundation.background

/**
 * Estado vazio padrão do app. Usar em todas as telas que podem ficar sem dados.
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(theme.spacing.xl),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(theme.color.bgSurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = theme.color.primary,
                modifier = Modifier.size(58.dp),
            )
        }
        Spacer(Modifier.height(theme.spacing.md))
        Text(
            text = title,
            color = theme.color.textPrimary,
            style = typo.titleMd,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(theme.spacing.xs))
        Text(
            text = description,
            color = theme.color.textSecondary,
            style = typo.bodyMd,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(theme.spacing.lg))
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

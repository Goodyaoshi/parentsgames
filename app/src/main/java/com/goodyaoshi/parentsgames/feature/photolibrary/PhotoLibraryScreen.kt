package com.goodyaoshi.parentsgames.feature.photolibrary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.ui.EmptyState
import com.goodyaoshi.parentsgames.core.ui.SimpleTopBar

@Composable
fun PhotoLibraryScreen(
    onBack: () -> Unit,
    onPhotoSelected: (photoId: Long) -> Unit,
) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
        SimpleTopBar(title = "我的照片", onBackClick = onBack)
        EmptyState(
            title = "这里还没有照片",
            description = "开始拼图时，可以直接从相册选择照片。",
            icon = Icons.Outlined.PhotoLibrary,
        )
    }
}

package com.goodyaoshi.parentsgames.feature.games.jigsaw

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography
import com.goodyaoshi.parentsgames.core.ui.PrimaryButton
import com.goodyaoshi.parentsgames.core.ui.SecondaryButton
import com.goodyaoshi.parentsgames.core.ui.SimpleTopBar
import com.goodyaoshi.parentsgames.feature.games.jigsaw.domain.JigsawSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private val DIFFICULTIES = listOf(12, 24, 48, 100)

@HiltViewModel
class JigsawSetupViewModel @Inject constructor(
    private val session: JigsawSession,
) : ViewModel() {
    fun setPhoto(uri: String?) { session.pendingPhotoUri = uri }
    fun currentPhoto(): String? = session.pendingPhotoUri
}

@Composable
fun JigsawSetupScreen(
    onBack: () -> Unit,
    onLaunch: (photoId: Long, pieceCount: Int) -> Unit,
    viewModel: JigsawSetupViewModel = hiltViewModel(),
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    val context = LocalContext.current
    var selectedPhotoUri by remember { mutableStateOf(viewModel.currentPhoto()) }
    var selectedDifficulty by remember { mutableStateOf(12) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            // Persiste permissão de leitura para conseguir abrir depois
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val s = uri.toString()
            selectedPhotoUri = s
            viewModel.setPhoto(s)
        }
    }

    LaunchedEffect(selectedPhotoUri) {
        viewModel.setPhoto(selectedPhotoUri)
    }

    Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
        SimpleTopBar(title = "拼图", onBackClick = onBack)

        Column(modifier = Modifier.padding(theme.spacing.lg)) {
            Text("选择一张图片", color = theme.color.textPrimary, style = typo.titleMd)
            Spacer(Modifier.height(theme.spacing.md))

            // Photo preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(theme.radii.lg))
                    .background(theme.color.bgSurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                if (selectedPhotoUri == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = theme.color.textSecondary,
                            modifier = Modifier.size(58.dp),
                        )
                        Spacer(Modifier.height(theme.spacing.xs))
                        Text(
                            "从相册选择一张照片",
                            color = theme.color.textSecondary,
                            style = typo.bodyMd,
                        )
                        Spacer(Modifier.height(theme.spacing.xs))
                        Text(
                            "或者不用照片——我们会使用默认风景图",
                            color = theme.color.textSecondary.copy(alpha = 0.7f),
                            style = typo.labelMd,
                        )
                    }
                } else {
                    AsyncImage(
                        model = selectedPhotoUri,
                        contentDescription = "预览",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(theme.radii.lg)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.height(theme.spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.sm),
            ) {
                SecondaryButton(
                    text = "相册",
                    onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(visible = selectedPhotoUri != null) {
                    SecondaryButton(
                        text = "更换",
                        onClick = {
                            selectedPhotoUri = null
                            viewModel.setPhoto(null)
                        },
                        modifier = Modifier.padding(start = theme.spacing.sm),
                    )
                }
            }

            Spacer(Modifier.height(theme.spacing.lg))
            Text("难度", color = theme.color.textPrimary, style = typo.titleMd)
            Spacer(Modifier.height(theme.spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs),
            ) {
                DIFFICULTIES.forEach { n ->
                    val isSel = selectedDifficulty == n
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(theme.radii.md))
                            .background(if (isSel) theme.color.primary else theme.color.bgSurfaceMuted)
                            .clickable { selectedDifficulty = n },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$n 块",
                            color = if (isSel) theme.color.textInverse else theme.color.textPrimary,
                            style = typo.labelLg,
                        )
                    }
                }
            }

            Spacer(Modifier.height(theme.spacing.xl))

            PrimaryButton(
                text = "开始",
                onClick = { onLaunch(0L, selectedDifficulty) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

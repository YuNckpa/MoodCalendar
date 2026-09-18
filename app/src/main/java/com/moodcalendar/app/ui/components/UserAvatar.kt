package com.moodcalendar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.moodcalendar.app.R

@Composable
fun UserAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = "头像"
) {
    val context = LocalContext.current
    val model = avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .memoryCacheKey(url)
            .diskCacheKey(url)
            .build()
    } ?: R.drawable.ic_default_avatar

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.ic_default_avatar),
            placeholder = painterResource(R.drawable.ic_default_avatar)
        )
    }
}

package com.moodcalendar.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun SoftCoverBackground(
    uri: String,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = 0.25f
) {
    val context = LocalContext.current
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(10.dp)
    } else {
        Modifier
    }
    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(uri)
                .size(96)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(blurModifier)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
        )
    }
}

package com.example.epubnoveltranslator.ui.screens.conversation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Movie
import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Lightweight GIF playback without a third-party image-loading dependency. */
@Composable
fun TranslationLoadingGif(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // Fits small phones while retaining a pleasant presence on large screens.
        val gifSize = (maxWidth * 0.58f).coerceIn(150.dp, 260.dp)
        AndroidView(
            modifier = Modifier.size(gifSize),
            factory = { context -> TranslationWaitingGifView(context) }
        )
    }
}

private class TranslationWaitingGifView(context: Context) : View(context) {
    private val movie = context.assets.open("translation_waiting.gif").use { Movie.decodeStream(it) }
    private val startedAt = SystemClock.uptimeMillis()
    private val duration = movie?.duration()?.takeIf { it > 0 } ?: 1_000

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val animation = movie ?: return
        val frameTime = ((SystemClock.uptimeMillis() - startedAt) % duration).toInt()
        animation.setTime(frameTime)
        val scale = minOf(width / animation.width().toFloat(), height / animation.height().toFloat())
        canvas.save()
        canvas.translate((width - animation.width() * scale) / 2, (height - animation.height() * scale) / 2)
        canvas.scale(scale, scale)
        animation.draw(canvas, 0f, 0f)
        canvas.restore()
        postInvalidateOnAnimation()
    }
}

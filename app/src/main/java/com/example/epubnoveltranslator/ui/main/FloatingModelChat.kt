package com.example.epubnoveltranslator.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.epubnoveltranslator.R
import kotlin.math.roundToInt

@Composable
fun FloatingModelChat(
    viewModel: FloatingModelChatViewModel = viewModel(),
    translationActive: Boolean = false,
    onTranslationBusySendAttempt: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var offset by remember { mutableStateOf(Offset(20f, 420f)) }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bubbleSize = 60.dp
        val maxX = with(density) { (maxWidth - bubbleSize).coerceAtLeast(0.dp).toPx() }
        val maxY = with(density) { (maxHeight - bubbleSize).coerceAtLeast(0.dp).toPx() }
        LaunchedEffect(maxX, maxY) {
            offset = Offset(offset.x.coerceIn(0f, maxX), offset.y.coerceIn(0f, maxY))
        }

        Surface(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(bubbleSize)
                .shadow(10.dp, CircleShape)
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset = Offset(
                            x = (offset.x + dragAmount.x).coerceIn(0f, maxX),
                            y = (offset.y + dragAmount.y).coerceIn(0f, maxY)
                        )
                    }
                },
            onClick = { expanded = true },
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = Color.Unspecified
        ) {
            Image(
                painter = painterResource(R.drawable.model_chat_bubble),
                contentDescription = "Open model chat",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }

    if (expanded) {
        FloatingChatDialog(
            state = state,
            translationActive = translationActive,
            onSend = viewModel::send,
            onClear = viewModel::clear,
            onTranslationBusySendAttempt = onTranslationBusySendAttempt,
            onDismiss = { expanded = false }
        )
    }
}

@Composable
private fun FloatingChatDialog(
    state: FloatingChatState,
    translationActive: Boolean,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
    onTranslationBusySendAttempt: () -> Unit,
    onDismiss: () -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp, max = 620.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.Transparent) {
                        Image(
                            painter = painterResource(R.drawable.model_chat_bubble),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Model chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (translationActive) "Chapter translation in progress" else "Ready to chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onClear, enabled = state.messages.isNotEmpty() && !state.isGenerating) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear chat")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close chat")
                    }
                }

                if (state.messages.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "Ask the active model anything.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.messages.size) { index ->
                            val message = state.messages[index]
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = if (message.isModel) MaterialTheme.colorScheme.surfaceContainerHigh
                                else MaterialTheme.colorScheme.primaryContainer
                            ) {
                                SelectionContainer {
                                    Text(
                                        message.text,
                                        modifier = Modifier.padding(12.dp),
                                        color = if (message.isModel) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                state.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isGenerating && !translationActive,
                        placeholder = { Text(if (translationActive) "Model is translating a chapter" else "Type or paste a message") },
                        maxLines = 4
                    )
                    val sendEnabled = input.isNotBlank() && !state.isGenerating && !translationActive
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = if (sendEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (sendEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            if (translationActive) onTranslationBusySendAttempt()
                            else if (input.isNotBlank() && !state.isGenerating) {
                                onSend(input)
                                input = ""
                            }
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                        }
                    }
                }
            }
        }
    }
}

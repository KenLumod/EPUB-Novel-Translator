package com.example.epubnoveltranslator.ui.screens.conversation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.example.epubnoveltranslator.data.UserPreferences
import com.example.epubnoveltranslator.data.db.GlossaryTermEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    novelId: String,
    chapterId: String,
    onBackClick: () -> Unit,
    viewModel: ConversationViewModel? = null
) {
    // Activity-scoped, chapter-keyed ViewModels keep an active translation alive after
    // Back without allowing it to replace another chapter's cached reader screen.
    val activity = LocalContext.current as? ComponentActivity
    val context = LocalContext.current
    val readerPreferences = remember(context) { UserPreferences(context) }
    val conversationViewModel = viewModel ?: if (activity != null) {
        composeViewModel(key = "conversation-$chapterId", viewModelStoreOwner = activity)
    } else {
        composeViewModel()
    }
    LaunchedEffect(novelId, chapterId) {
        conversationViewModel.loadConversation(novelId, chapterId)
        conversationViewModel.refreshInteractionPreferences()
    }

    val uiState by conversationViewModel.uiState.collectAsStateWithLifecycle()
    var showGlossary by remember { mutableStateOf(false) }
    var showReaderSettings by remember { mutableStateOf(false) }
    var fontSizeSp by remember { mutableFloatStateOf(readerPreferences.readerFontSizeSp) }
    var fontFamily by remember { mutableStateOf(readerPreferences.readerFontFamily) }
    var fontColorArgb by remember { mutableIntStateOf(readerPreferences.readerFontColor) }

    when (val state = uiState) {
        is ConversationUiState.Initializing -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is ConversationUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        is ConversationUiState.Content -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = state.chapterTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = state.novelTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { showReaderSettings = true }) {
                                Icon(Icons.Default.FormatSize, contentDescription = "Reader appearance")
                            }
                            IconButton(onClick = { showGlossary = true }) {
                                Icon(
                                    imageVector = Icons.Default.FormatListBulleted,
                                    contentDescription = "View glossary"
                                )
                            }
                            IconButton(
                                onClick = { conversationViewModel.startTranslation(forceRefresh = true) },
                                enabled = !state.isStreaming
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Re-translate"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Status bar banner
                    Surface(
                        color = if (state.isStreaming) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Model • ${state.statusMessage}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // A translation is a chapter-length document, not a chat bubble.
                        // The single bounded reader pane keeps the header fixed while the
                        // native selectable TextView scrolls as streamed chunks are appended.
                        val translation = state.messages.lastOrNull { it.isModel }
                        if (translation != null) {
                            ReaderOutputPane(
                                message = translation,
                                meaningSearchEnabled = state.meaningSearchEnabled,
                                promptGlossaryTerms = state.promptGlossaryTerms,
                                fontSizeSp = fontSizeSp,
                                fontFamily = fontFamily,
                                fontColor = Color(fontColorArgb),
                                onAddReplacement = conversationViewModel::addReplacementFromOutput,
                                showTranslationLoading = state.isStreaming &&
                                    (translation.content == "Translating chapter with Gemma 3n..." || translation.content.isBlank()),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                }
            }
            if (showGlossary) {
                TranslationGlossaryDialog(
                    promptTerms = state.promptGlossaryTerms,
                    replacementTerms = state.replacementGlossaryTerms,
                    onDismiss = { showGlossary = false }
                )
            }
            if (showReaderSettings) {
                ReaderAppearanceDialog(
                    initialFontSize = fontSizeSp,
                    initialFontFamily = fontFamily,
                    initialFontColor = fontColorArgb,
                    onDismiss = { showReaderSettings = false },
                    onApply = { size, family, color ->
                        fontSizeSp = size
                        fontFamily = family
                        fontColorArgb = color
                        readerPreferences.readerFontSizeSp = size
                        readerPreferences.readerFontFamily = family
                        readerPreferences.readerFontColor = color
                        showReaderSettings = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ReaderOutputPane(
    message: ChatItem,
    meaningSearchEnabled: Boolean,
    promptGlossaryTerms: List<GlossaryTermEntity>,
    fontSizeSp: Float,
    fontFamily: String,
    fontColor: Color,
    onAddReplacement: (String, String) -> Unit,
    showTranslationLoading: Boolean,
    modifier: Modifier = Modifier
) {
    ChatMessageBubble(
        message = message,
        meaningSearchEnabled = meaningSearchEnabled,
        promptGlossaryTerms = promptGlossaryTerms,
        fontSizeSp = fontSizeSp,
        fontFamily = fontFamily,
        fontColor = fontColor,
        onAddReplacement = onAddReplacement,
        showTranslationLoading = showTranslationLoading,
        readerStyle = true,
        modifier = modifier
    )
}

@Composable
private fun TranslationGlossaryDialog(
    promptTerms: List<com.example.epubnoveltranslator.data.db.GlossaryTermEntity>,
    replacementTerms: List<com.example.epubnoveltranslator.data.db.GlossaryTermEntity>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chapter glossary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlossaryListSection("Prompt glossary", promptTerms, "No prompt terms.")
                GlossaryListSection("Replacement glossary", replacementTerms, "No replacement terms.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun GlossaryListSection(
    title: String,
    terms: List<com.example.epubnoveltranslator.data.db.GlossaryTermEntity>,
    emptyText: String
) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    if (terms.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodySmall)
    else terms.forEach { Text("${it.sourceTerm} → ${it.targetTerm}", style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun ReaderAppearanceDialog(
    initialFontSize: Float,
    initialFontFamily: String,
    initialFontColor: Int,
    onDismiss: () -> Unit,
    onApply: (Float, String, Int) -> Unit
) {
    var size by remember { mutableFloatStateOf(initialFontSize) }
    var family by remember { mutableStateOf(initialFontFamily) }
    var color by remember { mutableIntStateOf(initialFontColor) }
    val fonts = listOf("serif" to "Literary Serif", "sans-serif" to "Modern Sans", "sans-serif-condensed" to "Compact Novel", "monospace" to "Monospace")
    val colors = listOf(0xFFBDE8F5.toInt(), 0xFFFFFFFF.toInt(), 0xFFD6E6FF.toInt(), 0xFFFFF1D6.toInt())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reader appearance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Text size: ${size.toInt()} sp", style = MaterialTheme.typography.titleSmall)
                Slider(value = size, onValueChange = { size = it }, valueRange = 12f..28f, steps = 15)
                Text("Font", style = MaterialTheme.typography.titleSmall)
                fonts.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (key, label) ->
                            FilterChip(
                                selected = family == key,
                                onClick = { family = key },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Text("Text color", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    colors.forEach { option ->
                        Surface(
                            modifier = Modifier.size(36.dp).clickable { color = option },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = Color(option),
                            border = if (color == option) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
                        ) {}
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(size, family, color) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ChatMessageBubble(
    message: ChatItem,
    meaningSearchEnabled: Boolean = false,
    promptGlossaryTerms: List<GlossaryTermEntity> = emptyList(),
    fontSizeSp: Float = 16f,
    fontFamily: String = "serif",
    fontColor: Color = Color(0xFFBDE8F5),
    onAddReplacement: (String, String) -> Unit = { _, _ -> },
    showTranslationLoading: Boolean = false,
    readerStyle: Boolean = false,
    modifier: Modifier = Modifier
) {
    var replacementSource by remember { mutableStateOf<String?>(null) }
    var meaningQuery by remember { mutableStateOf<String?>(null) }
    var originalTermInfo by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectionActive by remember { mutableStateOf(false) }
    val isModel = message.isModel
    val alignment = if (isModel) Alignment.Start else Alignment.End
    val containerColor = if (isModel) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val textColor = if (isModel) {
        fontColor
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Column(
        modifier = if (isModel) modifier.fillMaxSize() else modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (!readerStyle) {
            Text(
                text = message.sender,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        Surface(
            shape = if (readerStyle) RoundedCornerShape(24.dp) else RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isModel) 4.dp else 16.dp,
                bottomEnd = if (isModel) 16.dp else 4.dp
            ),
            color = containerColor,
            modifier = if (isModel) {
                Modifier.fillMaxWidth().weight(1f)
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            if (isModel) {
                TranslationOutputText(
                    text = message.content,
                    promptGlossaryTerms = promptGlossaryTerms,
                    replacementTerms = message.replacementTerms,
                    meaningSearchEnabled = meaningSearchEnabled,
                    textColor = textColor,
                    fontSizeSp = fontSizeSp,
                    fontFamilyName = fontFamily,
                    onAddReplacement = { replacementSource = it },
                    onSearchMeaning = { meaningQuery = it },
                    onShowOriginal = { source, target -> originalTermInfo = source to target },
                    selectionActive = selectionActive,
                    onSelectionActiveChanged = { selectionActive = it },
                    modifier = Modifier.fillMaxSize().padding(if (readerStyle) 18.dp else 14.dp)
                )
            } else {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
        if (showTranslationLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) { TranslationLoadingGif(modifier = Modifier.fillMaxWidth()) }
        }
    }

    originalTermInfo?.let { (source, target) ->
        AlertDialog(
            onDismissRequest = { originalTermInfo = null },
            title = { Text("Glossary term") },
            text = { Text("“$target” replaced the original term “$source”.") },
            confirmButton = { TextButton(onClick = { originalTermInfo = null }) { Text("OK") } }
        )
    }
    replacementSource?.let { source ->
        var target by remember(source) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { replacementSource = null },
            title = { Text("Add replacement glossary") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Original output: $source")
                    OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Replacement") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (target.isNotBlank()) onAddReplacement(source, target.trim())
                    replacementSource = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { replacementSource = null }) { Text("Cancel") } }
        )
    }
    meaningQuery?.let { query -> MeaningWebViewDialog(query, onDismiss = { meaningQuery = null }) }
}

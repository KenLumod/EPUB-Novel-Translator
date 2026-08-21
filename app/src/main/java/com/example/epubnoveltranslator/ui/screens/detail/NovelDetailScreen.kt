package com.example.epubnoveltranslator.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.epubnoveltranslator.R
import com.example.epubnoveltranslator.data.db.ChapterEntity
import com.example.epubnoveltranslator.data.db.GlossaryTermEntity
import com.example.epubnoveltranslator.data.db.GlossaryKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelDetailScreen(
    novelId: String,
    onBackClick: () -> Unit,
    onChapterClick: (String) -> Unit,
    viewModel: NovelDetailViewModel = viewModel()
) {
    LaunchedEffect(novelId) {
        viewModel.loadNovel(novelId)
    }

    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val translatingChapterIds by viewModel.translatingChapterIds.collectAsStateWithLifecycle()
    val queuedChapterIds by viewModel.queuedChapterIds.collectAsStateWithLifecycle()
    val promptGlossaryTerms by viewModel.promptGlossaryTerms.collectAsStateWithLifecycle()
    val replacementGlossaryTerms by viewModel.replacementGlossaryTerms.collectAsStateWithLifecycle()
    val sortByStatus by viewModel.sortByStatus.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Chapters", "Prompt Glossary", "Replacement", "Prompt", "Notes")

    val novelTitle = novel?.title ?: "Novel Details"
    val novelAuthor = novel?.author ?: ""

    val translatedCount = chapters.count { it.isTranslated }
    val totalCount = chapters.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = novelTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = novelAuthor,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                    if (selectedTabIndex == 0) {
                        IconButton(onClick = { viewModel.toggleSortByStatus() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_sort_vertical),
                                contentDescription = if (sortByStatus) "Sorted by status" else "Sorted by EPUB order",
                                tint = if (sortByStatus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
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
            // Stats bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Chapters: $totalCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Translated: $translatedCount / $totalCount",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Horizontally scrollable Tab Bar
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                divider = { HorizontalDivider() }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> ChaptersTab(
                    chapters = chapters,
                    translatingChapterIds = translatingChapterIds,
                    queuedChapterIds = queuedChapterIds,
                    sortByStatus = sortByStatus,
                    onChapterClick = onChapterClick
                )
                1 -> GlossaryTab(
                    glossaryTerms = promptGlossaryTerms,
                    title = "Prompt Glossary",
                    description = "These rules are included in the translation prompt.",
                    onAddTerm = { source, target, note ->
                        viewModel.addGlossaryTerm(source, target, note, GlossaryKind.PROMPT)
                    },
                    onDeleteTerm = { term ->
                        viewModel.deleteGlossaryTerm(term)
                    }
                )
                2 -> GlossaryTab(
                    glossaryTerms = replacementGlossaryTerms,
                    title = "Replacement Glossary",
                    description = "After translation, replace the original output with your preferred term. Replaced words are bold and italic in the reader.",
                    sourceLabel = "Original output (e.g. Dragon)",
                    targetLabel = "Replacement (e.g. Long)",
                    onAddTerm = { source, target, note ->
                        viewModel.addGlossaryTerm(source, target, note, GlossaryKind.REPLACEMENT)
                    },
                    onDeleteTerm = { term -> viewModel.deleteGlossaryTerm(term) }
                )
                3 -> PromptTab(
                    currentTemplate = novel?.customPromptTemplate ?: DEFAULT_PROMPT_TEMPLATE.trimIndent(),
                    onSaveTemplate = { newPrompt ->
                        viewModel.updatePromptTemplate(newPrompt)
                    },
                    onResetToDefault = {
                        viewModel.resetPromptTemplateToDefault()
                    }
                )
                4 -> NotesTab(
                    currentNotes = novel?.notes ?: "",
                    onSaveNotes = { viewModel.updateNotes(it) }
                )
            }
        }
    }
}

@Composable
fun ChaptersTab(
    chapters: List<ChapterEntity>,
    translatingChapterIds: Set<String>,
    queuedChapterIds: Set<String>,
    sortByStatus: Boolean,
    onChapterClick: (String) -> Unit
) {
    val listState = rememberLazyListState()
    var previousChapters by remember { mutableStateOf(emptyList<ChapterEntity>()) }
    var previousSortByStatus by remember { mutableStateOf(sortByStatus) }

    val sortedChapters = remember(chapters, translatingChapterIds, queuedChapterIds, sortByStatus) {
        if (!sortByStatus) {
            chapters
        } else {
            chapters.sortedWith(
                compareBy(
                    { chapter ->
                        when {
                            chapter.isTranslated -> 0                 // Translated
                            chapter.id in translatingChapterIds -> 1  // Translating
                            chapter.id in queuedChapterIds -> 2       // Queued
                            else -> 3                                 // Untranslated
                        }
                    },
                    { it.chapterOrder }
                )
            )
        }
    }

    LaunchedEffect(sortByStatus) {
        if (previousChapters.isNotEmpty() && previousSortByStatus != sortByStatus) {
            val visibleIndex = listState.firstVisibleItemIndex
            if (visibleIndex in previousChapters.indices) {
                val anchorChapterId = previousChapters[visibleIndex].id
                val newIndex = sortedChapters.indexOfFirst { it.id == anchorChapterId }
                if (newIndex >= 0) {
                    listState.scrollToItem(newIndex, listState.firstVisibleItemScrollOffset)
                }
            }
        }
        previousSortByStatus = sortByStatus
        previousChapters = sortedChapters
    }

    LaunchedEffect(sortedChapters) {
        previousChapters = sortedChapters
    }

    if (chapters.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No chapters found in this novel.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(sortedChapters, key = { it.id }) { chapter ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChapterClick(chapter.id) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (chapter.id in translatingChapterIds) {
                            AssistChip(
                                onClick = { onChapterClick(chapter.id) },
                                label = { Text("Translating") },
                                leadingIcon = {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            )
                        } else if (chapter.id in queuedChapterIds) {
                            AssistChip(
                                onClick = { onChapterClick(chapter.id) },
                                label = { Text("Queued") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        } else if (chapter.isTranslated) {
                            FilterChip(
                                selected = true,
                                onClick = { onChapterClick(chapter.id) },
                                label = { Text("Translated") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        } else {
                            SuggestionChip(
                                onClick = { onChapterClick(chapter.id) },
                                label = { Text("Untranslated") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlossaryTab(
    glossaryTerms: List<GlossaryTermEntity>,
    title: String,
    description: String,
    sourceLabel: String = "Source term",
    targetLabel: String = "Preferred translation",
    onAddTerm: (source: String, target: String, note: String) -> Unit,
    onDeleteTerm: (GlossaryTermEntity) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var sourceInput by remember { mutableStateOf("") }
    var targetInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$title (${glossaryTerms.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Term")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (glossaryTerms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No terms added yet.\n$description",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(glossaryTerms) { term ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${term.sourceTerm} ➔ ${term.targetTerm}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (term.note.isNotEmpty()) {
                                        Text(
                                            text = term.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { onDeleteTerm(term) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add $title Pair") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sourceInput,
                            onValueChange = { sourceInput = it },
                            label = { Text(sourceLabel) }
                        )
                        OutlinedTextField(
                            value = targetInput,
                            onValueChange = { targetInput = it },
                            label = { Text(targetLabel) }
                        )
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            label = { Text("Note (Optional)") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (sourceInput.isNotBlank() && targetInput.isNotBlank()) {
                                onAddTerm(sourceInput, targetInput, noteInput)
                                sourceInput = ""
                                targetInput = ""
                                noteInput = ""
                                showAddDialog = false
                            }
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun PromptTab(
    currentTemplate: String,
    onSaveTemplate: (String) -> Unit,
    onResetToDefault: () -> Unit
) {
    var promptText by remember(currentTemplate) { mutableStateOf(currentTemplate) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Translation Prompt Template",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            TextButton(onClick = onResetToDefault) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset Default")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = promptText,
            onValueChange = {
                promptText = it
                onSaveTemplate(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun NotesTab(
    currentNotes: String,
    onSaveNotes: (String) -> Unit
) {
    var notesText by remember(currentNotes) { mutableStateOf(currentNotes) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Novel Notes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Notes are shared across all chapters of this novel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = notesText,
            onValueChange = {
                notesText = it
                onSaveNotes(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = {
                Text(
                    "Write your notes here… Use **bold** or *italic* markers for formatting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

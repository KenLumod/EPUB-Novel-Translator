package com.example.epubnoveltranslator.ui.screens.conversation

import android.graphics.Rect
import android.graphics.Typeface
import android.content.ClipData
import android.content.ClipboardManager
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.epubnoveltranslator.data.db.GlossaryTermEntity
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val ADD_REPLACEMENT_ID = 1001
private const val SEARCH_MEANING_ID = 1002
private const val COPY_ID = 1003
private val GLOSSARY_HIGHLIGHT_COLOR = android.graphics.Color.argb(66, 86, 109, 255)

@Composable
fun TranslationOutputText(
    text: String,
    promptGlossaryTerms: List<GlossaryTermEntity>,
    replacementTerms: List<GlossaryTermEntity>,
    meaningSearchEnabled: Boolean,
    textColor: Color,
    fontSizeSp: Float,
    fontFamilyName: String,
    onAddReplacement: (String) -> Unit,
    onSearchMeaning: (String) -> Unit,
    onShowOriginal: (String, String) -> Unit,
    selectionActive: Boolean,
    onSelectionActiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val rendered = remember(text, replacementTerms) {
        replacementSpans(markdownSpans(text), replacementTerms, onShowOriginal)
    }
    val isUserTouching = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val textView = TextView(context).apply {
                setTextIsSelectable(true)
                isLongClickable = true
                isFocusable = true
                isFocusableInTouchMode = true
                gravity = Gravity.TOP
                movementMethod = SelectableLinkMovementMethod
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> isUserTouching.set(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isUserTouching.set(false)
                    }
                    false
                }
                customSelectionActionModeCallback = selectionCallback(
                    view = this,
                    meaningSearchEnabled = meaningSearchEnabled,
                    onAddReplacement = onAddReplacement,
                    onSearchMeaning = onSearchMeaning,
                    onSelectionActiveChanged = onSelectionActiveChanged,
                    isUserTouching = isUserTouching
                )
            }
            object : NestedScrollView(context) {
                override fun requestChildRectangleOnScreen(
                    child: View,
                    rectangle: Rect,
                    immediate: Boolean
                ): Boolean {
                    // Prevent TextView cursor/selection layout changes from pulling scroll position back to top
                    return false
                }
            }.apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = true
                isNestedScrollingEnabled = true
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> isUserTouching.set(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isUserTouching.set(false)
                    }
                    false
                }
                addView(textView)
            }
        },
        update = { container ->
            val view = container.getChildAt(0) as TextView
            // A streamed token normally replaces the full output. Do not replace the
            // TextView while Android is showing selection handles or user is touching down;
            // doing so clears the selection and cancels long-press gesture.
            val lastRendered = view.tag as? CharSequence
            if (!selectionActive && !isUserTouching.get() && lastRendered !== rendered) {
                view.tag = rendered
                view.setTextKeepState(rendered, TextView.BufferType.SPANNABLE)
                view.requestLayout()
                view.invalidate()
            }
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            view.setTextColor(textColor.toArgb())
            view.typeface = Typeface.create(fontFamilyName, Typeface.NORMAL)
            view.setLineSpacing(0f, 1.16f)
            if (!selectionActive) {
                view.customSelectionActionModeCallback = selectionCallback(
                    view, meaningSearchEnabled, onAddReplacement, onSearchMeaning, onSelectionActiveChanged, isUserTouching
                )
            }
        }
    )
}

/**
 * Lets Android's selectable TextView retain its normal long-press selection behavior.
 * Scrolling is owned by the surrounding NestedScrollView instead of the movement method.
 */
private object SelectableLinkMovementMethod : android.text.method.ArrowKeyMovementMethod() {
    override fun onTouchEvent(widget: TextView, buffer: android.text.Spannable, event: MotionEvent): Boolean {
        // Handle glossary taps only on ACTION_UP when nothing is selected.
        // Consuming ACTION_DOWN on a ClickableSpan blocks long-press selection.
        if (event.actionMasked == MotionEvent.ACTION_UP && !widget.hasSelection()) {
            val link = clickableSpanAt(widget, buffer, event)
            if (link != null) {
                link.onClick(widget)
                return true
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }

    private fun clickableSpanAt(
        widget: TextView,
        buffer: android.text.Spannable,
        event: MotionEvent
    ): android.text.style.ClickableSpan? {
        val layout = widget.layout ?: return null
        val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
        val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
        if (y < 0 || y > layout.height) return null
        val line = layout.getLineForVertical(y)
        val off = layout.getOffsetForHorizontal(line, x.toFloat())
        return buffer.getSpans(off, off, android.text.style.ClickableSpan::class.java).firstOrNull()
    }
}

private fun selectionCallback(
    view: TextView,
    meaningSearchEnabled: Boolean,
    onAddReplacement: (String) -> Unit,
    onSearchMeaning: (String) -> Unit,
    onSelectionActiveChanged: (Boolean) -> Unit,
    isUserTouching: java.util.concurrent.atomic.AtomicBoolean
) = object : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        onSelectionActiveChanged(true)
        menu.add(0, COPY_ID, 10, "Copy")
        menu.add(0, ADD_REPLACEMENT_ID, 20, "Add replacement")
        if (meaningSearchEnabled) menu.add(0, SEARCH_MEANING_ID, 21, "Search meaning")
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val selected = view.text.subSequence(view.selectionStart, view.selectionEnd).toString().trim()
        if (selected.isBlank()) return false
        when (item.itemId) {
            COPY_ID -> {
                val clipboard = view.context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Translation selection", selected))
            }
            ADD_REPLACEMENT_ID -> onAddReplacement(selected)
            SEARCH_MEANING_ID -> onSearchMeaning(selected)
            else -> return false // Preserve Android's normal Copy / Share actions.
        }
        mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        isUserTouching.set(false)
        onSelectionActiveChanged(false)
    }
}

/**
 * Small, deliberately conservative Markdown renderer for model output. It only
 * consumes delimiters that have a matching closing delimiter, so a partly streamed
 * token such as "**unfinished" remains visible until the model finishes it.
 */
private fun markdownSpans(markdown: String): SpannableStringBuilder {
    val result = SpannableStringBuilder()
    var cursor = 0
    while (cursor < markdown.length) {
        val delimiter = when {
            markdown.startsWith("***", cursor) || markdown.startsWith("___", cursor) -> markdown.substring(cursor, cursor + 3)
            markdown.startsWith("**", cursor) || markdown.startsWith("__", cursor) -> markdown.substring(cursor, cursor + 2)
            markdown[cursor] == '*' || markdown[cursor] == '_' -> markdown[cursor].toString()
            else -> null
        }
        if (delimiter == null) {
            result.append(markdown[cursor])
            cursor += 1
            continue
        }

        val contentStart = cursor + delimiter.length
        val closing = markdown.indexOf(delimiter, contentStart)
        // Empty or unclosed delimiters are regular text, which also makes streamed
        // output stable while the last word is still arriving.
        if (closing <= contentStart) {
            result.append(markdown[cursor])
            cursor += 1
            continue
        }

        val spanStart = result.length
        result.append(markdown, contentStart, closing)
        val style = when (delimiter.length) {
            3 -> Typeface.BOLD_ITALIC
            2 -> Typeface.BOLD
            else -> Typeface.ITALIC
        }
        result.setSpan(StyleSpan(style), spanStart, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        cursor = closing + delimiter.length
    }
    return result
}

private fun replacementSpans(
    original: Spanned,
    replacements: List<GlossaryTermEntity>,
    onShowOriginal: (String, String) -> Unit
): SpannableString {
    val usableTerms = replacements
        .filter { it.sourceTerm.isNotBlank() && it.targetTerm.isNotBlank() }
        .sortedByDescending { it.sourceTerm.length }
    if (usableTerms.isEmpty()) return SpannableString(original)

    // One combined pass means only text that was actually substituted gets emphasis.
    val matcher = Regex(usableTerms.joinToString("|") { Regex.escape(it.sourceTerm) }, RegexOption.IGNORE_CASE)
    val result = SpannableStringBuilder()
    var previousEnd = 0
    matcher.findAll(original.toString()).forEach { match ->
        // Appending the Spanned source preserves Markdown formatting everywhere
        // except the exact word that is intentionally replaced.
        result.append(original, previousEnd, match.range.first)
        val term = usableTerms.first { it.sourceTerm.equals(match.value, ignoreCase = true) }
        val start = result.length
        result.append(term.targetTerm)
        val end = result.length
        result.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        result.setSpan(BackgroundColorSpan(GLOSSARY_HIGHLIGHT_COLOR), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        result.setSpan(object : android.text.style.ClickableSpan() {
            override fun onClick(widget: android.view.View) {
                onShowOriginal(term.sourceTerm, term.targetTerm)
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        previousEnd = match.range.last + 1
    }
    result.append(original, previousEnd, original.length)
    return SpannableString(result)
}


@Composable
fun MeaningWebViewDialog(query: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface {
            Column(Modifier.heightIn(min = 420.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Meaning: $query", modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close") }
                }
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = false
                            loadUrl("https://www.google.com/search?q=" + URLEncoder.encode("$query meaning", StandardCharsets.UTF_8.toString()))
                        }
                    }
                )
            }
        }
    }
}

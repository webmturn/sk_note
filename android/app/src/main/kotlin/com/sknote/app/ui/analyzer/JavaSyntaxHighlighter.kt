package com.sknote.app.ui.analyzer

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.widget.EditText
import java.util.regex.Pattern

object JavaSyntaxHighlighter {

    // Dark theme colors (IntelliJ Darcula)
    private val DARK_KEYWORD = Color.parseColor("#CC7832")
    private val DARK_STRING = Color.parseColor("#6A8759")
    private val DARK_COMMENT = Color.parseColor("#808080")
    private val DARK_NUMBER = Color.parseColor("#6897BB")
    private val DARK_ANNOTATION = Color.parseColor("#BBB529")
    private val DARK_TYPE = Color.parseColor("#FFC66D")

    // Light theme colors (IntelliJ Light)
    private val LIGHT_KEYWORD = Color.parseColor("#0033B3")
    private val LIGHT_STRING = Color.parseColor("#067D17")
    private val LIGHT_COMMENT = Color.parseColor("#8C8C8C")
    private val LIGHT_NUMBER = Color.parseColor("#1750EB")
    private val LIGHT_ANNOTATION = Color.parseColor("#9E880D")
    private val LIGHT_TYPE = Color.parseColor("#000000")

    data class ColorScheme(
        val keyword: Int, val string: Int, val comment: Int,
        val number: Int, val annotation: Int, val type: Int
    )

    private fun getScheme(context: Context): ColorScheme {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (isDark) ColorScheme(DARK_KEYWORD, DARK_STRING, DARK_COMMENT, DARK_NUMBER, DARK_ANNOTATION, DARK_TYPE)
        else ColorScheme(LIGHT_KEYWORD, LIGHT_STRING, LIGHT_COMMENT, LIGHT_NUMBER, LIGHT_ANNOTATION, LIGHT_TYPE)
    }

    private val KEYWORDS = arrayOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new",
        "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while", "true", "false", "null"
    )

    private val KEYWORD_PATTERN = Pattern.compile(
        "\\b(${KEYWORDS.joinToString("|")})\\b"
    )
    private val STRING_PATTERN = Pattern.compile("\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(\\\\.[^'\\\\]*)*'")
    private val SINGLE_LINE_COMMENT = Pattern.compile("//[^\n]*")
    private val MULTI_LINE_COMMENT = Pattern.compile("/\\*[\\s\\S]*?\\*/")
    private val NUMBER_PATTERN = Pattern.compile("\\b\\d+(\\.\\d+)?[fFdDlL]?\\b")
    private val ANNOTATION_PATTERN = Pattern.compile("@\\w+")
    private val TYPE_PATTERN = Pattern.compile("\\b[A-Z][a-zA-Z0-9_]*\\b")

    fun highlight(editable: Spannable, scheme: ColorScheme) {
        // Remove old spans
        val oldSpans = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
        for (span in oldSpans) editable.removeSpan(span)

        // Apply in order: types, numbers, keywords, annotations, strings, comments (later overrides earlier)
        applyPattern(editable, TYPE_PATTERN, scheme.type)
        applyPattern(editable, NUMBER_PATTERN, scheme.number)
        applyPattern(editable, KEYWORD_PATTERN, scheme.keyword)
        applyPattern(editable, ANNOTATION_PATTERN, scheme.annotation)
        applyPattern(editable, STRING_PATTERN, scheme.string)
        applyPattern(editable, SINGLE_LINE_COMMENT, scheme.comment)
        applyPattern(editable, MULTI_LINE_COMMENT, scheme.comment)
    }

    private fun applyPattern(editable: Spannable, pattern: Pattern, color: Int) {
        val matcher = pattern.matcher(editable)
        while (matcher.find()) {
            // Remove any existing spans in this range first
            val existing = editable.getSpans(matcher.start(), matcher.end(), ForegroundColorSpan::class.java)
            for (span in existing) editable.removeSpan(span)
            editable.setSpan(
                ForegroundColorSpan(color),
                matcher.start(), matcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    fun attachTo(editText: EditText) {
        val scheme = getScheme(editText.context)
        editText.addTextChangedListener(object : TextWatcher {
            private var isHighlighting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isHighlighting || s == null) return
                isHighlighting = true
                highlight(s, getScheme(editText.context))
                isHighlighting = false
            }
        })
        // Initial highlight
        editText.text?.let { highlight(it, scheme) }
    }
}

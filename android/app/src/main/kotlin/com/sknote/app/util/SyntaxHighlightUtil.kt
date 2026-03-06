package com.sknote.app.util

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan

object SyntaxHighlightUtil {

    private data class ColorScheme(
        val keyword: Int,
        val string: Int,
        val comment: Int,
        val number: Int,
        val annotation: Int,
        val type: Int
    )

    private val darkScheme = ColorScheme(
        keyword = Color.parseColor("#CC7832"),
        string = Color.parseColor("#6A8759"),
        comment = Color.parseColor("#808080"),
        number = Color.parseColor("#6897BB"),
        annotation = Color.parseColor("#BBB529"),
        type = Color.parseColor("#FFC66D")
    )

    private val lightScheme = ColorScheme(
        keyword = Color.parseColor("#0000FF"),
        string = Color.parseColor("#067D17"),
        comment = Color.parseColor("#8C8C8C"),
        number = Color.parseColor("#1750EB"),
        annotation = Color.parseColor("#808000"),
        type = Color.parseColor("#000080")
    )

    private val javaKeywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new", "package",
        "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient",
        "try", "void", "volatile", "while", "true", "false", "null", "var"
    )

    private val kotlinKeywords = javaKeywords + setOf(
        "fun", "val", "var", "when", "object", "companion", "data", "sealed",
        "override", "open", "internal", "lateinit", "by", "lazy", "suspend",
        "inline", "reified", "crossinline", "noinline", "typealias", "is", "as",
        "in", "out", "it", "init", "constructor"
    )

    private val jsKeywords = setOf(
        "async", "await", "break", "case", "catch", "class", "const", "continue",
        "debugger", "default", "delete", "do", "else", "export", "extends", "false",
        "finally", "for", "from", "function", "if", "import", "in", "instanceof",
        "let", "new", "null", "of", "return", "static", "super", "switch", "this",
        "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "yield"
    )

    private val pythonKeywords = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break",
        "class", "continue", "def", "del", "elif", "else", "except", "finally",
        "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
        "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
        "self", "print"
    )

    private fun getKeywords(language: String): Set<String> {
        return when (language.lowercase()) {
            "java" -> javaKeywords
            "kotlin", "kt" -> kotlinKeywords
            "javascript", "js", "typescript", "ts" -> jsKeywords
            "python", "py" -> pythonKeywords
            "c", "cpp", "c++" -> javaKeywords + setOf(
                "auto", "register", "sizeof", "struct", "typedef", "union", "unsigned",
                "signed", "extern", "inline", "template", "namespace", "using",
                "virtual", "operator", "delete", "nullptr", "bool", "string"
            )
            else -> javaKeywords
        }
    }

    fun highlight(code: String, language: String, isDarkTheme: Boolean): CharSequence {
        val scheme = if (isDarkTheme) darkScheme else lightScheme
        val keywords = getKeywords(language)
        val builder = SpannableStringBuilder(code)

        val ranges = mutableListOf<IntRange>()

        // 1. Single-line comments
        val singleComment = if (language.lowercase() == "python" || language.lowercase() == "py") {
            Regex("#[^\n]*")
        } else {
            Regex("//[^\n]*")
        }
        for (match in singleComment.findAll(code)) {
            setSpan(builder, match.range, scheme.comment)
            ranges.add(match.range)
        }

        // 2. Multi-line comments
        val multiComment = Regex("/\\*[\\s\\S]*?\\*/")
        for (match in multiComment.findAll(code)) {
            setSpan(builder, match.range, scheme.comment)
            ranges.add(match.range)
        }

        // 3. Strings (double-quoted and single-quoted)
        val stringPattern = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'")
        for (match in stringPattern.findAll(code)) {
            if (!isOverlapping(ranges, match.range)) {
                setSpan(builder, match.range, scheme.string)
                ranges.add(match.range)
            }
        }

        // 4. Annotations
        val annotationPattern = Regex("@\\w+")
        for (match in annotationPattern.findAll(code)) {
            if (!isOverlapping(ranges, match.range)) {
                setSpan(builder, match.range, scheme.annotation)
                ranges.add(match.range)
            }
        }

        // 5. Numbers
        val numberPattern = Regex("\\b\\d+(\\.\\d+)?[fFdDlL]?\\b")
        for (match in numberPattern.findAll(code)) {
            if (!isOverlapping(ranges, match.range)) {
                setSpan(builder, match.range, scheme.number)
                ranges.add(match.range)
            }
        }

        // 6. Keywords
        val wordPattern = Regex("\\b\\w+\\b")
        for (match in wordPattern.findAll(code)) {
            if (match.value in keywords && !isOverlapping(ranges, match.range)) {
                setSpan(builder, match.range, scheme.keyword)
                ranges.add(match.range)
            }
        }

        return builder
    }

    private fun setSpan(builder: SpannableStringBuilder, range: IntRange, color: Int) {
        builder.setSpan(
            ForegroundColorSpan(color),
            range.first,
            range.last + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun isOverlapping(ranges: List<IntRange>, target: IntRange): Boolean {
        return ranges.any { it.first <= target.last && target.first <= it.last }
    }
}

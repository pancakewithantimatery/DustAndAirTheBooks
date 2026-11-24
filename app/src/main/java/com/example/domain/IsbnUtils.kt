package com.example.domain

data class IsbnPair(
    val isbn13: String,
    val isbn10: String
)

data class ParsedIsbn(
    val isValid: Boolean,
    val inputType: String,
    val isbn13: String,
    val isbn10: String,
    val cleaned: String
)

object IsbnUtils {

    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace(Regex("[^0-9Xx]"), "").uppercase()
    }

    fun isValidIsbn10(code: String): Boolean {
        if (!Regex("^[0-9]{9}[0-9X]$").matches(code)) return false
        var sum = 0
        for (i in 0 until 10) {
            val value = if (code[i] == 'X') 10 else code[i].digitToInt()
            sum += value * (10 - i)
        }
        return sum % 11 == 0
    }

    fun isValidIsbn13(code: String): Boolean {
        if (!Regex("^[0-9]{13}$").matches(code)) return false
        var sum = 0
        for (i in 0 until 13) {
            sum += code[i].digitToInt() * if (i % 2 == 0) 1 else 3
        }
        return sum % 10 == 0
    }

    fun toIsbn13(isbn10: String): String {
        val cleaned = clean(isbn10)
        if (!isValidIsbn10(cleaned)) return ""
        val core = "978" + cleaned.substring(0, 9)
        var sum = 0
        for (i in 0 until 12) {
            sum += core[i].digitToInt() * if (i % 2 == 0) 1 else 3
        }
        val checkDigit = (10 - (sum % 10)) % 10
        return core + checkDigit.toString()
    }

    fun toIsbn10(isbn13: String): String {
        val cleaned = clean(isbn13)
        if (!isValidIsbn13(cleaned) || !cleaned.startsWith("978")) return ""
        val core = cleaned.substring(3, 12)
        var sum = 0
        for (i in 0 until 9) {
            sum += core[i].digitToInt() * (10 - i)
        }
        val remainder = (11 - (sum % 11)) % 11
        val checkChar = if (remainder == 10) "X" else remainder.toString()
        return core + checkChar
    }

    fun normalizePair(rawIsbn13: String?, rawIsbn10: String?): IsbnPair {
        var i13 = clean(rawIsbn13)
        var i10 = clean(rawIsbn10)

        if (i13.length == 10 && i10.isEmpty()) {
            i10 = i13
            i13 = ""
        }
        if (i10.length == 13 && i13.isEmpty()) {
            i13 = i10
            i10 = ""
        }

        if (isValidIsbn10(i10) && !isValidIsbn13(i13)) {
            i13 = toIsbn13(i10)
        }
        if (isValidIsbn13(i13) && !isValidIsbn10(i10)) {
            i10 = toIsbn10(i13)
        }

        return IsbnPair(isbn13 = i13, isbn10 = i10)
    }

    fun parseSingle(raw: String?): ParsedIsbn {
        val cleaned = clean(raw)
        return when {
            cleaned.length == 10 && isValidIsbn10(cleaned) -> ParsedIsbn(
                isValid = true,
                inputType = "ISBN-10",
                isbn13 = toIsbn13(cleaned),
                isbn10 = cleaned,
                cleaned = cleaned
            )
            cleaned.length == 13 && isValidIsbn13(cleaned) -> ParsedIsbn(
                isValid = true,
                inputType = "ISBN-13",
                isbn13 = cleaned,
                isbn10 = toIsbn10(cleaned),
                cleaned = cleaned
            )
            else -> ParsedIsbn(
                isValid = false,
                inputType = "",
                isbn13 = "",
                isbn10 = "",
                cleaned = cleaned
            )
        }
    }

    fun formatPublishDate(year: String, month: String, day: String): String {
        val y = year.trim()
        val m = month.trim()
        val d = day.trim()
        if (y.isEmpty()) return ""
        return when {
            m.isNotEmpty() && d.isNotEmpty() -> "$y-${m.padStart(2, '0')}-${d.padStart(2, '0')}"
            m.isNotEmpty() -> "$y-${m.padStart(2, '0')}"
            else -> y
        }
    }
}

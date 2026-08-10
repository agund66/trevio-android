package com.trevio.android.util

object StringUtils {
    /** Removes non-numeric characters, keeping digits and decimal point. */
    fun sanitizeNumberInput(value: String): String = value.filter { it.isDigit() || it == '.' }

    /** Normalizes a username: lowercase, only alphanumeric, dots, underscores. */
    fun normalizeUsername(username: String): String =
        username.lowercase().replace(Regex("[^a-z0-9._]"), "")

    /** Removes all non-digit characters from a phone number. */
    fun sanitizePhoneNumber(phone: String): String = phone.replace(Regex("\\D"), "")

    /** Capitalizes the first letter of a string. */
    fun toTitleCase(str: String): String =
        if (str.isEmpty()) str else str[0].uppercase() + str.drop(1)

    /** Case-insensitive substring search. */
    fun caseInsensitiveIncludes(text: String, query: String): Boolean {
        if (query.isBlank()) return true
        return text.lowercase().contains(query.lowercase())
    }

    /** Checks if a string is non-empty after trimming. */
    fun isNonEmptyString(value: String?): Boolean =
        !value.isNullOrBlank()

    /** Generates a unique ID using timestamp + random string. */
    fun generateId(prefix: String? = null): String {
        val id = System.currentTimeMillis().toString(36) + (1..6).map { ('a'..'z').random() }.joinToString("")
        return prefix?.let { "${it}_$id" } ?: id
    }
}

/** Converts an enum to its lowercase storage string. */
fun <T : Enum<T>> T.toStorageString(): String = this.name.lowercase()

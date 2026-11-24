package com.example.domain

import java.util.UUID

enum class AiModelOption(
    val modelId: String,
    val label: String,
    val badge: String?,
    val requiresPaidTier: Boolean,
    val description: String
) {
    FAST_FLASH(
        modelId = "gemini-3.8-flash",
        label = "gemini-3.8-flash",
        badge = "Default",
        requiresPaidTier = false,
        description = "Fast ISBN extraction, Vision OCR, and Google Search bibliographic grounding."
    ),
    DEEP_ARGON(
        modelId = "gemini-4-argon",
        label = "gemini-4-argon",
        badge = "Paid / Blaze",
        requiresPaidTier = true,
        description = "Deep bibliographic research, obscure 1990s/2000s editions & rich lore analysis. Requires Paid API key or Blaze plan."
    ),
    LITE_BATCH(
        modelId = "gemini-3.5-flash-lite",
        label = "gemini-3.5-flash-lite",
        badge = "Ultra-Fast",
        requiresPaidTier = false,
        description = "High-speed lightweight cataloging for rapid shelf scanning sessions."
    ),
    CUSTOM(
        modelId = "custom",
        label = "Custom Model ID",
        badge = "BYOK",
        requiresPaidTier = true,
        description = "Use any specific Gemini model ID from Google AI Studio with your paid API key."
    );

    fun resolveModelId(customModelId: String): String {
        return if (this == CUSTOM) {
            customModelId.trim().ifEmpty { FAST_FLASH.modelId }
        } else {
            modelId
        }
    }

    companion object {
        fun fromModelId(id: String?): AiModelOption {
            return entries.firstOrNull { it.modelId == id } ?: FAST_FLASH
        }
    }
}

data class GroundingSource(
    val title: String,
    val uri: String
)

data class BookRecord(
    val id: String = "book_${UUID.randomUUID()}",
    val title: String = "",
    val authors: String = "",
    val description: String = "",
    val publisher: String = "",
    val publishYear: String = "",
    val publishMonth: String = "",
    val publishDay: String = "",
    val isbn13: String = "",
    val isbn10: String = "",
    val pages: Int = 0,
    val group: String = "Home Library",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val formattedPublishDate: String
        get() = IsbnUtils.formatPublishDate(publishYear, publishMonth, publishDay)
}

data class BookLookupResult(
    val record: BookRecord,
    val sources: List<GroundingSource>
)

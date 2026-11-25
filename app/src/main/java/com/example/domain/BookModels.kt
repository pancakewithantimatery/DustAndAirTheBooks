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
    LITE_BATCH(
        modelId = "gemini-3.5-flash-lite",
        label = "gemini-3.5-flash-lite",
        badge = "Lite",
        requiresPaidTier = false,
        description = "Ultra-fast lightweight cataloging for rapid shelf scanning sessions."
    ),
    DEEP_ARGON(
        modelId = "gemini-4-argon",
        label = "gemini-4-argon",
        badge = "Paid",
        requiresPaidTier = true,
        description = "Deep bibliographic research, obscure 1990s/2000s editions & rich lore analysis."
    ),
    CUSTOM(
        modelId = "custom",
        label = "Custom Model ID",
        badge = "BYOK",
        requiresPaidTier = true,
        description = "Use any verified Gemini model ID available to your Google AI Studio API key."
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

        fun presetMatchingId(modelId: String): AiModelOption? {
            return entries.firstOrNull { it != CUSTOM && it.modelId.equals(modelId, ignoreCase = true) }
        }
    }
}

data class DiscoveredGeminiModel(
    val id: String,
    val displayName: String,
    val description: String
)

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

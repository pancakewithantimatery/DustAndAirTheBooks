package com.example.domain

data class ArchivistTier(
    val name: String,
    val minBooks: Int
)

data class ArchivistProgress(
    val currentRank: String,
    val totalBooks: Int,
    val totalPages: Int,
    val progressFraction: Float,
    val nextRankHint: String,
    val tiers: List<Pair<ArchivistTier, Boolean>>
)

object ArchivistRankEngine {

    val tiers: List<ArchivistTier> = listOf(
        ArchivistTier("Novice Scribe", 1),
        ArchivistTier("Shelf Keeper", 10),
        ArchivistTier("Lore Master", 50),
        ArchivistTier("Grand Librarian", 100)
    )

    fun calculate(totalBooks: Int, totalPages: Int): ArchivistProgress {
        var currentRank = "Initiate"
        var nextTier: ArchivistTier? = tiers.first()

        for (index in tiers.indices) {
            if (totalBooks >= tiers[index].minBooks) {
                currentRank = tiers[index].name
                nextTier = tiers.getOrNull(index + 1)
            }
        }

        val progress = if (nextTier != null) {
            (totalBooks.toFloat() / nextTier.minBooks.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }

        val hint = if (nextTier != null) {
            val remaining = (nextTier.minBooks - totalBooks).coerceAtLeast(1)
            val noun = if (remaining == 1) "book" else "books"
            "$remaining more $noun to unlock ${nextTier.name}"
        } else {
            "Maximum Archivist Rank achieved"
        }

        return ArchivistProgress(
            currentRank = currentRank,
            totalBooks = totalBooks,
            totalPages = totalPages,
            progressFraction = progress,
            nextRankHint = hint,
            tiers = tiers.map { it to (totalBooks >= it.minBooks) }
        )
    }
}

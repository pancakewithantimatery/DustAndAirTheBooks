package com.example

import com.example.data.export.LibraryExporter
import com.example.domain.ArchivistRankEngine
import com.example.domain.BookRecord
import com.example.domain.IsbnUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class IsbnAndExporterTest {

    @Test
    fun isbn10ToIsbn13AndBack_standardAndTrailingX() {
        val parsed10 = IsbnUtils.parseSingle("0-14-028329-3")
        assertTrue(parsed10.isValid)
        assertEquals("ISBN-10", parsed10.inputType)
        assertEquals("0140283293", parsed10.isbn10)
        assertEquals("9780140283297", parsed10.isbn13)

        val parsed13 = IsbnUtils.parseSingle("9780140283297")
        assertTrue(parsed13.isValid)
        assertEquals("ISBN-13", parsed13.inputType)
        assertEquals("0140283293", parsed13.isbn10)

        // Trailing X check digit: 0-8044-2957-X -> 9780804429573
        val withX = IsbnUtils.parseSingle("0-8044-2957-x")
        assertTrue(withX.isValid)
        assertEquals("080442957X", withX.isbn10)
        assertEquals("9780804429573", withX.isbn13)
        assertEquals("080442957X", IsbnUtils.toIsbn10("9780804429573"))

        assertFalse(IsbnUtils.parseSingle("9780140283290").isValid)
    }

    @Test
    fun archivistRankProgression_thresholds() {
        assertEquals("Initiate", ArchivistRankEngine.calculate(0, 0).currentRank)
        assertEquals("Novice Scribe", ArchivistRankEngine.calculate(1, 320).currentRank)
        assertEquals("Shelf Keeper", ArchivistRankEngine.calculate(10, 3200).currentRank)
        assertEquals("Lore Master", ArchivistRankEngine.calculate(50, 15000).currentRank)
        assertEquals("Grand Librarian", ArchivistRankEngine.calculate(100, 42000).currentRank)
    }

    @Test
    fun exporters_libibCsvAndMarkdownAndObsidianZip() {
        val sample = listOf(
            BookRecord(
                id = "book_1",
                title = "Dune, Illustrated Edition",
                authors = "Frank Herbert",
                description = "Desert planet Arrakis political & ecological epic.",
                publisher = "Chilton Books (Philadelphia)",
                publishYear = "1965",
                publishMonth = "08",
                publishDay = "01",
                isbn13 = "9780441172719",
                isbn10 = "0441172717",
                pages = 412,
                group = "Sci-Fi",
                notes = "First edition notes",
                tags = listOf("sci-fi", "ecology", "classic")
            )
        )

        val csv = LibraryExporter.generateLibibCsv(sample)
        assertTrue(csv.startsWith("\uFEFFTitle,Authors,Description,Publisher,Publish Date,ISBN 13,ISBN 10,Pages,Group,Notes,Tags"))
        assertTrue(csv.contains("\"Dune, Illustrated Edition\""))
        assertTrue(csv.contains("1965-08-01"))

        val md = LibraryExporter.generateCombinedMarkdown(sample)
        assertTrue(md.contains("title: \"Dune, Illustrated Edition\""))
        assertTrue(md.contains("isbn_13: \"9780441172719\""))
        assertTrue(md.contains("pages: 412"))

        val zipBytes = ByteArrayOutputStream()
        LibraryExporter.writeObsidianZip(sample, zipBytes)
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                entry = zis.nextEntry
            }
        }
        assertEquals(1, entries.size)
        assertTrue(entries.first().startsWith("TomeScanner-Vault/"))
        assertTrue(entries.first().endsWith(".md"))
    }
}

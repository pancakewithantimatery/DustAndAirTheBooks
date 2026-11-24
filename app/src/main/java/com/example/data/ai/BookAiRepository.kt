package com.example.data.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.domain.BookLookupResult
import com.example.domain.BookRecord
import com.example.domain.GroundingSource
import com.example.domain.IsbnUtils
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "BookAiRepository"
private const val AI_TIMEOUT_MS = 60_000L
private const val HTTP_CONNECT_TIMEOUT_MS = 20_000
private const val HTTP_READ_TIMEOUT_MS = 55_000
private const val MAX_IMAGE_DIMENSION = 1600

class BookAiRepository {

    private val bookResponseSchema: Schema by lazy {
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Full book title including subtitle if relevant"),
                "authors" to Schema.string(description = "Comma-separated list of authors"),
                "description" to Schema.string(description = "Concise bibliographic summary and subject matter"),
                "publisher" to Schema.string(description = "Publisher name, including city if available"),
                "publish_year" to Schema.string(description = "4-digit publication year YYYY or empty string"),
                "publish_month" to Schema.string(description = "2-digit publication month MM or empty string"),
                "publish_day" to Schema.string(description = "2-digit publication day DD or empty string"),
                "isbn_13" to Schema.string(description = "13-digit ISBN with no hyphens"),
                "isbn_10" to Schema.string(description = "10-character ISBN with no hyphens"),
                "pages" to Schema.integer(description = "Total page count as integer, 0 if unknown"),
                "group" to Schema.string(description = "Shelf or library group name"),
                "notes" to Schema.string(description = "Edition details, translation, or reprint information"),
                "tags" to Schema.array(
                    items = Schema.string(),
                    description = "3 to 5 concise topic tags"
                )
            )
        )
    }

    private val modelCache = ConcurrentHashMap<String, GenerativeModel>()

    private fun getFirebaseModel(modelId: String): GenerativeModel {
        return modelCache.getOrPut(modelId) {
            Firebase.ai.generativeModel(
                modelName = modelId,
                generationConfig = generationConfig {
                    temperature = 0.2f
                    responseMimeType = "application/json"
                    responseSchema = bookResponseSchema
                },
                tools = listOf(Tool.googleSearch())
            )
        }
    }

    suspend fun lookupByIsbnOrQuery(
        query: String,
        modelId: String,
        defaultGroup: String,
        customApiKey: String? = null
    ): BookLookupResult = withContext(Dispatchers.IO) {
        val parsed = IsbnUtils.parseSingle(query)
        val identifiersText = if (parsed.isValid) {
            "ISBN-13: ${parsed.isbn13.ifEmpty { "N/A" }}, ISBN-10: ${parsed.isbn10.ifEmpty { "N/A" }}"
        } else {
            "Query / ISBN: ${query.trim()}"
        }

        val prompt = buildString {
            append("Look up the exact bibliographic record for this physical book using Google Search ($identifiersText). ")
            append("Pay special attention to regional, non-English, or 1990s/2000s editions matching the exact ISBN. ")
            append("Set \"group\" to \"$defaultGroup\". ")
            append("Provide 3 to 5 concise topic tags in \"tags\" and concise edition/reprint details in \"notes\". ")
            append("Use empty string for unknown text fields and 0 for unknown page counts.")
        }

        val activeKey = customApiKey?.trim().orEmpty()
        if (activeKey.isNotEmpty()) {
            return@withContext callGeminiDirectApi(
                apiKey = activeKey,
                modelId = modelId,
                prompt = prompt,
                imageBitmap = null,
                fallbackQuery = query,
                defaultGroup = defaultGroup
            )
        }

        val response = withTimeout(AI_TIMEOUT_MS) {
            getFirebaseModel(modelId).generateContent(prompt)
        }

        val rawJson = response.text
        if (rawJson.isNullOrBlank()) {
            throw IllegalStateException("Gemini returned an empty bibliographic record. Please try again.")
        }

        val sources = extractFirebaseGroundingSources(response)
        val record = parseBookRecord(
            rawJson = rawJson,
            fallbackQuery = query,
            defaultGroup = defaultGroup
        )
        BookLookupResult(record = record, sources = sources)
    }

    suspend fun lookupByPhoto(
        bitmap: Bitmap,
        modelId: String,
        defaultGroup: String,
        customApiKey: String? = null
    ): BookLookupResult = withContext(Dispatchers.IO) {
        val scaledBitmap = scaleBitmapForVision(bitmap)

        val prompt = buildString {
            append("Inspect this book cover, barcode, copyright page, or title page image. ")
            append("Extract any visible ISBN-10, ISBN-13, title, author, publisher, and edition details. ")
            append("Then use Google Search to verify and complete the exact bibliographic metadata for this specific edition. ")
            append("Set \"group\" to \"$defaultGroup\". ")
            append("Provide 3 to 5 concise topic tags in \"tags\" and concise edition/reprint details in \"notes\". ")
            append("Use empty string for unknown text fields and 0 for unknown page counts.")
        }

        val activeKey = customApiKey?.trim().orEmpty()
        if (activeKey.isNotEmpty()) {
            return@withContext callGeminiDirectApi(
                apiKey = activeKey,
                modelId = modelId,
                prompt = prompt,
                imageBitmap = scaledBitmap,
                fallbackQuery = "",
                defaultGroup = defaultGroup
            )
        }

        val response = withTimeout(AI_TIMEOUT_MS) {
            getFirebaseModel(modelId).generateContent(
                content {
                    image(scaledBitmap)
                    text(prompt)
                }
            )
        }

        val rawJson = response.text
        if (rawJson.isNullOrBlank()) {
            throw IllegalStateException("Gemini could not extract bibliographic data from the photo.")
        }

        val sources = extractFirebaseGroundingSources(response)
        val record = parseBookRecord(
            rawJson = rawJson,
            fallbackQuery = "",
            defaultGroup = defaultGroup
        )
        BookLookupResult(record = record, sources = sources)
    }

    /**
     * Direct Gemini REST API call used ONLY when the user explicitly provides
     * their own Paid / Google AI Studio API key (BYOK) in Settings.
     */
    private suspend fun callGeminiDirectApi(
        apiKey: String,
        modelId: String,
        prompt: String,
        imageBitmap: Bitmap?,
        fallbackQuery: String,
        defaultGroup: String
    ): BookLookupResult = withTimeout(AI_TIMEOUT_MS) {
        val encodedModel = URLEncoder.encode(modelId.trim(), "UTF-8")
        val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/$encodedModel:generateContent")

        val parts = JSONArray()
        if (imageBitmap != null) {
            val base64Jpeg = encodeBitmapToJpegBase64(imageBitmap)
            parts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", base64Jpeg)
                )
            )
        }
        parts.put(JSONObject().put("text", prompt))

        val payload = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", parts)
                )
            )
            .put(
                "tools",
                JSONArray().put(JSONObject().put("google_search", JSONObject()))
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", buildRestBookResponseSchema())
            )

        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = HTTP_CONNECT_TIMEOUT_MS
            readTimeout = HTTP_READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            connection.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            val responseText = (if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (statusCode !in 200..299) {
                val parsedError = try {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                } catch (_: Exception) {
                    null
                }
                val message = parsedError?.takeIf { it.isNotBlank() }
                    ?: "Gemini API error ($statusCode). Check your API key, billing plan, and model ID."
                throw IllegalStateException(message)
            }

            val root = JSONObject(responseText)
            val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
            val contentParts = candidate?.optJSONObject("content")?.optJSONArray("parts")
            val combinedText = buildString {
                if (contentParts != null) {
                    for (i in 0 until contentParts.length()) {
                        val partText = contentParts.optJSONObject(i)?.optString("text", "").orEmpty()
                        append(partText)
                    }
                }
            }.trim()

            if (combinedText.isBlank()) {
                throw IllegalStateException("Gemini returned an empty response. Try again or switch model.")
            }

            val cleanedJson = stripOptionalCodeFences(combinedText)
            val sources = extractRestGroundingSources(candidate)
            val record = parseBookRecord(
                rawJson = cleanedJson,
                fallbackQuery = fallbackQuery,
                defaultGroup = defaultGroup
            )
            BookLookupResult(record = record, sources = sources)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildRestBookResponseSchema(): JSONObject {
        val properties = JSONObject()
            .put("title", JSONObject().put("type", "STRING").put("description", "Full book title including subtitle if relevant"))
            .put("authors", JSONObject().put("type", "STRING").put("description", "Comma-separated list of authors"))
            .put("description", JSONObject().put("type", "STRING").put("description", "Concise bibliographic summary and subject matter"))
            .put("publisher", JSONObject().put("type", "STRING").put("description", "Publisher name, including city if available"))
            .put("publish_year", JSONObject().put("type", "STRING").put("description", "4-digit publication year YYYY or empty string"))
            .put("publish_month", JSONObject().put("type", "STRING").put("description", "2-digit month MM or empty string"))
            .put("publish_day", JSONObject().put("type", "STRING").put("description", "2-digit day DD or empty string"))
            .put("isbn_13", JSONObject().put("type", "STRING").put("description", "13-digit ISBN with no hyphens"))
            .put("isbn_10", JSONObject().put("type", "STRING").put("description", "10-character ISBN with no hyphens"))
            .put("pages", JSONObject().put("type", "INTEGER").put("description", "Total page count as integer, 0 if unknown"))
            .put("group", JSONObject().put("type", "STRING").put("description", "Shelf or library group name"))
            .put("notes", JSONObject().put("type", "STRING").put("description", "Edition details, translation, or reprint information"))
            .put(
                "tags",
                JSONObject()
                    .put("type", "ARRAY")
                    .put("items", JSONObject().put("type", "STRING"))
                    .put("description", "3 to 5 concise topic tags")
            )

        val required = JSONArray(
            listOf(
                "title", "authors", "description", "publisher",
                "publish_year", "publish_month", "publish_day",
                "isbn_13", "isbn_10", "pages", "group", "notes", "tags"
            )
        )

        return JSONObject()
            .put("type", "OBJECT")
            .put("properties", properties)
            .put("required", required)
    }

    private fun parseBookRecord(
        rawJson: String,
        fallbackQuery: String,
        defaultGroup: String
    ): BookRecord {
        val json = JSONObject(rawJson)
        val rawIsbn13 = json.optString("isbn_13", "")
        val rawIsbn10 = json.optString("isbn_10", "")
        var pair = IsbnUtils.normalizePair(rawIsbn13, rawIsbn10)

        if (pair.isbn13.isEmpty() && pair.isbn10.isEmpty() && fallbackQuery.isNotBlank()) {
            val fromQuery = IsbnUtils.parseSingle(fallbackQuery)
            if (fromQuery.isValid) {
                pair = IsbnUtils.normalizePair(fromQuery.isbn13, fromQuery.isbn10)
            }
        }

        val tagsList = buildList {
            val arr = json.optJSONArray("tags")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val tag = arr.optString(i).trim()
                    if (tag.isNotEmpty()) add(tag)
                }
            }
        }

        val yearDigits = json.optString("publish_year", "").filter { it.isDigit() }.take(4)
        val monthDigits = json.optString("publish_month", "").filter { it.isDigit() }.take(2)
        val dayDigits = json.optString("publish_day", "").filter { it.isDigit() }.take(2)
        val pagesCount = json.optInt("pages", 0).coerceAtLeast(0)
        val shelfGroup = json.optString("group", "").trim().ifEmpty { defaultGroup }

        return BookRecord(
            title = json.optString("title", "").trim(),
            authors = json.optString("authors", "").trim(),
            description = json.optString("description", "").trim(),
            publisher = json.optString("publisher", "").trim(),
            publishYear = yearDigits,
            publishMonth = monthDigits,
            publishDay = dayDigits,
            isbn13 = pair.isbn13,
            isbn10 = pair.isbn10,
            pages = pagesCount,
            group = shelfGroup,
            notes = json.optString("notes", "").trim(),
            tags = tagsList
        )
    }

    private fun extractFirebaseGroundingSources(
        response: com.google.firebase.ai.type.GenerateContentResponse
    ): List<GroundingSource> {
        return try {
            val chunks = response.candidates.firstOrNull()?.groundingMetadata?.groundingChunks
            chunks.orEmpty().mapNotNull { chunk ->
                val web = chunk.web
                val uri = web?.uri?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = web.title?.takeIf { it.isNotBlank() } ?: uri
                GroundingSource(title = title, uri = uri)
            }.distinctBy { it.uri }.take(5)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to extract grounding metadata", e)
            emptyList()
        }
    }

    private fun extractRestGroundingSources(candidate: JSONObject?): List<GroundingSource> {
        return try {
            val chunks = candidate
                ?.optJSONObject("groundingMetadata")
                ?.optJSONArray("groundingChunks")
                ?: return emptyList()
            buildList {
                for (i in 0 until chunks.length()) {
                    val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                    val uri = web.optString("uri", "").trim()
                    if (uri.isEmpty()) continue
                    val title = web.optString("title", "").trim().ifEmpty { uri }
                    add(GroundingSource(title = title, uri = uri))
                }
            }.distinctBy { it.uri }.take(5)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to parse REST grounding chunks", e)
            emptyList()
        }
    }

    private fun stripOptionalCodeFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .replaceFirst(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replaceFirst(Regex("\\s*```$"), "")
            .trim()
    }

    private fun encodeBitmapToJpegBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 84, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleBitmapForVision(source: Bitmap): Bitmap {
        val maxSide = maxOf(source.width, source.height)
        if (maxSide <= MAX_IMAGE_DIMENSION) return source
        val scale = min(
            MAX_IMAGE_DIMENSION.toFloat() / source.width.toFloat(),
            MAX_IMAGE_DIMENSION.toFloat() / source.height.toFloat()
        )
        val targetW = (source.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }
}

package com.clipforge.mobile.ai

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val SCHEME = "https://"
    private const val GEMINI_HOST = "generativelanguage.googleapis.com"
    private const val YT_HOST = "www.youtube.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun verifyApiKey(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val cleanModel = model.trim().ifBlank { "gemini-2.0-flash" }
        if (cleanKey.isBlank()) return@withContext Result.failure(Exception("API ключ не указан"))
        try {
            val url = "${SCHEME}${GEMINI_HOST}/v1beta/models/$cleanModel?key=$cleanKey"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success("Подключено ($cleanModel)")
                } else {
                    Result.failure(Exception("Ошибка ${resp.code}: проверь ключ или имя модели"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Нет сети: ${e.localizedMessage}"))
        }
    }

    suspend fun fetchYouTubeTitle(youtubeUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(youtubeUrl.trim(), "UTF-8")
            val url = "${SCHEME}${YT_HOST}/oembed?url=$encoded&format=json"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val title = json.optString("title", "YouTube видео")
                    val author = json.optString("author_name", "")
                    Result.success(if (author.isNotBlank()) "$title — $author" else title)
                } else {
                    Result.failure(Exception("Не удалось получить данные видео"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка ссылки: ${e.localizedMessage}"))
        }
    }

    // Лёгкий AI-запрос для Text & Motion Studio (без отправки видео!)
    suspend fun runTextStudioCommand(
        apiKey: String,
        model: String,
        userCommand: String,
        currentText: String
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val cleanModel = model.trim().ifBlank { "gemini-2.0-flash" }
        if (cleanKey.isBlank()) return@withContext Result.failure(Exception("Укажи Gemini API Key в Настройках"))

        try {
            val prompt = """
                Ты AI-ассистент мобильного видеоредактора Text & Motion Studio.
                Текущий текст на экране: "$currentText"
                Команда пользователя: "$userCommand"
                
                Верни СТРОГО JSON-объект без markdown:
                {
                  "reply": "Короткий ответ пользователю на русском (1 предложение)",
                  "new_text": "Новый текст (или null, если менять текст не просили)",
                  "preset": "MINIMAL_WHITE | CINEMATIC | BOLD_CAPTION | NEON | RETRO | EDITORIAL | LUXURY | DARK_AESTHETIC | null",
                  "animation": "NONE | FADE_IN | SLIDE_UP | ZOOM_IN | POP | TYPEWRITER | PULSE | FLOATING | null",
                  "pos_y": 0.2 или 0.5 или 0.8 (или null, если позицию менять не нужно),
                  "has_glow": true или false (или null)
                }
            """.trimIndent()

            val payload = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.5))

            val url = "${SCHEME}${GEMINI_HOST}/v1beta/models/$cleanModel:generateContent?key=$cleanKey"
            val req = Request.Builder().url(url).post(payload.toString().toRequestBody("application/json".toMediaType())).build()

            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    val errMsg = try { JSONObject(bodyStr).getJSONObject("error").getString("message") } catch (_: Exception) { "Код ${resp.code}" }
                    throw Exception("Ошибка AI (${resp.code}): $errMsg")
                }
                val textResult = JSONObject(bodyStr)
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                    .getString("text").trim()
                    .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                Result.success(JSONObject(textResult))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildPrompt(mode: SearchMode, customPrompt: String, bannerRules: List<String>): String {
        val taskInstruction = if (mode == SearchMode.CUSTOM && customPrompt.isNotBlank()) customPrompt else mode.instruction
        val bannerBlock = if (bannerRules.isNotEmpty()) {
            """
            ДОПОЛНИТЕЛЬНОЕ ЗАДАНИЕ ПО БАННЕРУ И СТОП-КАДРУ:
            ${bannerRules.joinToString("\n") { "- $it" }}
            Если внутри момента есть подходящая секунда для баннера, заполни "banner_action". Если нужен стоп-кадр — "freeze_seconds" > 0.
            """.trimIndent()
        } else "Поле \"banner_action\" оставь null."

        return """
            Ты режиссер монтажа Shorts/Reels/TikTok. Задача: $taskInstruction$bannerBlock
            Найди от 2 до 6 лучших моментов. Не выдумывай события.
            Верни СТРОГО JSON-массив без markdown:
            [
              {
                "title": "Заголовок",
                "start_time": "MM:SS",
                "end_time": "MM:SS",
                "description": "Описание",
                "reason": "Почему удержит зрителя",
                "score": 95,
                "emotion_score": 88,
                "editing_tip": "Совет по монтажу",
                "banner_action": {
                  "trigger_time": "MM:SS",
                  "freeze_seconds": 2.0,
                  "banner_duration_seconds": 3.0,
                  "reason": "Почему здесь"
                }
              }
            ]
        """.trimIndent()
    }

    private fun parseMoments(rawResponse: String): List<VideoMoment> {
        val textResult = JSONObject(rawResponse)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text").trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        val arr = JSONArray(textResult)
        val moments = mutableListOf<VideoMoment>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val bObj = obj.optJSONObject("banner_action")
            val bannerAction = if (bObj != null) {
                BannerAction(
                    triggerTime = bObj.optString("trigger_time", obj.optString("start_time", "00:00")),
                    freezeSeconds = bObj.optDouble("freeze_seconds", 0.0).toFloat(),
                    bannerDurationSeconds = bObj.optDouble("banner_duration_seconds", 2.5).toFloat(),
                    reason = bObj.optString("reason", "")
                )
            } else null

            moments.add(
                VideoMoment(
                    title = obj.optString("title", "Момент #${i + 1}"),
                    startTime = obj.optString("start_time", "00:00"),
                    endTime = obj.optString("end_time", "00:15"),
                    description = obj.optString("description", ""),
                    reason = obj.optString("reason", ""),
                    score = obj.optInt("score", 80),
                    emotionScore = obj.optInt("emotion_score", 75),
                    editingTip = obj.optString("editing_tip", ""),
                    bannerAction = bannerAction
                )
            )
        }
        return moments
    }

    suspend fun analyzeYouTubeUrl(
        youtubeUrl: String,
        apiKey: String,
        model: String,
        mode: SearchMode,
        customPrompt: String,
        bannerRules: List<String>,
        onProgress: (String) -> Unit
    ): Result<List<VideoMoment>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val cleanModel = model.trim().ifBlank { "gemini-2.0-flash" }
        if (cleanKey.isBlank()) return@withContext Result.failure(Exception("Сначала укажи Gemini API Key в Настройках"))

        try {
            onProgress("AI анализирует YouTube-видео...")
            val parts = JSONArray()
                .put(JSONObject().put("file_data", JSONObject().put("file_uri", youtubeUrl.trim())))
                .put(JSONObject().put("text", buildPrompt(mode, customPrompt, bannerRules)))

            val payload = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.4))

            val genUrl = "${SCHEME}${GEMINI_HOST}/v1beta/models/$cleanModel:generateContent?key=$cleanKey"
            val genReq = Request.Builder().url(genUrl).post(payload.toString().toRequestBody("application/json".toMediaType())).build()

            val rawResponse = client.newCall(genReq).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    val errMsg = try { JSONObject(bodyStr).getJSONObject("error").getString("message") } catch (_: Exception) { "Код ${resp.code}" }
                    throw Exception("Ошибка YouTube (${resp.code}): $errMsg")
                }
                bodyStr
            }
            Result.success(parseMoments(rawResponse))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeVideo(
        context: Context,
        videoUri: Uri,
        sizeBytes: Long,
        apiKey: String,
        model: String,
        mode: SearchMode,
        customPrompt: String,
        bannerRules: List<String>,
        onProgress: (String) -> Unit
    ): Result<List<VideoMoment>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val cleanModel = model.trim().ifBlank { "gemini-2.0-flash" }
        if (cleanKey.isBlank()) return@withContext Result.failure(Exception("Сначала укажи Gemini API Key в Настройках"))

        try {
            val mimeType = context.contentResolver.getType(videoUri) ?: "video/mp4"
            val parts = JSONArray()

            // БЫСТРЫЙ РЕЖИМ: если видео меньше 10 МБ — шлем напрямую без File API
            if (sizeBytes in 1..(10 * 1024 * 1024)) {
                onProgress("Быстрая отправка видео в AI...")
                val bytes = context.contentResolver.openInputStream(videoUri)?.use { it.readBytes() }
                    ?: throw Exception("Не удалось прочитать файл")
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", mimeType).put("data", b64)))
            } else {
                onProgress("Загрузка большого видео в File API...")
                val initBody = JSONObject().put("file", JSONObject().put("display_name", "clipforge_video"))
                    .toString().toRequestBody("application/json".toMediaType())

                val initReq = Request.Builder()
                    .url("${SCHEME}${GEMINI_HOST}/upload/v1beta/files?key=$cleanKey")
                    .addHeader("X-Goog-Upload-Protocol", "resumable")
                    .addHeader("X-Goog-Upload-Command", "start")
                    .addHeader("X-Goog-Upload-Header-Content-Length", sizeBytes.toString())
                    .addHeader("X-Goog-Upload-Header-Content-Type", mimeType)
                    .post(initBody).build()

                val uploadUrl = client.newCall(initReq).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("Ошибка инициализации (${resp.code})")
                    resp.header("X-Goog-Upload-URL") ?: throw Exception("Нет URL загрузки")
                }

                val streamBody = object : RequestBody() {
                    override fun contentType(): MediaType = mimeType.toMediaType()
                    override fun contentLength(): Long = if (sizeBytes > 0) sizeBytes else -1L
                    override fun writeTo(sink: BufferedSink) {
                        context.contentResolver.openInputStream(videoUri)?.use { sink.writeAll(it.source()) }
                    }
                }

                val fileJson = client.newCall(
                    Request.Builder().url(uploadUrl)
                        .addHeader("X-Goog-Upload-Offset", "0")
                        .addHeader("X-Goog-Upload-Command", "upload, finalize")
                        .post(streamBody).build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("Ошибка загрузки (${resp.code})")
                    JSONObject(resp.body?.string() ?: "{}").getJSONObject("file")
                }

                val fileName = fileJson.getString("name")
                val fileUri = fileJson.getString("uri")

                onProgress("Обработка на сервере...")
                var state = fileJson.optString("state", "PROCESSING")
                var attempts = 0
                while (state == "PROCESSING" && attempts < 45) {
                    delay(2000)
                    client.newCall(Request.Builder().url("${SCHEME}${GEMINI_HOST}/v1beta/$fileName?key=$cleanKey").get().build())
                        .execute().use { resp ->
                            if (resp.isSuccessful) state = JSONObject(resp.body?.string() ?: "{}").optString("state", "ACTIVE")
                        }
                    attempts++
                }
                if (state == "FAILED") throw Exception("Сервер не смог обработать видео")
                parts.put(JSONObject().put("file_data", JSONObject().put("mime_type", mimeType).put("file_uri", fileUri)))
            }

            onProgress("AI анализирует моменты...")
            parts.put(JSONObject().put("text", buildPrompt(mode, customPrompt, bannerRules)))

            val payload = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.4))

            val genUrl = "${SCHEME}${GEMINI_HOST}/v1beta/models/$cleanModel:generateContent?key=$cleanKey"
            val rawResponse = client.newCall(
                Request.Builder().url(genUrl).post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            ).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    val errMsg = try { JSONObject(bodyStr).getJSONObject("error").getString("message") } catch (_: Exception) { "Код ${resp.code}" }
                    throw Exception("Ошибка AI (${resp.code}): $errMsg")
                }
                bodyStr
            }

            Result.success(parseMoments(rawResponse))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

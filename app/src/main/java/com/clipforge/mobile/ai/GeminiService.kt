package com.clipforge.mobile.ai

import android.content.Context
import android.net.Uri
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
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun verifyApiKey(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("API ключ не указан"))
        try {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model?key=$apiKey")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success("Подключено ($model)")
                } else {
                    Result.failure(Exception("Ошибка ${resp.code}: проверь ключ или имя модели"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Нет сети или ошибка запроса: ${e.localizedMessage}"))
        }
    }

    suspend fun fetchYouTubeTitle(youtubeUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(youtubeUrl.trim(), "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val title = json.optString("title", "YouTube видео")
                    val author = json.optString("author_name", "")
                    Result.success(if (author.isNotBlank()) "$title —$author" else title)
                } else {
                    Result.failure(Exception("Не удалось получить данные видео (проверь ссылку)"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка проверки ссылки: ${e.localizedMessage}"))
        }
    }

    private fun buildPrompt(mode: SearchMode, customPrompt: String, bannerRules: List<String>): String {
        val taskInstruction = if (mode == SearchMode.CUSTOM && customPrompt.isNotBlank()) {
            customPrompt
        } else {
            mode.instruction
        }

        val bannerBlock = if (bannerRules.isNotEmpty()) {
            """
            ДОПОЛНИТЕЛЬНОЕ ЗАДАНИЕ ПО БАННЕРУ И СТОП-КАДРУ:
            Пользователь задал условия вставки баннера:
            ${bannerRules.joinToString("\n") { "- $it" }}
            Если внутри найденного момента есть подходящая секунда для вставки баннера по этим условиям, заполни поле "banner_action".
            Если нужно сделать стоп-кадр (заморозку видео) — укажи "freeze_seconds" > 0 (например 1.5 или 2.0), иначе 0.0.
            Если в этом моменте баннер не нужен, верни "banner_action": null.
            """.trimIndent()
        } else {
            "Поле \"banner_action\" оставь null."
        }

        return """
            Ты профессиональный режиссер монтажа коротких вертикальных роликов (Shorts/Reels/TikTok).
            Задача: $taskInstruction$bannerBlock
            Проанализируй видео и найди от 3 до 7 лучших моментов. Не выдумывай события, которых нет в видео.
            Верни ответ СТРОГО в формате JSON-массива объектов без markdown-разметки:
            [
              {
                "title": "Короткий цепляющий заголовок на русском",
                "start_time": "MM:SS",
                "end_time": "MM:SS",
                "description": "Краткое описание сути момента",
                "reason": "Почему этот момент удержит зрителя",
                "score": 95,
                "emotion_score": 88,
                "editing_tip": "Совет по монтажу",
                "banner_action": {
                  "trigger_time": "MM:SS",
                  "freeze_seconds": 2.0,
                  "banner_duration_seconds": 3.0,
                  "reason": "Почему баннер и стоп-кадр подходят именно здесь"
                }
              }
            ]
        """.trimIndent()
    }

    private fun parseMoments(rawResponse: String): List<VideoMoment> {
        val textResult = JSONObject(rawResponse)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

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
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("Сначала укажи Gemini API Key в Настройках"))
        if (youtubeUrl.isBlank()) return@withContext Result.failure(Exception("Вставь ссылку на YouTube"))

        try {
            onProgress("AI анализирует YouTube-видео...")
            val promptText = buildPrompt(mode, customPrompt, bannerRules)

            val parts = JSONArray()
                .put(JSONObject().put("file_data", JSONObject().put("file_uri", youtubeUrl.trim())))
                .put(JSONObject().put("text", promptText))

            val payload = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .put("generationConfig", JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("temperature", 0.4))

            val genReq = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val rawResponse = client.newCall(genReq).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw Exception("Ошибка анализа YouTube (${resp.code}). Убедись, что видео публичное или загрузи файл.")
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
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("Сначала укажи Gemini API Key в Настройках"))

        try {
            val mimeType = context.contentResolver.getType(videoUri) ?: "video/mp4"

            onProgress("Подготовка к загрузке видео...")
            val initBody = JSONObject().put("file", JSONObject().put("display_name", "clipforge_video"))
                .toString().toRequestBody("application/json".toMediaType())

            val initReq = Request.Builder()
                .url("https://generativelanguage.googleapis.com/upload/v1beta/files?key=$apiKey")
                .addHeader("X-Goog-Upload-Protocol", "resumable")
                .addHeader("X-Goog-Upload-Command", "start")
                .addHeader("X-Goog-Upload-Header-Content-Length", sizeBytes.toString())
                .addHeader("X-Goog-Upload-Header-Content-Type", mimeType)
                .post(initBody)
                .build()

            val uploadUrl = client.newCall(initReq).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Ошибка инициализации загрузки (${resp.code})")
                resp.header("X-Goog-Upload-URL") ?: throw Exception("Не получен URL для загрузки")
            }

            onProgress("Загрузка видео в Gemini File API...")
            val streamBody = object : RequestBody() {
                override fun contentType(): MediaType = mimeType.toMediaType()
                override fun contentLength(): Long = if (sizeBytes > 0) sizeBytes else -1L
                override fun writeTo(sink: BufferedSink) {
                    context.contentResolver.openInputStream(videoUri)?.use { input ->
                        sink.writeAll(input.source())
                    } ?: throw Exception("Не удалось прочитать файл видео")
                }
            }

            val uploadReq = Request.Builder()
                .url(uploadUrl)
                .addHeader("X-Goog-Upload-Offset", "0")
                .addHeader("X-Goog-Upload-Command", "upload, finalize")
                .post(streamBody)
                .build()

            val fileJson = client.newCall(uploadReq).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Ошибка загрузки файла (${resp.code})")
                JSONObject(resp.body?.string() ?: "{}").getJSONObject("file")
            }

            val fileName = fileJson.getString("name")
            val fileUri = fileJson.getString("uri")

            onProgress("Обработка видео на сервере AI...")
            var state = fileJson.optString("state", "PROCESSING")
            var attempts = 0
            while (state == "PROCESSING" && attempts < 45) {
                delay(2000)
                val checkReq = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/$fileName?key=$apiKey")
                    .get()
                    .build()
                client.newCall(checkReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        state = JSONObject(resp.body?.string() ?: "{}").optString("state", "ACTIVE")
                    }
                }
                attempts++
            }
            if (state == "FAILED") throw Exception("Сервер Gemini не смог обработать это видео")

            onProgress("AI ищет моменты и точки для баннера...")
            val promptText = buildPrompt(mode, customPrompt, bannerRules)

            val parts = JSONArray()
                .put(JSONObject().put("file_data", JSONObject()
                    .put("mime_type", mimeType)
                    .put("file_uri", fileUri)))
                .put(JSONObject().put("text", promptText))

            val payload = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .put("generationConfig", JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("temperature", 0.4))

            val genReq = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val rawResponse = client.newCall(genReq).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw Exception("Ошибка анализа AI (${resp.code})")
                bodyStr
            }

            Result.success(parseMoments(rawResponse))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

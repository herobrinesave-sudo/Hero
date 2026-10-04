package com.example.data.gemini

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class GeminiMessage(
    val role: String, // "user" or "model"
    val text: String,
    val imageBase64: String? = null,
    val imageMimeType: String? = null
)

sealed class GeminiResult<out T> {
    data class Success<out T>(val data: T) : GeminiResult<T>()
    data class Error(val message: String, val isApiKeyMissing: Boolean = false) : GeminiResult<Nothing>()
}

class GeminiRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("pro_ai_prefs", Context.MODE_PRIVATE)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun getApiKey(): String {
        val userSavedKey = prefs.getString("custom_gemini_api_key", "")?.trim() ?: ""
        if (userSavedKey.isNotEmpty()) {
            return userSavedKey
        }
        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (_: Exception) {
            ""
        }
        return if (buildConfigKey.isNotEmpty() && buildConfigKey != "MY_GEMINI_API_KEY") {
            buildConfigKey
        } else {
            ""
        }
    }

    fun saveCustomApiKey(key: String) {
        prefs.edit().putString("custom_gemini_api_key", key.trim()).apply()
    }

    fun hasValidApiKey(): Boolean {
        val key = getApiKey()
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    /**
     * Send chat conversation with history to Gemini 3.5 Flash.
     */
    suspend fun generateChatResponse(
        conversationHistory: List<GeminiMessage>,
        systemPrompt: String = "You are Pro AI, a professional, futuristic, and highly intelligent AI assistant. Provide concise, well-structured, clear answers with a modern, insightful tone."
    ): GeminiResult<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiResult.Error(
                "Gemini API key is required. Tap the key icon to configure your key or inject it via AI Studio Secrets.",
                isApiKeyMissing = true
            )
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val contentsArray = JSONArray()
            // Take the last 15 messages for context window management
            val recentMessages = conversationHistory.takeLast(15)

            for (msg in recentMessages) {
                val contentObj = JSONObject()
                contentObj.put("role", if (msg.role == "model") "model" else "user")
                val partsArray = JSONArray()

                val textPart = JSONObject()
                textPart.put("text", msg.text)
                partsArray.put(textPart)

                if (!msg.imageBase64.isNullOrEmpty()) {
                    val inlineDataObj = JSONObject()
                    inlineDataObj.put("mimeType", msg.imageMimeType ?: "image/jpeg")
                    inlineDataObj.put("data", msg.imageBase64)
                    val imgPart = JSONObject()
                    imgPart.put("inlineData", inlineDataObj)
                    partsArray.put(imgPart)
                }

                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
            }

            val requestJson = JSONObject()
            requestJson.put("contents", contentsArray)

            // System instructions
            val systemInstructionObj = JSONObject()
            val sysParts = JSONArray()
            val sysPartText = JSONObject()
            sysPartText.put("text", systemPrompt)
            sysParts.put(sysPartText)
            systemInstructionObj.put("parts", sysParts)
            requestJson.put("systemInstruction", systemInstructionObj)

            // Generation config
            val genConfig = JSONObject()
            genConfig.put("temperature", 0.7)
            genConfig.put("topP", 0.95)
            requestJson.put("generationConfig", genConfig)

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseString, response.code)
                return@withContext GeminiResult.Error(errorMsg)
            }

            val jsonResponse = JSONObject(responseString)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext GeminiResult.Error("No response generated by Pro AI.")
            }

            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            val responseText = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        responseText.append(part.getString("text"))
                    }
                }
            }

            val resultText = responseText.toString().trim()
            if (resultText.isNotEmpty()) {
                GeminiResult.Success(resultText)
            } else {
                GeminiResult.Error("Received empty response from Pro AI.")
            }
        } catch (e: Exception) {
            GeminiResult.Error(e.localizedMessage ?: "Network error connecting to Pro AI service")
        }
    }

    /**
     * Generate an image using Gemini 2.5 Flash Image.
     */
    suspend fun generateImage(
        prompt: String,
        aspectRatio: String = "1:1"
    ): GeminiResult<Pair<String, String>> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiResult.Error(
                "Gemini API key is required for Image AI generation.",
                isApiKeyMissing = true
            )
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent?key=$apiKey"

            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val textPart = JSONObject()
            textPart.put("text", prompt)
            partsArray.put(textPart)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)

            val requestJson = JSONObject()
            requestJson.put("contents", contentsArray)

            val genConfig = JSONObject()
            val modalities = JSONArray()
            modalities.put("TEXT")
            modalities.put("IMAGE")
            genConfig.put("responseModalities", modalities)

            val imageConfig = JSONObject()
            imageConfig.put("aspectRatio", aspectRatio)
            genConfig.put("imageConfig", imageConfig)

            requestJson.put("generationConfig", genConfig)

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseString, response.code)
                return@withContext GeminiResult.Error(errorMsg)
            }

            val jsonResponse = JSONObject(responseString)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext GeminiResult.Error("No image generated by model.")
            }

            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            var imageBase64: String? = null
            var description: String = ""

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("inlineData")) {
                        val inline = part.getJSONObject("inlineData")
                        imageBase64 = inline.optString("data", "")
                    }
                    if (part.has("text")) {
                        description += part.getString("text") + " "
                    }
                }
            }

            if (!imageBase64.isNullOrEmpty()) {
                GeminiResult.Success(Pair(imageBase64, description.trim()))
            } else {
                GeminiResult.Error("Pro AI processed the prompt: $description, but did not return image data.")
            }
        } catch (e: Exception) {
            GeminiResult.Error(e.localizedMessage ?: "Error during Image AI generation")
        }
    }

    private fun parseErrorMessage(errorBody: String, statusCode: Int): String {
        return try {
            val json = JSONObject(errorBody)
            val error = json.optJSONObject("error")
            val message = error?.optString("message", "") ?: ""
            if (message.isNotEmpty()) {
                "Pro AI Error ($statusCode): $message"
            } else {
                "Service request failed with HTTP $statusCode"
            }
        } catch (_: Exception) {
            "Service request failed with HTTP $statusCode"
        }
    }

    companion object {
        fun bitmapToBase64(bitmap: Bitmap): String {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }
    }
}

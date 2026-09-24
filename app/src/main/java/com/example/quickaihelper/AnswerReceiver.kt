package com.example.quickaihelper

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AnswerReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "ai_solver_channel"
        const val NOTIFICATION_ID = 1001
        const val KEY_TEXT_INPUT = "key_text_input"

        // THAY MÃ API KEY CỦA BẠN VÀO ĐÂY:
        private const val GEMINI_API_KEY = "AQ.Ab8RN6JFBxV8Rasm5_Hcunc7eNOHstgDGd6GCo_k_pxacc8l9g"
        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$GEMINI_API_KEY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val questionText = remoteInput?.getCharSequence(KEY_TEXT_INPUT)?.toString()

        if (!questionText.isNullOrBlank()) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            showLoadingNotification(context, notificationManager)

            CoroutineScope(Dispatchers.IO).launch {
                val resultText = requestGeminiSolution(questionText)
                withContext(Dispatchers.Main) {
                    showResultNotification(context, notificationManager, questionText, resultText)
                }
            }
        }
    }

    private fun showLoadingNotification(context: Context, manager: NotificationManager) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Gemini đang suy nghĩ...")
            .setContentText("Đang phân tích câu hỏi...")
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showResultNotification(
        context: Context,
        manager: NotificationManager,
        question: String,
        answer: String
    ) {
        val nextInput = RemoteInput.Builder(KEY_TEXT_INPUT)
            .setLabel("Dán câu hỏi tiếp theo...")
            .build()

        val nextIntent = Intent(context, AnswerReceiver::class.java)
        val nextPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            nextIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "Giải câu tiếp",
            nextPendingIntent
        ).addRemoteInput(nextInput).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("KẾT QUẢ:")
            .setContentText(answer)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("📝 Đề bài:\n$question\n\n🎯 Đáp án:\n$answer")
            )
            .setOngoing(true)
            .addAction(nextAction)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun requestGeminiSolution(prompt: String): String {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

            val instructions = "Bạn là trợ lý giải trắc nghiệm và câu hỏi đúng/sai. Đưa ra đáp án chính xác ngay đầu tiên (ví dụ: 'Đáp án: C' hoặc '1. Đúng, 2. Sai'). Sau đó giải thích ngắn gọn trong 1-2 câu. Định dạng plain text."

            val jsonPayload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", "$instructions\n\nNội dung câu hỏi:\n$prompt"))
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(ENDPOINT)
                .post(jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: return "Không nhận được phản hồi từ server."
                if (!response.isSuccessful) return "Lỗi API (${response.code}): $bodyString"

                val jsonResponse = JSONObject(bodyString)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val parts = candidates.getJSONObject(0)
                        .optJSONObject("content")
                        ?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return parts.getJSONObject(0).optString("text", "Không có nội dung trả lời.").trim()
                    }
                }
                "Không đọc được định dạng đáp án từ Gemini."
            }
        } catch (e: Exception) {
            "Lỗi kết nối: ${e.message}"
        }
    }
}

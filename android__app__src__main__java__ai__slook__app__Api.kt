package ai.slook.app

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun stream(
        baseUrl: String,
        token: String,
        history: List<Pair<String, String>>,
        att: Att?,
        onCall: (Call) -> Unit,
        onChunk: (String) -> Unit,
    ) {
        val msgs = JSONArray()
        history.forEach { (r, t) -> msgs.put(JSONObject().put("role", r).put("text", t)) }
        val body = JSONObject().put("messages", msgs)
        if (att != null) {
            body.put("attachment", JSONObject().put("name", att.name).put("mime", att.mime).put("data", att.b64))
        }
        var base = baseUrl.trim().trimEnd('/')
        if (!base.startsWith("http")) base = "https://$base"
        val req = Request.Builder()
            .url("$base/chat")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val call = client.newCall(req)
        onCall(call)
        call.execute().use { r ->
            if (!r.isSuccessful) {
                val b = r.body?.string().orEmpty()
                val msg = try { JSONObject(b).optString("detail", b) } catch (e: Exception) { b }
                throw IOException("Сервер ответил ${r.code}: ${msg.take(300)}")
            }
            val src = r.body!!.source()
            while (true) {
                val ln = src.readUtf8Line() ?: break
                if (ln.isBlank()) continue
                val o = JSONObject(ln)
                if (o.has("e")) throw IOException(o.getString("e"))
                onChunk(o.optString("t"))
            }
        }
    }
}

package ai.slook.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Msg(val role: String, text: String) {
    var text by mutableStateOf(text)
}

class Conv(val id: String, title: String) {
    var title by mutableStateOf(title)
    val msgs = mutableStateListOf<Msg>()
}

class Att(val name: String, val mime: String, val b64: String)

object Store {
    private fun file(ctx: Context) = File(ctx.filesDir, "convs.json")

    fun load(ctx: Context): List<Conv> = try {
        val f = file(ctx)
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val c = Conv(o.getString("id"), o.getString("title"))
                val ms = o.getJSONArray("msgs")
                for (j in 0 until ms.length()) {
                    val m = ms.getJSONObject(j)
                    c.msgs.add(Msg(m.getString("role"), m.getString("text")))
                }
                c
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(ctx: Context, convs: List<Conv>) {
        try {
            val arr = JSONArray()
            convs.filter { it.msgs.isNotEmpty() }.forEach { c ->
                val ms = JSONArray()
                c.msgs.forEach { ms.put(JSONObject().put("role", it.role).put("text", it.text)) }
                arr.put(JSONObject().put("id", c.id).put("title", c.title).put("msgs", ms))
            }
            val tmp = File(ctx.filesDir, "convs.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file(ctx))
        } catch (e: Exception) {
            // не удалось сохранить историю — приложение продолжает работать
        }
    }
}

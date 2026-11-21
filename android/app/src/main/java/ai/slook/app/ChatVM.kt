package ai.slook.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import java.util.UUID

class ChatVM(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("slook", 0)
    val convs = mutableStateListOf<Conv>()
    var current by mutableStateOf<Conv?>(null)
    var busy by mutableStateOf(false)
    var pending by mutableStateOf<Att?>(null)
    var serverUrl by mutableStateOf(prefs.getString("url", "") ?: "")
    var token by mutableStateOf(prefs.getString("token", "") ?: "")
    private var call: Call? = null
    private var job: Job? = null

    init {
        convs.addAll(Store.load(app))
        newChat()
    }

    fun newChat() {
        val first = convs.firstOrNull()
        if (first != null && first.msgs.isEmpty()) { current = first; return }
        val c = Conv(UUID.randomUUID().toString(), "Новый чат")
        convs.add(0, c)
        current = c
    }

    private fun toast(s: String) = Toast.makeText(getApplication(), s, Toast.LENGTH_LONG).show()

    private fun persist() = Store.save(getApplication(), convs.toList())

    fun saveSettings(url: String, tok: String) {
        serverUrl = url; token = tok
        prefs.edit().putString("url", url).putString("token", tok).apply()
    }

    fun delete(c: Conv) {
        if (busy) return
        convs.remove(c)
        if (current === c) current = convs.firstOrNull()
        if (current == null) newChat()
        persist()
    }

    fun attach(uri: Uri) {
        val cr = getApplication<Application>().contentResolver
        val bytes = try { cr.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
        if (bytes == null) { toast("Не удалось прочитать файл"); return }
        if (bytes.size > 10 * 1024 * 1024) { toast("Файл больше 10 МБ"); return }
        var name = "файл"
        cr.query(uri, null, null, null, null)?.use { cur ->
            val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && cur.moveToFirst()) name = cur.getString(i)
        }
        pending = Att(name, cr.getType(uri) ?: "application/octet-stream", Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    fun stop() {
        call?.cancel()
        job?.cancel()
    }

    fun send(text: String) {
        val c = current ?: return
        if (busy || (text.isBlank() && pending == null)) return
        if (serverUrl.isBlank() || token.isBlank()) {
            toast("Сначала укажи адрес сервера и токен в настройках")
            return
        }
        val att = pending
        pending = null
        c.msgs.add(Msg("user", if (att != null) "📎 ${att.name}\n$text".trim() else text))
        if (c.title == "Новый чат") c.title = text.ifBlank { att?.name ?: "Чат" }.take(30)
        val reply = Msg("model", "")
        c.msgs.add(reply)
        val history = c.msgs.dropLast(1)
            .filter { it.text.isNotBlank() && !it.text.startsWith("⚠️") }
            .takeLast(40)
            .map { it.role to it.text }
            .dropWhile { it.first == "model" }
        busy = true
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                Api.stream(serverUrl, token, history, att, { call = it }) { reply.text += it }
            } catch (e: Exception) {
                if (!isActive) reply.text += "\n[остановлено]"
                else if (reply.text.isBlank()) reply.text = "⚠️ ${e.message}"
                else reply.text += "\n⚠️ ${e.message}"
            } finally {
                busy = false
                persist()
            }
        }
    }
}

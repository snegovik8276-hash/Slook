package ai.slook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SlookTheme { App() } }
    }
}

@Composable
fun SlookTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF9D8CFF), primaryContainer = Color(0xFF3A3190))
    } else {
        lightColorScheme(primary = Color(0xFF5B4BDB), primaryContainer = Color(0xFFE4E0FF))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: ChatVM = viewModel()) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(vm.serverUrl.isBlank() || vm.token.isBlank()) }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Text("История", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                TextButton(
                    onClick = { vm.newChat(); scope.launch { drawer.close() } },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text("+ Новый чат") }
                LazyColumn {
                    items(vm.convs, key = { it.id }) { c ->
                        NavigationDrawerItem(
                            label = { Text(c.title, maxLines = 1) },
                            selected = c === vm.current,
                            onClick = { vm.current = c; scope.launch { drawer.close() } },
                            badge = {
                                IconButton(onClick = { vm.delete(c) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить чат")
                                }
                            },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SLOOK") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "История")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                    },
                )
            },
        ) { pad -> Chat(vm, Modifier.padding(pad)) }
    }
    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
fun Chat(vm: ChatVM, modifier: Modifier) {
    val c = vm.current ?: return
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.attach(uri)
    }
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val lastText = c.msgs.lastOrNull()?.text
    LaunchedEffect(c.msgs.size, lastText) {
        if (c.msgs.isNotEmpty()) list.scrollToItem(c.msgs.size - 1)
    }
    Column(modifier.fillMaxSize()) {
        if (c.msgs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Чем помочь?", style = MaterialTheme.typography.headlineSmall)
            }
        } else {
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { items(c.msgs) { MsgBubble(it) } }
        }
        vm.pending?.let { a ->
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📎 ${a.name}", Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = { vm.pending = null }) {
                    Icon(Icons.Default.Close, contentDescription = "Убрать файл")
                }
            }
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = { picker.launch("*/*") }, enabled = !vm.busy) {
                Icon(Icons.Default.Add, contentDescription = "Прикрепить файл")
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение") },
                maxLines = 5,
            )
            if (vm.busy) {
                IconButton(onClick = { vm.stop() }) {
                    Icon(Icons.Default.Close, contentDescription = "Остановить")
                }
            } else {
                IconButton(onClick = { vm.send(input); input = "" }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                }
            }
        }
    }
}

@Composable
fun MsgBubble(m: Msg) {
    val mine = m.role == "user"
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            SelectionContainer {
                Text(if (m.text.isEmpty()) "…" else m.text, Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
fun SettingsDialog(vm: ChatVM, onClose: () -> Unit) {
    var url by remember { mutableStateOf(vm.serverUrl) }
    var tok by remember { mutableStateOf(vm.token) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Настройки сервера") },
        text = {
            Column {
                OutlinedTextField(url, { url = it }, label = { Text("Адрес сервера") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(tok, { tok = it }, label = { Text("Токен доступа") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.saveSettings(url.trim(), tok.trim()); onClose() }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } },
    )
}

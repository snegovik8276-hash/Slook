package ai.slook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        darkColorScheme(
            primary = Color(0xFFECECEC), onPrimary = Color(0xFF171717),
            background = Color(0xFF212121), onBackground = Color(0xFFECECEC),
            surface = Color(0xFF212121), onSurface = Color(0xFFECECEC),
            surfaceVariant = Color(0xFF2F2F2F), onSurfaceVariant = Color(0xFFB4B4B4),
            outline = Color(0xFF4A4A4A),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1A1A1A), onPrimary = Color.White,
            background = Color.White, onBackground = Color(0xFF1A1A1A),
            surface = Color.White, onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFF4F4F4), onSurfaceVariant = Color(0xFF6B6B6B),
            outline = Color(0xFFDDDDDD),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: ChatVM = viewModel()) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(vm.serverUrl.isBlank() || vm.token.isBlank()) }
    val itemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unselectedContainerColor = Color.Transparent,
    )

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background) {
                Text("SLOOK", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(20.dp))
                NavigationDrawerItem(
                    label = { Text("Новый чат") },
                    selected = false,
                    onClick = { vm.newChat(); scope.launch { drawer.close() } },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    colors = itemColors,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Text(
                    "История", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
                )
                LazyColumn(Modifier.weight(1f)) {
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
                            colors = itemColors,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Настройки") },
                    selected = false,
                    onClick = { showSettings = true; scope.launch { drawer.close() } },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    colors = itemColors,
                    modifier = Modifier.padding(12.dp),
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("SLOOK", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "История")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.newChat() }) {
                            Icon(Icons.Default.Edit, contentDescription = "Новый чат")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
            EmptyState(vm, Modifier.weight(1f).fillMaxWidth())
        } else {
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                itemsIndexed(c.msgs) { i, m ->
                    if (m.role == "user") UserMsg(m)
                    else AssistantMsg(m, vm.busy && i == c.msgs.lastIndex)
                }
            }
        }
        vm.pending?.let { a ->
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📎 ${a.name}", Modifier.weight(1f), maxLines = 1, fontSize = 13.sp)
                IconButton(onClick = { vm.pending = null }) {
                    Icon(Icons.Default.Close, contentDescription = "Убрать файл")
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = { picker.launch("*/*") }, enabled = !vm.busy) {
                    Icon(Icons.Default.Add, contentDescription = "Прикрепить файл")
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение") },
                    maxLines = 6,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Box(Modifier.padding(6.dp)) {
                    FilledIconButton(onClick = {
                        if (vm.busy) vm.stop() else { vm.send(input); input = "" }
                    }) {
                        if (vm.busy) Text("■")
                        else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(vm: ChatVM, modifier: Modifier) {
    val tips = listOf(
        "Объясни простыми словами, как работает ИИ",
        "Составь план на неделю",
        "Помоги написать сообщение другу",
    )
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SLOOK", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Чем помочь?", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        tips.forEach { tip ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp).clickable { vm.send(tip) },
            ) {
                Text(tip, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun UserMsg(m: Msg) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            SelectionContainer {
                Text(m.text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
fun AssistantMsg(m: Msg, streaming: Boolean) {
    val clip = LocalClipboardManager.current
    val isError = m.text.startsWith("⚠️")
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("S", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (m.text.isEmpty()) {
                Typing()
            } else {
                SelectionContainer {
                    Column {
                        parseBlocks(m.text).forEach { b ->
                            when (b) {
                                is TextBlock -> Text(
                                    inline(prettyLines(b.s), MaterialTheme.colorScheme.surfaceVariant),
                                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp,
                                )
                                is CodeBlock -> Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                ) {
                                    Text(
                                        b.s,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (!streaming) {
                    Text(
                        "Копировать", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { clip.setText(AnnotatedString(m.text)) }.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun Typing() {
    val t = rememberInfiniteTransition(label = "typing")
    val a by t.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha",
    )
    Box(
        Modifier.padding(top = 8.dp).size(12.dp).alpha(a).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface),
    )
}

sealed interface Block
class TextBlock(val s: String) : Block
class CodeBlock(val s: String) : Block

fun parseBlocks(t: String): List<Block> {
    val out = mutableListOf<Block>()
    t.split("```").forEachIndexed { i, p ->
        if (i % 2 == 0) {
            if (p.isNotBlank()) out.add(TextBlock(p.trim('\n')))
        } else {
            out.add(CodeBlock(p.substringAfter('\n', p).trimEnd()))
        }
    }
    return out
}

fun prettyLines(s: String): String = s.lines().joinToString("\n") { l ->
    when {
        l.startsWith("### ") -> "**${l.drop(4)}**"
        l.startsWith("## ") -> "**${l.drop(3)}**"
        l.startsWith("# ") -> "**${l.drop(2)}**"
        l.startsWith("- ") || l.startsWith("* ") -> "• ${l.drop(2)}"
        else -> l
    }
}

fun inline(s: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        if (s.startsWith("**", i)) {
            val e = s.indexOf("**", i + 2)
            if (e > 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, e)) }
                i = e + 2
                continue
            }
        }
        if (s[i] == '`') {
            val e = s.indexOf('`', i + 1)
            if (e > 0) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                    append(s.substring(i + 1, e))
                }
                i = e + 1
                continue
            }
        }
        append(s[i])
        i++
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

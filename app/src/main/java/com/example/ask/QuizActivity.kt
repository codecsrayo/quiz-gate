package com.example.ask

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ask.ui.theme.AskTheme
import kotlinx.coroutines.launch

class QuizActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TRIGGER_PKG = "trigger_pkg"
        private const val TAG = "QuizGate"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "QuizActivity onCreate")

        WatchdogService.start(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* swallow back */ }
        })

        setContent {
            AskTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    QuizGateScreen(
                        onUnlock = {
                            Log.i(TAG, "QuizActivity onUnlock pressed")
                            val pkg = Prefs.getLastBlockedPackage(this)
                            if (pkg != null) {
                                Prefs.setPendingUnlock(this, pkg)
                                packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
                                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(launch)
                                }
                            }
                            finishAndRemoveTask()
                        },
                        onCancel = {
                            Log.i(TAG, "QuizActivity onCancel pressed")
                            Prefs.clearPendingUnlock(this)
                            val home = Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_HOME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { startActivity(home) }
                            finishAndRemoveTask()
                        },
                    )
                }
            }
        }
    }

    override fun onStart() { super.onStart(); Log.i(TAG, "QuizActivity onStart") }
    override fun onResume() { super.onResume(); Log.i(TAG, "QuizActivity onResume") }
    override fun onPause() { Log.i(TAG, "QuizActivity onPause"); super.onPause() }
    override fun onStop() { Log.i(TAG, "QuizActivity onStop"); super.onStop() }
    override fun onDestroy() { Log.i(TAG, "QuizActivity onDestroy isFinishing=$isFinishing"); super.onDestroy() }
}

private sealed interface UiState {
    data object Loading : UiState
    data class Error(val message: String) : UiState
    data class Showing(
        val question: Question,
        val selected: Set<Int>,
        val checked: Boolean,
    ) : UiState
}

@Composable
private fun QuizGateScreen(onUnlock: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { QuizRepository(context) }
    val scope = rememberCoroutineScope()

    var lang by remember { mutableStateOf(Prefs.getLang(context)) }
    var pool by remember { mutableStateOf<List<Question>>(emptyList()) }
    var state by remember { mutableStateOf<UiState>(UiState.Loading) }

    fun pickRandom(): UiState {
        if (pool.isEmpty()) {
            return UiState.Error("No hay preguntas en caché. Abre la app principal y pulsa 'Sincronizar'.")
        }
        val enabledDomains = Prefs.getEnabledDomains(context)
        val byDomain = if (enabledDomains.isEmpty()) pool
            else pool.filter { it.domain in enabledDomains }
        if (byDomain.isEmpty()) {
            return UiState.Error("Ningún dominio seleccionado tiene preguntas. Ajusta el filtro.")
        }
        val chosen = byDomain.random()
        return UiState.Showing(chosen, selected = emptySet(), checked = false)
    }

    LaunchedEffect(Unit) {
        val cached = repo.loadCached()
        if (cached.isNotEmpty()) {
            pool = cached
            state = pickRandom()
        } else {
            state = UiState.Loading
            val r = repo.refreshFromNetwork()
            r.onSuccess {
                pool = repo.loadCached()
                state = pickRandom()
            }.onFailure {
                state = UiState.Error("No se pudo descargar el quiz: ${it.message ?: "error"}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quiz Gate", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FilterChip(
                selected = lang == "es",
                onClick = { lang = "es"; Prefs.setLang(context, "es") },
                label = { Text("ES") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = lang == "en",
                onClick = { lang = "en"; Prefs.setLang(context, "en") },
                label = { Text("EN") }
            )
        }

        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                is UiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is UiState.Error -> Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.message)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        scope.launch {
                            state = UiState.Loading
                            repo.refreshFromNetwork().onSuccess {
                                pool = repo.loadCached()
                                state = pickRandom()
                            }.onFailure {
                                state = UiState.Error("Sin conexión: ${it.message ?: "error"}")
                            }
                        }
                    }) { Text("Reintentar") }
                }

                is UiState.Showing -> QuestionView(
                    state = s,
                    lang = lang,
                    onToggle = { idx ->
                        val cur = s.selected
                        val next = if (s.question.isMultiResponse) {
                            if (idx in cur) cur - idx else cur + idx
                        } else {
                            setOf(idx)
                        }
                        state = s.copy(selected = next)
                    },
                    onCheck = { state = s.copy(checked = true) },
                    onNext = { state = pickRandom() },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            val current = state as? UiState.Showing
            val isCorrect = current != null && current.checked &&
                current.selected == current.question.correctAnswers()
            Button(
                onClick = onUnlock,
                enabled = isCorrect,
                modifier = Modifier.weight(2f)
            ) { Text(if (isCorrect) "Continuar" else "Acierta para continuar") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
        }
    }
}

@Composable
private fun QuestionView(
    state: UiState.Showing,
    lang: String,
    onToggle: (Int) -> Unit,
    onCheck: () -> Unit,
    onNext: () -> Unit,
) {
    val q = state.question
    val correct = q.correctAnswers()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = {}, label = { Text(q.domainText(lang)) })
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (q.isMultiResponse) "${q.id} · Multi×${correct.size}" else q.id,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Text(q.statement(lang), style = MaterialTheme.typography.titleMedium)

        val (successBg, successFg) = successColors()
        val (errorBg, errorFg) = errorColors()

        val opts = q.optionsFor(lang)
        opts.forEachIndexed { idx, opt ->
            val isSelected = idx in state.selected
            val isCorrectOpt = idx in correct
            val bg: Color = when {
                state.checked && isCorrectOpt -> successBg
                state.checked && isSelected && !isCorrectOpt -> errorBg
                isSelected -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
            val fg: Color = when {
                state.checked && isCorrectOpt -> successFg
                state.checked && isSelected && !isCorrectOpt -> errorFg
                else -> MaterialTheme.colorScheme.onSurface
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 1.dp,
                color = bg,
                contentColor = fg,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        enabled = !state.checked,
                        onClick = { onToggle(idx) }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (q.isMultiResponse) {
                        Checkbox(
                            checked = isSelected,
                            enabled = !state.checked,
                            onCheckedChange = { onToggle(idx) }
                        )
                    } else {
                        RadioButton(
                            selected = isSelected,
                            enabled = !state.checked,
                            onClick = { onToggle(idx) }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(opt, modifier = Modifier.weight(1f))
                }
            }
        }

        if (!state.checked) {
            Button(
                onClick = onCheck,
                enabled = state.selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (q.isMultiResponse) "Comprobar (selecciona todas las correctas)"
                    else "Comprobar"
                )
            }
        } else {
            val isCorrect = state.selected == correct
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isCorrect) successBg else errorBg,
                contentColor = if (isCorrect) successFg else errorFg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = if (isCorrect) "Correcto" else "Incorrecto",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(q.explanationFor(lang))
                    if (q.tipFor(lang).isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Tip: ${q.tipFor(lang)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (!isCorrect) {
                OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text("Otra pregunta")
                }
            }
        }
    }
}

@Composable
private fun successColors(): Pair<Color, Color> = if (isSystemInDarkTheme())
    Color(0xFF1B5E20) to Color(0xFFE8F5E9)
else
    Color(0xFFE8F5E9) to Color(0xFF1B5E20)

@Composable
private fun errorColors(): Pair<Color, Color> = if (isSystemInDarkTheme())
    Color(0xFF7F1D1D) to Color(0xFFFFEBEE)
else
    Color(0xFFFFEBEE) to Color(0xFFB71C1C)


package com.codecsrayo.quizgate

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codecsrayo.quizgate.ui.theme.AskTheme
import kotlinx.coroutines.launch

class QuizActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TRIGGER_PKG = "trigger_pkg"
        const val EXTRA_PRACTICE_MODE = "practice_mode"
        private const val TAG = "QuizGate"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val practice = intent?.getBooleanExtra(EXTRA_PRACTICE_MODE, false) ?: false
        Log.i(TAG, "QuizActivity onCreate practice=$practice")

        // Manifest defaults the task to excludeFromRecents=true so the gate
        // can't be dismissed via the recents UI. Practice mode is voluntary
        // study and must allow normal multitasking — flip the flag per launch
        // since singleTask reuses the same task across modes.
        runCatching {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.appTasks
                .firstOrNull { it.taskInfo.taskId == taskId }
                ?.setExcludeFromRecents(!practice)
        }.onFailure { Log.w(TAG, "setExcludeFromRecents failed", it) }

        WatchdogService.start(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (practice) finishAndRemoveTask()
                // gate mode: swallow back
            }
        })

        setContent {
            AskTheme {
                var lang by remember { mutableStateOf(Prefs.getLang(this@QuizActivity)) }
                LocalizedContent(lang) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        QuizGateScreen(
                            practiceMode = practice,
                            lang = lang,
                            onLangChange = { newLang ->
                                lang = newLang
                                Prefs.setLang(this@QuizActivity, newLang)
                            },
                            onUnlock = {
                            Log.i(TAG, "QuizActivity onUnlock pressed practice=$practice")
                            if (!practice) {
                                val pkg = Prefs.getLastBlockedPackage(this)
                                if (pkg != null) {
                                    Prefs.setPendingUnlock(this, pkg)
                                    packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
                                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        startActivity(launch)
                                    }
                                }
                            }
                            finishAndRemoveTask()
                        },
                        onCancel = {
                            Log.i(TAG, "QuizActivity onCancel pressed practice=$practice")
                            if (practice) {
                                finishAndRemoveTask()
                            } else {
                                Prefs.clearPendingUnlock(this)
                                val home = Intent(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_HOME)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { startActivity(home) }
                                finishAndRemoveTask()
                            }
                        },
                    )
                }
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
    data class Error(@param:StringRes val messageRes: Int, val detail: String? = null) : UiState
    data class Showing(
        val question: Question,
        // Maps the position the user sees -> the original index in question.options.
        // Lets us shuffle the visible order while keeping `answer`/`answers` valid.
        val permutation: List<Int>,
        val selected: Set<Int>,
        val checked: Boolean,
    ) : UiState
}

@Composable
private fun QuizGateScreen(
    practiceMode: Boolean,
    lang: String,
    onLangChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { QuizRepository(context) }
    val scope = rememberCoroutineScope()

    var pool by remember { mutableStateOf<List<Question>>(emptyList()) }
    var state by remember { mutableStateOf<UiState>(UiState.Loading) }
    var mode by remember { mutableStateOf<QuizMode.Mode?>(null) }

    fun pickRandom(): UiState {
        val now = System.currentTimeMillis()
        val anchor = Prefs.getQuizModeAnchorMs(context, now)
        val activeMode = QuizMode.current(anchor, now)
        mode = activeMode
        val result = QuizSelector.pick(
            pool = pool,
            includeOfficial = Prefs.isIncludeOfficial(context),
            includeSynthetic = Prefs.isIncludeSynthetic(context),
            enabledDomains = Prefs.getEnabledDomainsOrNull(context),
            recentIds = Prefs.getRecentQuestionIds(context).toSet(),
            stats = Prefs.getStats(context),
            mode = activeMode,
        )
        return when (result) {
            QuizSelector.Result.NoCache -> UiState.Error(R.string.quiz_err_no_cache)
            QuizSelector.Result.NoSourceSelected -> UiState.Error(R.string.quiz_err_no_source)
            QuizSelector.Result.NoSourceQuestions -> UiState.Error(R.string.quiz_err_no_source_questions)
            QuizSelector.Result.NoDomain -> UiState.Error(R.string.quiz_err_no_domain)
            is QuizSelector.Result.Picked -> {
                val maxRecent = (pool.size / 2).coerceIn(5, 30)
                Prefs.pushRecentQuestionId(context, result.question.id, maxRecent)
                Prefs.recordShown(context, result.question.id)
                Log.i("QuizGate", "pickRandom id=${result.question.id} perm=${result.permutation}")
                UiState.Showing(
                    result.question,
                    permutation = result.permutation,
                    selected = emptySet(),
                    checked = false,
                )
            }
        }
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
                state = UiState.Error(R.string.quiz_err_download, it.message ?: "error")
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
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            mode?.let { m ->
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            stringResource(
                                when (m) {
                                    QuizMode.Mode.Novedad -> R.string.quiz_mode_novelty
                                    QuizMode.Mode.Refuerzo -> R.string.quiz_mode_reinforcement
                                }
                            )
                        )
                    },
                )
                Spacer(Modifier.width(8.dp))
            }
            FilterChip(
                selected = lang == "es",
                onClick = {
                    Log.i("QuizGate", "Lang chip tap -> es (was=$lang)")
                    onLangChange("es")
                },
                label = { Text("ES") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = lang == "en",
                onClick = {
                    Log.i("QuizGate", "Lang chip tap -> en (was=$lang)")
                    onLangChange("en")
                },
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
                    Text(
                        if (s.detail != null) stringResource(s.messageRes, s.detail)
                        else stringResource(s.messageRes)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        scope.launch {
                            state = UiState.Loading
                            repo.refreshFromNetwork().onSuccess {
                                pool = repo.loadCached()
                                state = pickRandom()
                            }.onFailure {
                                state = UiState.Error(R.string.quiz_err_offline, it.message ?: "error")
                            }
                        }
                    }) { Text(stringResource(R.string.quiz_retry)) }
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
                    onCheck = {
                        val selectedOriginal = s.selected.mapTo(HashSet()) { s.permutation[it] }
                        val correct = selectedOriginal == s.question.correctAnswers()
                        Prefs.recordAnswer(context, s.question.id, correct)
                        state = s.copy(checked = true)
                    },
                    onNext = { state = pickRandom() },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            val current = state as? UiState.Showing
            val isCorrect = current != null && current.checked &&
                current.selected.mapTo(HashSet()) { current.permutation[it] } ==
                current.question.correctAnswers()
            if (practiceMode) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.quiz_exit)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { state = pickRandom() },
                    enabled = state is UiState.Showing,
                    modifier = Modifier.weight(2f)
                ) { Text(stringResource(R.string.quiz_another)) }
            } else {
                Button(
                    onClick = onUnlock,
                    enabled = isCorrect,
                    modifier = Modifier.weight(2f)
                ) {
                    Text(
                        stringResource(
                            if (isCorrect) R.string.quiz_continue
                            else R.string.quiz_continue_disabled
                        )
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.quiz_cancel))
                }
            }
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
    val stmt = q.statement(lang)
    val rawOpts = q.optionsFor(lang)
    // Defensive: if the per-language option list mismatches the permutation length
    // (data shape changed mid-quiz), fall back to identity ordering.
    val perm = if (state.permutation.size == rawOpts.size) state.permutation
        else rawOpts.indices.toList()
    Log.i("QuizGate", "QuestionView render id=${q.id} lang=$lang qKeys=${q.q.keys} stmtPreview='${stmt.take(40)}' perm=$perm")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(
                onClick = {},
                label = { Text(q.domainText(lang)) },
                leadingIcon = if (q.synthetic) {
                    {
                        Icon(
                            Icons.Filled.Science,
                            contentDescription = stringResource(R.string.quiz_synthetic_cd),
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    }
                } else null,
            )
        }

        Text(stmt, style = MaterialTheme.typography.titleMedium)

        val (successBg, successFg) = successColors()
        val (errorBg, errorFg) = errorColors()

        perm.forEachIndexed { idx, originalIdx ->
            val opt = rawOpts[originalIdx]
            val isSelected = idx in state.selected
            val isCorrectOpt = originalIdx in correct
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
                    stringResource(
                        if (q.isMultiResponse) R.string.quiz_check_multi
                        else R.string.quiz_check
                    )
                )
            }
        } else {
            val isCorrect = state.selected.mapTo(HashSet()) { perm[it] } == correct
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isCorrect) successBg else errorBg,
                contentColor = if (isCorrect) successFg else errorFg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(
                            if (isCorrect) R.string.quiz_correct else R.string.quiz_incorrect
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(q.explanationFor(lang))
                    if (q.tipFor(lang).isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.quiz_tip, q.tipFor(lang)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (!isCorrect) {
                OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.quiz_another))
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


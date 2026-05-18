package com.example.ask

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.FlowRow
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ask.ui.theme.AskTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored — watchdog notification is low-priority */ }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        WatchdogService.start(this)
        GlossaryNotifications.ensureChannel(this)
        GlossaryPushWorker.applyFromPrefs(this)

        setContent {
            AskTheme {
                AppRoot()
            }
        }
    }
}

private enum class Screen { Home, Config }

@Composable
private fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    val context = LocalContext.current
    var lang by remember { mutableStateOf(Prefs.getLang(context)) }

    BackHandler(enabled = screen == Screen.Config) { screen = Screen.Home }

    LocalizedContent(lang) {
        when (screen) {
            Screen.Home -> HomeScreen(
                lang = lang,
                onStartQuiz = {
                    val intent = Intent(context, QuizActivity::class.java)
                        .putExtra(QuizActivity.EXTRA_PRACTICE_MODE, true)
                    context.startActivity(intent)
                },
                onOpenConfig = { screen = Screen.Config },
            )
            Screen.Config -> ConfigScreen(
                lang = lang,
                onLangChange = { newLang ->
                    lang = newLang
                    Prefs.setLang(context, newLang)
                },
                onBack = { screen = Screen.Home },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(lang: String, onStartQuiz: () -> Unit, onOpenConfig: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repo = remember { QuizRepository(context) }
    val scope = rememberCoroutineScope()

    var pool by remember { mutableStateOf<List<Question>>(emptyList()) }
    var availableDomains by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var enabledDomains by remember { mutableStateOf<Set<String>?>(Prefs.getEnabledDomainsOrNull(context)) }
    var includeOfficial by remember { mutableStateOf(Prefs.isIncludeOfficial(context)) }
    var includeSynthetic by remember { mutableStateOf(Prefs.isIncludeSynthetic(context)) }
    var perms by remember {
        mutableStateOf(
            PermStatus(
                accessibility = Permissions.isAccessibilityEnabled(context),
                overlay = Permissions.canDrawOverlays(context),
                battery = Permissions.isIgnoringBatteryOptimizations(context),
            )
        )
    }

    fun refreshDomains(cached: List<Question>) {
        availableDomains = cached
            .groupBy { it.domain }
            .map { (dom, qs) -> dom to (qs.first().domainText(lang).ifBlank { dom }) }
            .sortedBy { it.second }
    }

    LaunchedEffect(Unit) {
        val cached = repo.loadCached()
        pool = cached
        refreshDomains(cached)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                perms = PermStatus(
                    accessibility = Permissions.isAccessibilityEnabled(context),
                    overlay = Permissions.canDrawOverlays(context),
                    battery = Permissions.isIgnoringBatteryOptimizations(context),
                )
                enabledDomains = Prefs.getEnabledDomainsOrNull(context)
                includeOfficial = Prefs.isIncludeOfficial(context)
                includeSynthetic = Prefs.isIncludeSynthetic(context)
                scope.launch {
                    val cached = repo.loadCached()
                    pool = cached
                    refreshDomains(cached)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val realCount = pool.count { !it.synthetic }
    val syntheticCount = pool.count { it.synthetic }
    val totalQuestions = realCount + syntheticCount
    val filteredCount = pool.count { q ->
        val sourceOk = (q.synthetic && includeSynthetic) || (!q.synthetic && includeOfficial)
        val domainOk = enabledDomains?.contains(q.domain) ?: true
        sourceOk && domainOk
    }
    val canStart = filteredCount > 0 && (includeOfficial || includeSynthetic)
    val permsOk = perms.accessibility && perms.overlay && perms.battery

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!permsOk) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.home_perms_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC62828),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.home_filters_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.home_filters_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = includeOfficial,
                            onCheckedChange = { checked ->
                                includeOfficial = checked
                                Prefs.setIncludeOfficial(context, checked)
                            },
                        )
                        Text(
                            text = stringResource(R.string.home_source_official) +
                                " (" + realCount + ")",
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = includeSynthetic,
                            onCheckedChange = { checked ->
                                includeSynthetic = checked
                                Prefs.setIncludeSynthetic(context, checked)
                            },
                        )
                        Text(
                            text = stringResource(R.string.home_source_synthetic) +
                                " (" + syntheticCount + ")",
                        )
                    }
                    if (!includeOfficial && !includeSynthetic) {
                        Text(
                            text = stringResource(R.string.home_no_sources),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC62828),
                        )
                    }
                    if (availableDomains.isNotEmpty()) {
                        Text(
                            stringResource(R.string.domain_filter_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        val allKeys = availableDomains.map { it.first }.toSet()
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            availableDomains.forEach { (key, label) ->
                                val current = enabledDomains ?: allKeys
                                val selected = key in current
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val next = current.toMutableSet().apply {
                                            if (selected) remove(key) else add(key)
                                        }
                                        enabledDomains = next
                                        Prefs.setEnabledDomains(context, next)
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onStartQuiz,
                enabled = canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_start_quiz),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!canStart) {
                Text(
                    text = stringResource(R.string.home_start_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedButton(
                onClick = onOpenConfig,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_open_config))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    lang: String,
    onLangChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.config_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                }
            )
        }
    ) { padding ->
        SetupScreen(
            modifier = Modifier.padding(padding),
            lang = lang,
            onLangChange = onLangChange,
        )
    }
}

private data class PermStatus(
    val accessibility: Boolean,
    val overlay: Boolean,
    val battery: Boolean,
)

@Composable
private fun SetupScreen(
    modifier: Modifier = Modifier,
    lang: String,
    onLangChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repo = remember { QuizRepository(context) }
    val snackbar = remember { SnackbarHostState() }

    var perms by remember {
        mutableStateOf(
            PermStatus(
                accessibility = Permissions.isAccessibilityEnabled(context),
                overlay = Permissions.canDrawOverlays(context),
                battery = Permissions.isIgnoringBatteryOptimizations(context),
            )
        )
    }
    var blocked by remember { mutableStateOf(Prefs.getBlockedPackages(context)) }
    var apiUrl by remember { mutableStateOf(Prefs.getApiUrl(context)) }
    var glossaryUrl by remember { mutableStateOf(Prefs.getGlossaryApiUrl(context)) }
    var glossaryPushEnabled by remember { mutableStateOf(Prefs.isGlossaryPushEnabled(context)) }
    var glossaryInterval by remember { mutableIntStateOf(Prefs.getGlossaryPushIntervalMin(context)) }
    var maxMinutes by remember { mutableIntStateOf(Prefs.getMaxInAppMinutes(context)) }
    var realCount by remember { mutableIntStateOf(0) }
    var syntheticCount by remember { mutableIntStateOf(0) }
    var lastFetch by remember { mutableLongStateOf(Prefs.getLastFetch(context)) }
    var syncing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cached = repo.loadCached()
        realCount = cached.count { !it.synthetic }
        syntheticCount = cached.count { it.synthetic }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                perms = PermStatus(
                    accessibility = Permissions.isAccessibilityEnabled(context),
                    overlay = Permissions.canDrawOverlays(context),
                    battery = Permissions.isIgnoringBatteryOptimizations(context),
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.setup_intro))

            PermissionsCard(
                perms = perms,
                onAccessibility = { Permissions.openAccessibilitySettings(context) },
                onOverlay = { Permissions.requestOverlayPermission(context) },
                onBattery = { Permissions.requestIgnoreBatteryOptimizations(context) },
                onMiuiAutostart = {
                    val ok = Permissions.openMiuiAutostart(context)
                    if (!ok) Permissions.openAppDetailsSettings(context)
                },
                onMiuiOther = {
                    val ok = Permissions.openMiuiOtherPermissions(context)
                    if (!ok) Permissions.openAppDetailsSettings(context)
                },
            )

            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.questions_official_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.questions_cached_count, realCount))
                    Text(
                        if (lastFetch == 0L) stringResource(R.string.never_synced)
                        else stringResource(R.string.last_sync, formatTime(lastFetch))
                    )
                    OutlinedTextField(
                        value = apiUrl,
                        onValueChange = { apiUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.api_url_title)) }
                    )
                    Button(
                        enabled = !syncing,
                        onClick = {
                            val cleaned = apiUrl.trim()
                            apiUrl = cleaned
                            Prefs.setApiUrl(context, cleaned)
                            scope.launch {
                                syncing = true
                                repo.refreshFromNetwork()
                                    .onSuccess { n ->
                                        lastFetch = Prefs.getLastFetch(context)
                                        val cached = repo.loadCached()
                                        realCount = cached.count { !it.synthetic }
                                        syntheticCount = cached.count { it.synthetic }
                                        snackbar.showSnackbar(
                                            context.getString(
                                                R.string.sync_result, n, realCount, syntheticCount
                                            )
                                        )
                                    }
                                    .onFailure {
                                        snackbar.showSnackbar(
                                            context.getString(
                                                R.string.sync_error,
                                                it.message ?: context.getString(R.string.error_unknown),
                                            )
                                        )
                                    }
                                syncing = false
                            }
                        }
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.sync_now))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleSmall)
                    Row {
                        FilterChip(
                            selected = lang == "es",
                            onClick = { onLangChange("es") },
                            label = { Text("Español") }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = lang == "en",
                            onClick = { onLangChange("en") },
                            label = { Text("English") }
                        )
                    }
                }
            }

            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.questions_synthetic_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.questions_synthetic_hint), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.questions_cached_count, syntheticCount))
                }
            }

            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.blocked_apps_title), style = MaterialTheme.typography.titleMedium)
                    BlockableApps.ALL.forEach { entry ->
                        val checked = entry.pkg in blocked
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    val newSet = blocked.toMutableSet().apply {
                                        if (isChecked) add(entry.pkg) else remove(entry.pkg)
                                    }
                                    blocked = newSet
                                    Prefs.setBlockedPackages(context, newSet)
                                }
                            )
                            Text(text = entry.label, modifier = Modifier.weight(1f))
                            Text(
                                text = entry.pkg,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.max_in_app_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.max_in_app_hint), style = MaterialTheme.typography.bodySmall)
                    MaxMinutesSelector(
                        value = maxMinutes,
                        onChange = {
                            maxMinutes = it
                            Prefs.setMaxInAppMinutes(context, it)
                        },
                    )
                }
            }

            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.glossary_push_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.glossary_push_hint), style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.glossary_push_enabled), modifier = Modifier.weight(1f))
                        Switch(
                            checked = glossaryPushEnabled,
                            onCheckedChange = { checked ->
                                glossaryPushEnabled = checked
                                Prefs.setGlossaryPushEnabled(context, checked)
                                GlossaryPushWorker.applyFromPrefs(context)
                            }
                        )
                    }
                    Text(stringResource(R.string.glossary_push_interval), style = MaterialTheme.typography.titleSmall)
                    GlossaryIntervalSelector(
                        value = glossaryInterval,
                        onChange = { mins ->
                            glossaryInterval = mins
                            Prefs.setGlossaryPushIntervalMin(context, mins)
                            if (glossaryPushEnabled) GlossaryPushWorker.applyFromPrefs(context)
                        }
                    )
                    Text(stringResource(R.string.glossary_url_title), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = glossaryUrl,
                        onValueChange = { glossaryUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row {
                        Button(
                            enabled = glossaryPushEnabled,
                            onClick = {
                                val cleaned = glossaryUrl.trim()
                                glossaryUrl = cleaned
                                Prefs.setGlossaryApiUrl(context, cleaned)
                                scope.launch {
                                    val repo = GlossaryRepository(context)
                                    var terms = repo.loadCached()
                                    if (terms.isEmpty()) {
                                        val r = repo.refreshFromNetwork()
                                        if (r.isFailure) {
                                            snackbar.showSnackbar(
                                                context.getString(
                                                    R.string.glossary_error,
                                                    r.exceptionOrNull()?.message
                                                        ?: context.getString(R.string.error_unknown),
                                                )
                                            )
                                            return@launch
                                        }
                                        terms = repo.loadCached()
                                    }
                                    if (terms.isEmpty()) {
                                        snackbar.showSnackbar(context.getString(R.string.glossary_empty))
                                        return@launch
                                    }
                                    val term = terms.random()
                                    GlossaryNotifications.postTerm(context, term, lang)
                                    snackbar.showSnackbar(
                                        context.getString(R.string.glossary_sent, term.symbol)
                                    )
                                }
                            }
                        ) { Text(stringResource(R.string.glossary_push_test)) }
                    }
                }
            }

        }
    }
}

@Composable
private fun PermissionsCard(
    perms: PermStatus,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onBattery: () -> Unit,
    onMiuiAutostart: () -> Unit,
    onMiuiOther: () -> Unit,
) {
    val allOk = perms.accessibility && perms.overlay && perms.battery
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.permissions_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (allOk) "OK" else stringResource(R.string.permissions_pending),
                    color = if (allOk) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontWeight = FontWeight.Bold,
                )
            }
            PermissionRow(
                label = stringResource(R.string.perm_accessibility),
                hint = stringResource(R.string.perm_accessibility_hint),
                granted = perms.accessibility,
                action = onAccessibility,
            )
            PermissionRow(
                label = stringResource(R.string.perm_overlay),
                hint = stringResource(R.string.perm_overlay_hint),
                granted = perms.overlay,
                action = onOverlay,
            )
            PermissionRow(
                label = stringResource(R.string.perm_battery),
                hint = stringResource(R.string.perm_battery_hint),
                granted = perms.battery,
                action = onBattery,
            )
            Text(
                text = stringResource(R.string.perm_open_miui_other_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onMiuiOther, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.perm_open_miui_other))
            }
            OutlinedButton(onClick = onMiuiAutostart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.perm_open_miui_autostart))
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    hint: String,
    granted: Boolean,
    action: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (granted) "✓" else "✗",
            color = if (granted) Color(0xFF2E7D32) else Color(0xFFC62828),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            TextButton(onClick = action) { Text(stringResource(R.string.perm_change)) }
        } else {
            Button(onClick = action) { Text(stringResource(R.string.perm_grant)) }
        }
    }
}

private val MAX_MINUTES_OPTIONS = listOf(0, 5, 10, 20, 30, 60)
private val GLOSSARY_INTERVAL_OPTIONS = listOf(15, 30, 60, 180, 360)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GlossaryIntervalSelector(value: Int, onChange: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        GLOSSARY_INTERVAL_OPTIONS.forEachIndexed { idx, minutes ->
            SegmentedButton(
                selected = minutes == value,
                onClick = { onChange(minutes) },
                shape = SegmentedButtonDefaults.itemShape(idx, GLOSSARY_INTERVAL_OPTIONS.size),
                label = { Text(if (minutes < 60) "${minutes}m" else "${minutes / 60}h") }
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MaxMinutesSelector(value: Int, onChange: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        MAX_MINUTES_OPTIONS.forEachIndexed { idx, minutes ->
            SegmentedButton(
                selected = minutes == value,
                onClick = { onChange(minutes) },
                shape = SegmentedButtonDefaults.itemShape(idx, MAX_MINUTES_OPTIONS.size),
                label = { Text(if (minutes == 0) "∞" else "$minutes") }
            )
        }
    }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

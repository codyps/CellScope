package app.cellscope

import android.Manifest
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cellscope.data.BatteryReading
import app.cellscope.data.BatterySample
import app.cellscope.data.ChartMetric
import app.cellscope.data.GapReason
import app.cellscope.data.TimelineGap
import app.cellscope.data.TimelineRange
import app.cellscope.data.categoryFor
import app.cellscope.data.hasValueFor
import app.cellscope.data.valueFor
import app.cellscope.battery.SysfsAccessState
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CellScopeTheme { CellScopeApp(viewModel) } }
    }

    override fun onStart() {
        super.onStart()
        viewModel.ensureRecording()
    }
}

private enum class Screen { LIVE, TIMELINE, SETTINGS }

@Composable
private fun CellScopeApp(viewModel: MainViewModel) {
    var screen by remember { mutableStateOf(Screen.LIVE) }
    val enabled by viewModel.recordingEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var requestedNotification by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(enabled) {
        if (enabled && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !requestedNotification
        ) {
            requestedNotification = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = { AppTopBar(enabled) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen == Screen.LIVE,
                    onClick = { screen = Screen.LIVE },
                    icon = { Text("◉") },
                    label = { Text("Live") },
                )
                NavigationBarItem(
                    selected = screen == Screen.TIMELINE,
                    onClick = { screen = Screen.TIMELINE },
                    icon = { Text("⌁") },
                    label = { Text("Timeline") },
                )
                NavigationBarItem(
                    selected = screen == Screen.SETTINGS,
                    onClick = { screen = Screen.SETTINGS },
                    icon = { Text("⚙") },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        when (screen) {
            Screen.LIVE -> LiveRoute(viewModel, enabled, Modifier.padding(padding))
            Screen.TIMELINE -> TimelineRoute(viewModel, Modifier.padding(padding))
            Screen.SETTINGS -> SettingsRoute(viewModel, enabled, Modifier.padding(padding))
        }
    }
}

@Composable
private fun LiveRoute(viewModel: MainViewModel, enabled: Boolean, modifier: Modifier) {
    val live by viewModel.liveReading.collectAsStateWithLifecycle()
    DashboardScreen(live, enabled, modifier)
}

@Composable
private fun TimelineRoute(viewModel: MainViewModel, modifier: Modifier) {
    val interval by viewModel.sampleIntervalMs.collectAsStateWithLifecycle()
    val collapseGaps by viewModel.collapseGaps.collectAsStateWithLifecycle()
    val samples by viewModel.samples.collectAsStateWithLifecycle()
    val gaps by viewModel.gaps.collectAsStateWithLifecycle()
    val range by viewModel.timelineRange.collectAsStateWithLifecycle()
    TimelineScreen(samples, gaps, range, collapseGaps, interval, viewModel, modifier)
}

@Composable
private fun SettingsRoute(viewModel: MainViewModel, enabled: Boolean, modifier: Modifier) {
    val interval by viewModel.sampleIntervalMs.collectAsStateWithLifecycle()
    val collapseGaps by viewModel.collapseGaps.collectAsStateWithLifecycle()
    val sampleCount by viewModel.sampleCount.collectAsStateWithLifecycle()
    val sysfsAccess by viewModel.sysfsAccess.collectAsStateWithLifecycle()
    SettingsScreen(
        recordingEnabled = enabled,
        intervalMs = interval,
        collapseGaps = collapseGaps,
        sampleCount = sampleCount,
        sysfsAccess = sysfsAccess,
        onRecordingEnabled = viewModel::setRecordingEnabled,
        onInterval = viewModel::setInterval,
        onCollapseGaps = viewModel::setCollapseGaps,
        onDeleteAll = viewModel::deleteAllData,
        onRequestShizuku = viewModel::requestShizukuAccess,
        onRootAccess = viewModel::setRootAccess,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(enabled: Boolean) {
    TopAppBar(title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CellScope", fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(9.dp).background(
                    if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    CircleShape,
                ),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (enabled) "MONITORING" else "PAUSED",
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    })
}

@Composable
private fun DashboardScreen(reading: BatteryReading?, enabled: Boolean, modifier: Modifier = Modifier) {
    val liveMetricGroups = remember(reading) {
        reading?.asSample()?.let(::storedMetricGroups).orEmpty()
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "live-status", contentType = "status") { StatusHero(reading) }
        item(key = "live-recording", contentType = "notice") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (enabled) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (enabled) "Continuous history is on" else "Continuous history is paused",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (enabled) "CellScope records automatically, including while this screen is closed."
                        else "Live values remain visible, but they are not being saved. This interval appears as a gap.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item(key = "live-heading", contentType = "heading") {
            Text("Live telemetry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item(key = "live-primary-1", contentType = "primary-metrics") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Voltage", reading?.voltageMv?.let { "%.3f V".format(it / 1_000f) }, Modifier.weight(1f))
                MetricCard("Current", reading?.currentNowUa?.let { "%+.0f mA".format(it / 1_000f) }, Modifier.weight(1f))
            }
        }
        item(key = "live-primary-2", contentType = "primary-metrics") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Charge", reading?.chargeCounterUah?.let { "%.0f mAh".format(it / 1_000f) }, Modifier.weight(1f))
                MetricCard("Temperature", reading?.temperatureDeciC?.let { "%.1f °C".format(it / 10f) }, Modifier.weight(1f))
            }
        }
        item(key = "live-primary-3", contentType = "primary-metrics") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val power = if (reading?.voltageMv != null && reading.currentNowUa != null) {
                    reading.voltageMv * reading.currentNowUa / 1_000_000_000f
                } else null
                MetricCard("Est. power", power?.let { "%+.2f W".format(it) }, Modifier.weight(1f))
                MetricCard("Avg. current", reading?.currentAverageUa?.let { "%+.0f mA".format(it / 1_000f) }, Modifier.weight(1f))
            }
        }
        if (liveMetricGroups.isNotEmpty()) {
            item(key = "live-extended-heading", contentType = "heading") {
                Text("Extended fuel-gauge data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            liveMetricGroups.forEach { group ->
                item(key = "live-section-${group.title}", contentType = "metric-section") {
                    MetricSectionHeader(group.title)
                }
                items(
                    items = group.metrics,
                    key = { "live-${group.title}-${it.first}" },
                    contentType = { "metric-row" },
                ) { (label, value) -> MetricListRow(label, value) }
            }
        }
        item(key = "live-footer", contentType = "footer") {
            Text(
                buildString {
                    append("Values come from Android's battery APIs")
                    reading?.sysfsProvider?.let { append(" plus ").append(it.lowercase()).append(" sysfs access") }
                    append(". “Not reported” means the device did not expose that metric; it is not zero.")
                    if (!reading?.sysfsFallbackFields.isNullOrEmpty()) {
                        append(" Sysfs supplied: ").append(reading!!.sysfsFallbackFields.sorted().joinToString()).append('.')
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun MetricListRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusHero(reading: BatteryReading?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(82.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    reading?.levelPercent?.let { "%.0f%%".format(it) } ?: "—",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(statusLabel(reading?.status), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(plugLabel(reading?.plugSource), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Live update every second", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String?, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value ?: "Not reported", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TimelineScreen(
    samples: List<BatterySample>,
    gaps: List<TimelineGap>,
    range: TimelineRange,
    collapseGaps: Boolean,
    intervalMs: Long,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    var selectedMetrics by remember { mutableStateOf(setOf(ChartMetric.LEVEL)) }
    var pendingCsv by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingCsv) }
    }
    val effectiveGaps = remember(samples, gaps, intervalMs) {
        effectiveTimelineGaps(samples, gaps, intervalMs, System.currentTimeMillis())
    }
    val availableMetrics = remember(samples) {
        ChartMetric.entries.filter { item ->
            !item.isExtended || samples.any { sample -> sample.hasValueFor(item) }
        }
    }
    val latestExtendedSample = remember(samples) { samples.lastOrNull { it.sysfsProvider != null } }
    val latestMetricGroups = remember(latestExtendedSample) {
        latestExtendedSample?.let(::storedMetricGroups).orEmpty()
    }
    LaunchedEffect(availableMetrics) {
        val retained = selectedMetrics.filterTo(linkedSetOf()) { it in availableMetrics }
        selectedMetrics = retained.ifEmpty {
            availableMetrics.firstOrNull()?.let(::setOf).orEmpty()
        }
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimelineRange.entries.forEach { item ->
                    FilterChip(selected = range == item, onClick = { viewModel.setTimelineRange(item) }, label = { Text(item.title) })
                }
            }
        }
        item(key = "plot-metric-heading", contentType = "heading") {
            Text(
                "Plot metrics · select one or more",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item(key = "plot-metric-selector", contentType = "selector") {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(availableMetrics, key = { it.name }) { item ->
                    FilterChip(
                        selected = item in selectedMetrics,
                        onClick = {
                            selectedMetrics = if (item in selectedMetrics) {
                                selectedMetrics - item
                            } else {
                                selectedMetrics + item
                            }
                        },
                        label = { Text(item.title) },
                    )
                }
            }
        }
        item {
            TimelineChart(
                samples,
                selectedMetrics.sortedBy(ChartMetric::ordinal),
                effectiveGaps,
                collapseGaps,
                intervalMs,
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Collapse gaps", fontWeight = FontWeight.SemiBold)
                    Text("Shorten missing periods but keep a visible marker", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = collapseGaps, onCheckedChange = viewModel::setCollapseGaps)
            }
        }
        if (effectiveGaps.isNotEmpty()) {
            item { Text("Gaps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(effectiveGaps, key = { "${it.startedAtMs}:${it.reason}" }) { gap -> GapCard(gap) }
        }
        if (latestExtendedSample != null) {
            item(key = "stored-header", contentType = "stored-header") {
                StoredExtendedSampleHeader(latestExtendedSample)
            }
            latestMetricGroups.forEach { group ->
                item(key = "stored-section-${group.title}", contentType = "metric-section") {
                    MetricSectionHeader(group.title)
                }
                items(
                    items = group.metrics,
                    key = { "stored-${group.title}-${it.first}" },
                    contentType = { "metric-row" },
                ) { (label, value) -> MetricListRow(label, value) }
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        pendingCsv = viewModel.csv(samples, effectiveGaps)
                        exporter.launch("cellscope-timeline.csv")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export visible timeline") }
        }
    }
}

private data class StoredMetricGroup(
    val title: String,
    val metrics: List<Pair<String, String>>,
)

private fun storedMetricGroups(sample: BatterySample): List<StoredMetricGroup> = listOf(
    StoredMetricGroup(
        "Capacity and voltage",
        listOfNotNull(
            "Learned full threshold" value sample.chargeFullUah?.let { "%.0f mAh".format(it / 1_000f) },
            "Design full threshold" value sample.chargeFullDesignUah?.let { "%.0f mAh".format(it / 1_000f) },
            "Charge voltage threshold" value sample.chargeVoltageLimitMv?.let { "%.3f V".format(it / 1_000f) },
            "Design voltage threshold" value sample.chargeVoltageDesignLimitMv?.let { "%.3f V".format(it / 1_000f) },
            "Charge start threshold" value sample.chargeStartThresholdPercent?.let { "$it%" },
            "Charge end threshold" value sample.chargeEndThresholdPercent?.let { "$it%" },
            "Cycle count" value sample.cycleCount?.toString(),
            "Open-circuit voltage" value sample.voltageOcvMv?.let { "%.3f V".format(it / 1_000f) },
        ),
    ),
    StoredMetricGroup(
        "Fuel gauge",
        listOfNotNull(
            "Fuel-gauge raw SOC" value sample.fuelGaugeRawSoc?.toString(),
            "Internal resistance" value sample.resistanceMicroOhm?.let { "%.1f mΩ".format(it / 1_000f) },
            "Battery profile" value sample.batteryProfile,
            "Battery ID resistance" value sample.batteryIdResistanceOhm?.let { "%.1f kΩ".format(it / 1_000f) },
            "JEITA cool boundary" value sample.jeitaCoolDeciC?.let { "%.1f °C".format(it / 10f) },
            "JEITA warm boundary" value sample.jeitaWarmDeciC?.let { "%.1f °C".format(it / 10f) },
            "SOC reporting ready" value sample.socReportingReady.yesNo(),
            "ESR update count" value sample.esrCount?.toString(),
            "Cycle depth bins" value sample.cycleCountBins,
            "Technology" value sample.technology,
        ),
    ),
    StoredMetricGroup(
        "Charging policy",
        listOfNotNull(
            "Charge type" value sample.chargeType,
            "Charge current limit" value sample.chargeCurrentLimitUa?.let { "%.0f mA".format(it / 1_000f) },
            "Input current limit" value sample.inputCurrentLimitUa?.let { "%.0f mA".format(it / 1_000f) },
            "Input voltage limit" value sample.inputVoltageLimitMv?.let { "%.3f V".format(it / 1_000f) },
            "Input current limited" value sample.inputCurrentLimited.yesNo(),
            "AICL complete" value sample.aiclComplete.yesNo(),
            "Restricted charging" value sample.restrictedCharging.yesNo(),
            "Battery charging enabled" value sample.batteryChargingEnabled.yesNo(),
            "Input charging enabled" value sample.chargingEnabled.yesNo(),
            "Safety timer enabled" value sample.safetyTimerEnabled.yesNo(),
            "Charger over-voltage" value sample.chargerOverVoltage.yesNo(),
            "Overload" value sample.overload.yesNo(),
            "USB overheat" value sample.usbOverheat.yesNo(),
        ),
    ),
    StoredMetricGroup(
        "USB input",
        listOfNotNull(
            "Power source" value sample.powerSupplyType,
            "Present" value sample.usbPresent.yesNo(),
            "Online" value sample.usbOnline.yesNo(),
            "Current limit" value sample.usbCurrentMaxUa?.let { "%.0f mA".format(it / 1_000f) },
            "Voltage limit" value sample.usbVoltageMaxMv?.let { "%.3f V".format(it / 1_000f) },
            "OTG active" value sample.usbOtg.yesNo(),
            "Health" value sample.usbHealth,
        ),
    ),
    StoredMetricGroup(
        "DC / wireless input",
        listOfNotNull(
            "Present" value sample.dcPresent.yesNo(),
            "Online" value sample.dcOnline.yesNo(),
            "Current limit" value sample.dcCurrentMaxUa?.let { "%.0f mA".format(it / 1_000f) },
            "Charging enabled" value sample.dcChargingEnabled.yesNo(),
            "Source type" value sample.dcType,
        ),
    ),
    StoredMetricGroup(
        "Parallel charger",
        listOfNotNull(
            "Present" value sample.parallelPresent.yesNo(),
            "Charging enabled" value sample.parallelChargingEnabled.yesNo(),
            "Status" value sample.parallelStatus,
            "Input limit" value sample.parallelCurrentMaxUa?.let { "%.0f mA".format(it / 1_000f) },
            "Charge limit" value sample.parallelChargeCurrentLimitUa?.let { "%.0f mA".format(it / 1_000f) },
            "Voltage limit" value sample.parallelVoltageMaxMv?.let { "%.3f V".format(it / 1_000f) },
            "Input limited" value sample.parallelInputCurrentLimited.yesNo(),
        ),
    ),
    StoredMetricGroup(
        "Collection",
        listOfNotNull("API fields filled by sysfs" value sample.sysfsFallbackFields?.replace('|', ',')),
    ),
).filter { it.metrics.isNotEmpty() }

private infix fun String.value(value: String?): Pair<String, String>? = value?.let { this to it }

@Composable
private fun StoredExtendedSampleHeader(sample: BatterySample) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Latest stored extended sample", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${formatDateTime(sample.wallTimeMs)} · ${sample.sysfsProvider?.lowercase()?.replaceFirstChar(Char::uppercase)} sysfs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Boolean?.yesNo(): String? = this?.let { if (it) "Yes" else "No" }

@Composable
private fun GapCard(gap: TimelineGap) {
    val end = gap.endedAtMs ?: System.currentTimeMillis()
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(GapReason.label(gap.reason), fontWeight = FontWeight.SemiBold)
            Text(
                "${formatDateTime(gap.startedAtMs)} · ${durationLabel(end - gap.startedAtMs)}",
                style = MaterialTheme.typography.bodySmall,
            )
            gap.details?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

internal data class TimelineLayout(
    val segments: List<List<Pair<Long, Float>>>,
    val gaps: List<Pair<Long, Long>>,
    val start: Long,
    val end: Long,
    val categories: List<String> = emptyList(),
)

@Composable
private fun TimelineChart(
    samples: List<BatterySample>,
    metrics: List<ChartMetric>,
    gaps: List<TimelineGap>,
    collapseGaps: Boolean,
    intervalMs: Long,
) {
    val layouts = remember(samples, metrics, gaps, collapseGaps, intervalMs) {
        metrics.associateWith { metric ->
            buildTimelineLayout(samples, metric, gaps, collapseGaps, intervalMs)
        }
    }
    val baseLayout = layouts.values.firstOrNull()
        ?: buildTimelineLayout(samples, ChartMetric.LEVEL, gaps, collapseGaps, intervalMs)
    val seriesPoints = remember(layouts) { layouts.mapValues { it.value.segments.flatten() } }
    val scaleRanges = remember(seriesPoints) {
        metrics.groupBy { it.scaleKey }.mapValues { (_, unitMetrics) ->
            val values = unitMetrics.flatMap { seriesPoints[it].orEmpty() }.map { it.second }
            val rawMin = values.minOrNull() ?: 0f
            val rawMax = values.maxOrNull() ?: 1f
            val pad = ((rawMax - rawMin) * .08f).coerceAtLeast(.01f)
            rawMin - pad to rawMax + pad
        }
    }
    val seriesColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        Color(0xFFE76F51),
        Color(0xFF2A9D8F),
        Color(0xFFE9C46A),
        Color(0xFF8B5CF6),
        Color(0xFF0EA5E9),
        Color(0xFFEC4899),
    )
    val gapColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    var cursorFraction by remember { mutableFloatStateOf(1f) }
    val cursorValues = metrics.associateWith { metric ->
        seriesPoints[metric].orEmpty().minByOrNull { point ->
            kotlin.math.abs(
                (point.first - baseLayout.start).toDouble() /
                    (baseLayout.end - baseLayout.start).coerceAtLeast(1) - cursorFraction,
            )
        }?.second
    }

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        when (metrics.size) {
                            0 -> "Select metrics to plot"
                            1 -> metrics.single().title
                            else -> "${metrics.size} metrics"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("${samples.size} samples · ${gaps.size} gaps", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (metrics.size > 1) {
                Text(
                    "Metrics with different units use independent scales.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (metrics.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(metrics, key = { it.name }) { metric ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(9.dp).background(
                                    seriesColors[metrics.indexOf(metric) % seriesColors.size],
                                    CircleShape,
                                ),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "${metric.title}: ${formatChartValue(metric, cursorValues[metric], layouts[metric])}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            Canvas(
                Modifier.fillMaxWidth().height(280.dp).padding(top = 12.dp)
                    .background(surfaceColor, RoundedCornerShape(12.dp))
                    .semantics {
                        contentDescription =
                            "Timeline with ${metrics.size} metrics, ${seriesPoints.values.sumOf { it.size }} points, and ${gaps.size} gaps"
                    }
                    .pointerInput(seriesPoints) {
                        detectHorizontalDragGestures { change, _ ->
                            cursorFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    },
            ) {
                repeat(5) { index ->
                    val y = size.height * index / 4f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                val span = (baseLayout.end - baseLayout.start).coerceAtLeast(1).toFloat()
                baseLayout.gaps.forEach { gap ->
                    val left = (gap.first - baseLayout.start) / span * size.width
                    val right = (gap.second - baseLayout.start) / span * size.width
                    drawRect(
                        color = gapColor.copy(alpha = .28f),
                        topLeft = Offset(left, 0f),
                        size = Size((right - left).coerceAtLeast(7f), size.height),
                    )
                    drawLine(gapColor, Offset(left, 0f), Offset(left, size.height), strokeWidth = 2f)
                }
                metrics.forEachIndexed { metricIndex, metric ->
                    val layout = layouts.getValue(metric)
                    val (minY, maxY) = scaleRanges.getValue(metric.scaleKey)
                    layout.segments.forEach { segment ->
                        if (segment.size < 2) return@forEach
                        val path = Path()
                        segment.forEachIndexed { index, point ->
                            val x = (point.first - baseLayout.start) / span * size.width
                            val y = size.height - (point.second - minY) / (maxY - minY) * size.height
                            if (index == 0) {
                                path.moveTo(x, y)
                            } else if (metric.isCategorical) {
                                val previousY = size.height - (segment[index - 1].second - minY) / (maxY - minY) * size.height
                                path.lineTo(x, previousY)
                                path.lineTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                        drawPath(
                            path,
                            seriesColors[metricIndex % seriesColors.size],
                            style = Stroke(width = 4f, cap = StrokeCap.Round),
                        )
                    }
                }
                if (metrics.isNotEmpty()) {
                    val cursorX = size.width * cursorFraction
                    drawLine(gridColor, Offset(cursorX, 0f), Offset(cursorX, size.height), 2f)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (samples.isEmpty()) "No samples" else formatShortDate(baseLayout.start), style = MaterialTheme.typography.labelSmall)
                Text(if (collapseGaps) "Gaps shortened" else formatShortDate(baseLayout.end), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private val ChartMetric.scaleKey: String
    get() = if (isCategorical) name else unit

private fun formatChartValue(metric: ChartMetric, value: Float?, layout: TimelineLayout?): String {
    if (value == null) return "No data"
    if (metric.isBinary) return if (value >= .5f) "Yes" else "No"
    if (metric.isCategorical) {
        val category = layout?.categories?.getOrNull(value.toInt()) ?: return "No data"
        return when (metric) {
            ChartMetric.STATUS -> statusLabel(category.toIntOrNull())
            ChartMetric.PLUG_SOURCE -> plugLabel(category.toIntOrNull())
            ChartMetric.HEALTH -> healthLabel(category.toIntOrNull())
            else -> category
        }
    }
    return "%.2f %s".format(value, metric.unit)
}

@Composable
private fun SettingsScreen(
    recordingEnabled: Boolean,
    intervalMs: Long,
    collapseGaps: Boolean,
    sampleCount: Long,
    sysfsAccess: SysfsAccessState,
    onRecordingEnabled: (Boolean) -> Unit,
    onInterval: (Long) -> Unit,
    onCollapseGaps: (Boolean) -> Unit,
    onDeleteAll: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRootAccess: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showIntervals by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Collection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SettingSwitch(
            title = "Continuous recording",
            detail = "Starts automatically after launch, reboot, and app updates. Turning it off creates a labelled timeline gap.",
            checked = recordingEnabled,
            onCheckedChange = onRecordingEnabled,
        )
        Card(onClick = { showIntervals = true }, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Sample interval", fontWeight = FontWeight.SemiBold)
                    Text("Applied to the next sample", style = MaterialTheme.typography.bodySmall)
                }
                Text(if (intervalMs < 60_000) "${intervalMs / 1_000} sec" else "1 min", color = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider()
        Text("Extended battery data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(sysfsAccess.detail, fontWeight = FontWeight.SemiBold)
                Text(
                    "CellScope can read Linux power-supply attributes when Android omits a value. Access is read-only and limited to /sys/class/power_supply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!sysfsAccess.shizukuPermissionGranted) {
                    Button(onClick = onRequestShizuku, modifier = Modifier.fillMaxWidth()) {
                        Text(if (sysfsAccess.shizukuRunning) "Grant Shizuku access" else "Connect Shizuku")
                    }
                } else {
                    Text("Shizuku permission granted", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        SettingSwitch(
            title = "Root fallback",
            detail = "Use a cached libsu root shell when direct and Shizuku access are unavailable. Enabling may open your root manager's consent prompt.",
            checked = sysfsAccess.rootEnabled,
            onCheckedChange = onRootAccess,
        )
        HorizontalDivider()
        Text("Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SettingSwitch(
            title = "Collapse gaps by default",
            detail = "Compress long gaps while retaining a highlighted marker and its full duration and reason.",
            checked = collapseGaps,
            onCheckedChange = onCollapseGaps,
        )
        HorizontalDivider()
        Text("Stored data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("$sampleCount samples stored locally. CellScope has no internet permission.")
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete all timeline data") }
        Text(
            "Android can restart the sticky recorder after ordinary process termination. A force-stop or the system Active Apps Stop control intentionally prevents immediate restart; CellScope annotates that interruption on the next launch.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (showIntervals) {
        AlertDialog(
            onDismissRequest = { showIntervals = false },
            title = { Text("Sample interval") },
            text = {
                Column {
                    listOf(1_000L, 5_000L, 10_000L, 30_000L, 60_000L).forEach { value ->
                        TextButton(onClick = { onInterval(value); showIntervals = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (value < 60_000) "Every ${value / 1_000} seconds" else "Every minute")
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete the entire timeline?") },
            text = { Text("All samples and gap annotations will be permanently removed. Recording remains enabled.") },
            confirmButton = {
                Button(onClick = { onDeleteAll(); confirmDelete = false }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

internal fun effectiveTimelineGaps(
    samples: List<BatterySample>,
    storedGaps: List<TimelineGap>,
    intervalMs: Long,
    nowMs: Long,
): List<TimelineGap> {
    val threshold = maxOf(intervalMs * 3, 30_000)
    val inferred = samples.zipWithNext().mapNotNull { (before, after) ->
        if (after.wallTimeMs - before.wallTimeMs <= threshold) return@mapNotNull null
        val start = before.wallTimeMs + intervalMs
        val end = after.wallTimeMs
        val alreadyCovered = storedGaps.any { gap ->
            val gapEnd = gap.endedAtMs ?: nowMs
            gap.startedAtMs <= end && gapEnd >= start
        }
        if (alreadyCovered) null else TimelineGap(
            startedAtMs = start,
            endedAtMs = end,
            reason = GapReason.RECORDER_INTERRUPTED,
            details = "Samples were unavailable during this interval.",
        )
    }
    return (storedGaps + inferred).sortedBy { it.startedAtMs }
}

internal fun buildTimelineLayout(
    samples: List<BatterySample>,
    metric: ChartMetric,
    gaps: List<TimelineGap>,
    collapseGaps: Boolean,
    intervalMs: Long,
): TimelineLayout {
    val categories = if (metric.isCategorical) {
        samples.mapNotNull { it.categoryFor(metric) }.distinct()
    } else {
        emptyList()
    }
    val categoryValues = categories.withIndex().associate { it.value to it.index.toFloat() }
    val rawPoints = samples.mapNotNull { sample ->
        val value = sample.valueFor(metric) ?: sample.categoryFor(metric)?.let(categoryValues::get)
        value?.let { sample.wallTimeMs to it }
    }
    if (samples.isEmpty()) return TimelineLayout(emptyList(), emptyList(), 0, 1)
    val rawStart = samples.first().wallTimeMs
    val lastSampleTime = samples.last().wallTimeMs
    val rawEnd = maxOf(lastSampleTime, gaps.maxOfOrNull { it.endedAtMs ?: lastSampleTime } ?: rawStart)
    val relevantGaps = gaps.mapNotNull { gap ->
        val start = gap.startedAtMs.coerceAtLeast(rawStart)
        val end = (gap.endedAtMs ?: rawEnd).coerceAtMost(rawEnd)
        if (end > start) start to end else null
    }.sortedBy { it.first }
    val replacement = maxOf(intervalMs * 2, (rawEnd - rawStart) / 50)
    fun transform(time: Long): Long {
        if (!collapseGaps) return time
        var removed = 0L
        relevantGaps.forEach { (start, end) ->
            if (time >= end) removed += (end - start - replacement).coerceAtLeast(0)
            else if (time > start) {
                val duration = end - start
                val compressedPosition = (time - start) * replacement / duration
                return start - removed + compressedPosition
            }
        }
        return time - removed
    }
    val segments = mutableListOf<MutableList<Pair<Long, Float>>>()
    var current = mutableListOf<Pair<Long, Float>>()
    rawPoints.forEach { point ->
        val crossesGap = current.lastOrNull()?.let { previous ->
            relevantGaps.any { (start, end) -> previous.first <= start && point.first >= end }
        } == true
        if (crossesGap && current.isNotEmpty()) {
            segments += current
            current = mutableListOf()
        }
        current += point.first to point.second
    }
    if (current.isNotEmpty()) segments += current
    val transformedSegments = segments.map { segment ->
        downsampleTelemetry(segment.map { transform(it.first) to it.second }, 600)
    }
    return TimelineLayout(
        segments = transformedSegments,
        gaps = relevantGaps.map { transform(it.first) to transform(it.second) },
        start = transform(rawStart),
        end = transform(rawEnd).coerceAtLeast(transform(rawStart) + 1),
        categories = categories,
    )
}

internal fun downsampleTelemetry(values: List<Pair<Long, Float>>, maxPoints: Int): List<Pair<Long, Float>> {
    if (values.size <= maxPoints || maxPoints < 4) return values
    val bucketCount = maxPoints / 2
    val bucketSize = kotlin.math.ceil(values.size.toDouble() / bucketCount).toInt()
    val result = ArrayList<Pair<Long, Float>>(maxPoints)
    values.chunked(bucketSize).forEach { bucket ->
        val min = bucket.minBy { it.second }
        val max = bucket.maxBy { it.second }
        if (min.first <= max.first) {
            result += min
            if (max != min) result += max
        } else {
            result += max
            result += min
        }
    }
    return result
}

internal fun durationLabel(milliseconds: Long): String {
    val seconds = (milliseconds / 1_000).coerceAtLeast(0)
    val days = seconds / 86_400
    val hours = seconds % 86_400 / 3_600
    val minutes = seconds % 3_600 / 60
    val remainder = seconds % 60
    return when {
        days > 0 -> String.format(Locale.US, "%dd %02dh", days, hours)
        hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
        minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, remainder)
        else -> "${remainder}s"
    }
}

private fun statusLabel(status: Int?): String = when (status) {
    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
    BatteryManager.BATTERY_STATUS_FULL -> "Full"
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
    else -> "Reading battery…"
}

private fun plugLabel(plug: Int?): String = when (plug) {
    BatteryManager.BATTERY_PLUGGED_AC -> "Connected to AC power"
    BatteryManager.BATTERY_PLUGGED_USB -> "Connected by USB"
    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless charging"
    0 -> "Running on battery"
    else -> "External power"
}

private fun healthLabel(health: Int?): String = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-voltage"
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
    BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
    else -> "Unknown"
}

private fun formatDateTime(timestamp: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT,
).format(Date(timestamp))

private fun formatShortDate(timestamp: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.SHORT,
    DateFormat.SHORT,
).format(Date(timestamp))

@Composable
private fun CellScopeTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF69D89D),
        secondary = Color(0xFF8FC9FF),
    ) else lightColorScheme(
        primary = Color(0xFF006D46),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF9CF5C1),
        onPrimaryContainer = Color(0xFF002114),
        secondary = Color(0xFF286488),
        background = Color(0xFFF7F9F5),
        surface = Color(0xFFF7F9F5),
        surfaceVariant = Color(0xFFE1E8E1),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

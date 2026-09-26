package com.skt.visionpulse

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class DashboardActivity : AppCompatActivity() {
    private lateinit var captureLauncher: ActivityResultLauncher<Intent>
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        captureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, CaptureService::class.java)
                    .setAction(CaptureService.ACTION_START)
                    .putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    .putExtra(CaptureService.EXTRA_DATA, result.data)
                ContextCompat.startForegroundService(this, serviceIntent)
            }
        }

        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (Settings.canDrawOverlays(this)) requestScreenCapture()
        }

        requestNotificationPermissionIfNeeded()

        setContentView(
            ComposeView(this).apply {
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                setContent {
                    VisionPulseTheme {
                        VisionPulseApp(
                            context = this@DashboardActivity,
                            onStart = ::startLiveMode,
                            onStop = {
                                stopService(Intent(this@DashboardActivity, CaptureService::class.java))
                            }
                        )
                    }
                }
            }
        )
    }

    private fun startLiveMode() {
        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName)
                )
            )
            return
        }
        requestScreenCapture()
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                41
            )
        }
    }
}

private val VisionDark = Color(0xFF080B10)
private val VisionSurface = Color(0xFF11161E)
private val VisionSurfaceHigh = Color(0xFF171E28)
private val VisionBorder = Color(0xFF25303D)
private val VisionAccent = Color(0xFF6FE3D0)
private val VisionBlue = Color(0xFF77AFFF)
private val VisionAmber = Color(0xFFFFBE6A)
private val VisionRed = Color(0xFFFF7486)
private val VisionMuted = Color(0xFF94A3B8)

private val visionColors = darkColorScheme(
    primary = VisionAccent,
    onPrimary = Color(0xFF04221C),
    secondary = VisionBlue,
    tertiary = VisionAmber,
    background = VisionDark,
    surface = VisionSurface,
    surfaceVariant = VisionSurfaceHigh,
    outline = VisionBorder,
    error = VisionRed
)

@Composable
private fun VisionPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = visionColors, content = content)
}

private enum class DashboardPage(val label: String) {
    Objects("Objects"),
    Session("Session"),
    Compare("Compare")
}

private enum class ObjectFilter(val label: String) {
    All("All"),
    Tracking("Tracking"),
    Lost("Lost"),
    Ended("Ended")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisionPulseApp(
    context: Context,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    var page by remember { mutableStateOf(DashboardPage.Objects) }
    var tracking by remember {
        mutableStateOf(
            ObjectTrackingStore.snapshot(
                DetectionStore.getInputSize(),
                DetectionStore.getConfidenceThreshold()
            )
        )
    }
    var detector by remember { mutableStateOf(DetectionStore.getSnapshot()) }
    var selectedObject by remember { mutableStateOf<ObjectTrackingStore.ObjectView?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            detector = DetectionStore.getSnapshot()
            tracking = ObjectTrackingStore.snapshot(
                DetectionStore.getInputSize(),
                DetectionStore.getConfidenceThreshold()
            )
            delay(350)
        }
    }

    Scaffold(
        containerColor = VisionDark,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VisionDark,
                    titleContentColor = Color.White
                ),
                title = {
                    Column {
                        Text(
                            "VisionPulse",
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.4).sp
                        )
                        Text(
                            if (DetectionStore.isRunning()) "LIVE OBJECT TRACKING" else "VISION ANALYTICS",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (DetectionStore.isRunning()) VisionAccent else VisionMuted
                        )
                    }
                },
                actions = {
                    AssistChip(
                        onClick = { showSettings = true },
                        label = {
                            Text(
                                DetectionStore.getInputSize().toString() + " px  ·  conf " +
                                    String.format(
                                        Locale.US,
                                        "%.2f",
                                        DetectionStore.getConfidenceThreshold()
                                    )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0D1219),
                windowInsets = WindowInsets.navigationBars
            ) {
                DashboardPage.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = page == destination,
                        onClick = { page = destination },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VisionAccent,
                            selectedTextColor = Color.White,
                            indicatorColor = Color(0xFF17312E),
                            unselectedIconColor = VisionMuted,
                            unselectedTextColor = VisionMuted
                        ),
                        icon = {
                            Icon(
                                imageVector = when (destination) {
                                    DashboardPage.Objects -> Icons.Rounded.TrackChanges
                                    DashboardPage.Session -> Icons.Rounded.Insights
                                    DashboardPage.Compare -> Icons.Rounded.CompareArrows
                                },
                                contentDescription = null
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = if (DetectionStore.isRunning()) onStop else onStart,
                containerColor = if (DetectionStore.isRunning()) Color(0xFF4B222A) else VisionAccent,
                contentColor = if (DetectionStore.isRunning()) Color.White else Color(0xFF04221C),
                icon = {
                    Icon(
                        if (DetectionStore.isRunning()) Icons.Rounded.StopCircle else Icons.Rounded.PlayArrow,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        if (DetectionStore.isRunning()) "Stop live test" else "Start live test",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (page) {
                DashboardPage.Objects -> ObjectsPage(tracking) { selectedObject = it }
                DashboardPage.Session -> SessionPage(detector, tracking)
                DashboardPage.Compare -> ComparePage(context)
            }
        }
    }

    selectedObject?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { selectedObject = null },
            containerColor = VisionSurface,
            contentColor = Color.White
        ) {
            ObjectDetailSheet(item)
        }
    }

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ObjectsPage(
    snapshot: ObjectTrackingStore.Snapshot,
    selectedObject: (ObjectTrackingStore.ObjectView) -> Unit
) {
    var filter by remember { mutableStateOf(ObjectFilter.All) }

    val filtered = snapshot.objects.filter { item ->
        when (filter) {
            ObjectFilter.All -> true
            ObjectFilter.Tracking -> item.state == "TRACKING"
            ObjectFilter.Lost -> item.state == "LOST"
            ObjectFilter.Ended -> item.state == "ENDED"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 10.dp,
            bottom = 104.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ObjectHero(snapshot) }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ObjectFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (DetectionStore.isRunning()) "Waiting for objects" else "No tracking data yet",
                    body = if (DetectionStore.isRunning()) {
                        "Open the screen you want to test. Detected objects will appear here with stable track IDs."
                    } else {
                        "Start a live test, then move to another app. VisionPulse will track each detected object over time."
                    }
                )
            }
        } else {
            items(filtered, key = { it.trackId }) { item ->
                ObjectCard(item, onClick = { selectedObject(item) })
            }
        }
    }
}

@Composable
private fun ObjectHero(snapshot: ObjectTrackingStore.Snapshot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101A1D)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF24423D))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Tracked objects",
                        color = VisionMuted,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        snapshot.trackedObjects.toString(),
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                StatusPill(
                    text = if (DetectionStore.isRunning()) "LIVE" else "IDLE",
                    color = if (DetectionStore.isRunning()) VisionAccent else VisionMuted
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniMetric(
                    "Visible",
                    snapshot.visibleObjects.toString(),
                    VisionAccent,
                    Modifier.weight(1f)
                )
                MiniMetric(
                    "Lost",
                    snapshot.lostObjects.toString(),
                    VisionAmber,
                    Modifier.weight(1f)
                )
                MiniMetric(
                    "Reacquired",
                    snapshot.reacquiredEvents.toString(),
                    VisionBlue,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ObjectCard(
    item: ObjectTrackingStore.ObjectView,
    onClick: () -> Unit
) {
    val stateColor = when (item.state) {
        "TRACKING" -> VisionAccent
        "LOST" -> VisionAmber
        else -> VisionMuted
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VisionSurface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VisionBorder)
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1B2530)
                ) {
                    Text(
                        "#" + item.trackId,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = VisionBlue,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(Modifier.width(11.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        item.label,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatSeconds(item.elapsedMs) + " elapsed",
                        color = VisionMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                StatusPill(item.state, stateColor)
            }

            Spacer(Modifier.height(15.dp))

            TrackTimeline(
                segments = item.segments,
                totalMs = max(1L, item.elapsedMs),
                accent = stateColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
            )

            Spacer(Modifier.height(13.dp))

            LinearProgressIndicator(
                progress = { item.continuity.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = stateColor,
                trackColor = Color(0xFF202A35)
            )

            Spacer(Modifier.height(13.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DataChip("Visible", formatSeconds(item.visibleMs), Modifier.weight(1f))
                DataChip("Continuity", formatPercent(item.continuity), Modifier.weight(1f))
                DataChip("Confidence", formatPercent(item.averageConfidence), Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DataChip("Lost", item.lostCount.toString(), Modifier.weight(1f))
                DataChip("Reacquired", item.reacquiredCount.toString(), Modifier.weight(1f))
                DataChip(
                    "Longest loss",
                    formatSeconds(item.longestLostMs),
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ObjectDetailSheet(item: ObjectTrackingStore.ObjectView) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(13.dp),
                color = Color(0xFF1A2630)
            ) {
                Text(
                    "#" + item.trackId,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    color = VisionBlue,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.label, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("Object track detail", color = VisionMuted)
            }
            StatusPill(
                item.state,
                when (item.state) {
                    "TRACKING" -> VisionAccent
                    "LOST" -> VisionAmber
                    else -> VisionMuted
                }
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            formatSeconds(item.elapsedMs),
            fontSize = 42.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "TOTAL TRACK TIME",
            color = VisionMuted,
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(Modifier.height(22.dp))

        Text("Visibility timeline", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        TrackTimeline(
            segments = item.segments,
            totalMs = max(1L, item.elapsedMs),
            accent = VisionAccent,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )

        Spacer(Modifier.height(22.dp))
        HorizontalDivider(color = VisionBorder)
        Spacer(Modifier.height(10.dp))

        DetailRow("First seen", formatSeconds(item.firstSeenMs))
        DetailRow("Last seen", formatSeconds(item.lastSeenMs))
        DetailRow("Visible", formatSeconds(item.visibleMs))
        DetailRow("Continuity", formatPercent(item.continuity))
        DetailRow("Lost", item.lostCount.toString())
        DetailRow("Reacquired", item.reacquiredCount.toString())
        DetailRow("Longest loss", formatSeconds(item.longestLostMs))
        DetailRow("Avg confidence", formatPercent(item.averageConfidence))
        DetailRow(
            "Confidence range",
            String.format(Locale.US, "%.2f – %.2f", item.minConfidence, item.maxConfidence)
        )
    }
}

@Composable
private fun SessionPage(
    detector: DetectionStore.DetectionSnapshot,
    tracking: ObjectTrackingStore.Snapshot
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 10.dp,
            bottom = 104.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle("Session health", "Runtime cost and tracking continuity")
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    "FPS",
                    String.format(Locale.US, "%.1f", tracking.averageFps),
                    VisionAccent,
                    Modifier.weight(1f)
                )
                MetricCard(
                    "P95",
                    String.format(Locale.US, "%.0f ms", tracking.p95PipelineMs),
                    VisionBlue,
                    Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    "Continuity",
                    formatPercent(tracking.averageContinuity),
                    VisionAmber,
                    Modifier.weight(1f)
                )
                MetricCard(
                    "Dropped",
                    formatPercent(tracking.droppedFrameRate),
                    VisionRed,
                    Modifier.weight(1f)
                )
            }
        }

        item {
            RuntimeChartCard(
                title = "FPS trend",
                values = tracking.samples.map { it.fps }.filter { it > 0 }
            )
        }

        item {
            RuntimeChartCard(
                title = "Pipeline latency",
                values = tracking.samples.map { it.pipelineMs }.filter { it > 0 },
                suffix = "ms"
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VisionSurface),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VisionBorder)
            ) {
                Column(Modifier.padding(17.dp)) {
                    Text(
                        "Tracking summary",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    DetailRow("Unique objects", tracking.trackedObjects.toString())
                    DetailRow("Visible now", tracking.visibleObjects.toString())
                    DetailRow("Lost now", tracking.lostObjects.toString())
                    DetailRow("Lost events", tracking.lostEvents.toString())
                    DetailRow("Reacquired", tracking.reacquiredEvents.toString())
                    DetailRow(
                        "Processed frames",
                        String.format(Locale.US, "%,d", tracking.processedFrames)
                    )
                    DetailRow("Current detections", detector.currentDetections.toString())
                }
            }
        }

        if (tracking.labels.isNotEmpty()) {
            item {
                SectionTitle("Label rollup", "Object-level totals grouped by class")
            }
            items(tracking.labels, key = { it.label }) { label ->
                LabelSummaryCard(label)
            }
        }
    }
}

@Composable
private fun RuntimeChartCard(
    title: String,
    values: List<Double>,
    suffix: String = ""
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VisionSurface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VisionBorder)
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(
                        "Latest " + values.size.coerceAtMost(180) + " samples",
                        color = VisionMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (values.isNotEmpty()) {
                    Text(
                        String.format(
                            Locale.US,
                            "%.1f%s",
                            values.last(),
                            if (suffix.isEmpty()) "" else " " + suffix
                        ),
                        color = VisionAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (values.size < 2) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(Color(0xFF0C1118), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Collecting runtime samples…", color = VisionMuted)
                }
            } else {
                val model = remember(values) {
                    CartesianChartModel(
                        LineCartesianLayerModel.build {
                            series(y = values)
                        }
                    )
                }
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer()
                    ),
                    model = model,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                )
            }
        }
    }
}

@Composable
private fun LabelSummaryCard(item: ObjectTrackingStore.LabelSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VisionSurface),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VisionBorder)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    item.objectCount.toString() + " objects",
                    color = VisionBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DataChip("Visible", formatSeconds(item.visibleMs), Modifier.weight(1f))
                DataChip(
                    "Continuity",
                    formatPercent(item.averageContinuity),
                    Modifier.weight(1f)
                )
                DataChip(
                    "Reacquired",
                    item.reacquiredCount.toString(),
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ComparePage(context: Context) {
    val history = remember { ObjectSessionHistory.load(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 10.dp,
            bottom = 104.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle(
                "Session compare",
                "Compare speed, continuity, and reacquisition across test settings"
            )
        }

        if (history.isEmpty()) {
            item {
                EmptyState(
                    "No completed sessions",
                    "Finish at least one live test. Recent sessions will appear here automatically."
                )
            }
        } else {
            items(history) { session ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = VisionSurface),
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VisionBorder)
                ) {
                    Column(Modifier.padding(17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    session.inputSize.toString() + " px  ·  conf " +
                                        String.format(Locale.US, "%.2f", session.threshold),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    DateFormat.getDateTimeInstance(
                                        DateFormat.SHORT,
                                        DateFormat.SHORT
                                    ).format(Date(session.startedAt)),
                                    color = VisionMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                formatSeconds(session.durationMs),
                                color = VisionAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DataChip(
                                "Objects",
                                session.trackedObjects.toString(),
                                Modifier.weight(1f)
                            )
                            DataChip(
                                "FPS",
                                String.format(Locale.US, "%.1f", session.averageFps),
                                Modifier.weight(1f)
                            )
                            DataChip(
                                "P95",
                                String.format(Locale.US, "%.0f ms", session.p95LatencyMs),
                                Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DataChip(
                                "Continuity",
                                formatPercent(session.averageContinuity),
                                Modifier.weight(1f)
                            )
                            DataChip(
                                "Lost",
                                session.lostEvents.toString(),
                                Modifier.weight(1f)
                            )
                            DataChip(
                                "Reacquired",
                                session.reacquiredEvents.toString(),
                                Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(onDismiss: () -> Unit) {
    var resolution by remember { mutableIntStateOf(DetectionStore.getInputSize()) }
    var threshold by remember { mutableStateOf(DetectionStore.getConfidenceThreshold()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VisionSurface,
        contentColor = Color.White
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 34.dp)
        ) {
            Text("Live test setup", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Tune inference before starting a new session.",
                color = VisionMuted,
                modifier = Modifier.padding(top = 3.dp, bottom = 20.dp)
            )

            Text("Input resolution", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(9.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(320, 416, 512, 640).forEach { size ->
                    FilterChip(
                        selected = resolution == size,
                        onClick = {
                            resolution = size
                            DetectionStore.setInputSize(size)
                        },
                        label = { Text(size.toString() + " px") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Confidence threshold",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    String.format(Locale.US, "%.2f", threshold),
                    color = VisionAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Slider(
                value = threshold,
                onValueChange = {
                    threshold = it
                    DetectionStore.setConfidenceThreshold(it)
                },
                valueRange = 0.10f..0.90f
            )

            Text(
                "Confidence is a model score, not recognition accuracy.",
                color = VisionMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TrackTimeline(
    segments: List<ObjectTrackingStore.VisibilitySegment>,
    totalMs: Long,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier.background(Color(0xFF0B1016), RoundedCornerShape(999.dp))
    ) {
        val radius = size.height / 2f

        drawRoundRect(
            color = Color(0xFF202A35),
            cornerRadius = CornerRadius(radius, radius)
        )

        segments.forEach { segment ->
            val start = (segment.startMs / totalMs.toFloat()).coerceIn(0f, 1f)
            val end = (segment.endMs / totalMs.toFloat()).coerceIn(start, 1f)
            val left = size.width * start
            val width = max(2f, size.width * (end - start))

            drawRoundRect(
                color = accent,
                topLeft = Offset(left, 0f),
                size = Size(width.coerceAtMost(size.width - left), size.height),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = VisionSurface),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VisionBorder)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = VisionMuted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(7.dp))
            Text(value, color = color, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MiniMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF162026)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = VisionMuted, style = MaterialTheme.typography.labelSmall)
            Text(
                value,
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DataChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = Color(0xFF171F29)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(label, color = VisionMuted, style = MaterialTheme.typography.labelSmall)
            Text(
                value,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            color.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                color = color,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = VisionMuted, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(
            title,
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            subtitle,
            color = VisionMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = VisionSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, VisionBorder)
    ) {
        Column(
            Modifier.padding(horizontal = 22.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.TrackChanges,
                contentDescription = null,
                tint = VisionMuted,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            )
            Spacer(Modifier.height(5.dp))
            Text(
                body,
                color = VisionMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatSeconds(ms: Long): String =
    String.format(Locale.US, "%.1f s", ms / 1000.0)

private fun formatPercent(value: Double): String =
    String.format(Locale.US, "%.0f%%", value.coerceIn(0.0, 1.0) * 100.0)

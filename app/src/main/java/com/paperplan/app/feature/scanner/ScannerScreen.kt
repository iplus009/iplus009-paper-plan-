package com.paperplan.app.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.paperplan.app.core.gpu.AutoQuadEstimator
import com.paperplan.app.core.gpu.BitmapDecoder
import com.paperplan.app.core.gpu.GlImageProcessor
import com.paperplan.app.core.gpu.GpuProcessResult
import com.paperplan.app.core.gpu.HomographySolver
import com.paperplan.app.core.gpu.Point2D
import com.paperplan.app.core.gpu.QuadCorners
import com.paperplan.app.core.gpu.ScanFilterMode
import com.paperplan.app.core.pdf.PdfEngine
import com.paperplan.app.core.storage.WorkspaceDoc
import com.paperplan.app.core.ui.components.GlassWorkspaceCard
import com.paperplan.app.core.ui.components.GpuZoomableBitmapPreview
import com.paperplan.app.core.ui.components.StatusBadgePill
import com.paperplan.app.core.ui.components.WorkspaceDocActionBar
import com.paperplan.app.core.ui.theme.PaperDanger
import com.paperplan.app.core.ui.theme.PaperGlassBorder
import com.paperplan.app.core.ui.theme.PaperInfoText
import com.paperplan.app.core.ui.theme.PaperMint
import com.paperplan.app.core.ui.theme.PaperMuted
import com.paperplan.app.core.ui.theme.PaperScanBlue
import com.paperplan.app.core.ui.theme.PaperViolet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private data class BatchScanPage(
    val id: Long,
    val bitmap: Bitmap,
    val mode: ScanFilterMode
)

@Composable
fun ScannerScreen(
    onOpenViewer: (WorkspaceDoc) -> Unit,
    onWorkspaceChanged: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraActive by remember { mutableStateOf(false) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var quadCorners by remember { mutableStateOf(defaultInsetQuad()) }
    var selectedMode by remember { mutableStateOf(ScanFilterMode.CLEAN_COLOR) }
    var gpuResult by remember { mutableStateOf<GpuProcessResult?>(null) }
    var batchPages by remember { mutableStateOf<List<BatchScanPage>>(emptyList()) }
    var nextBatchId by remember { mutableLongStateOf(1L) }
    var exportedDoc by remember { mutableStateOf<WorkspaceDoc?>(null) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember {
        mutableStateOf("Use Live Camera or import a skewed photo to snap edges on OpenGL ES 2.0.")
    }

    suspend fun runGpuWarp(
        bmp: Bitmap,
        corners: QuadCorners,
        mode: ScanFilterMode
    ) {
        if (!HomographySolver.isConvexValidQuad(corners)) {
            statusText = "Invalid quad: drag corners into a clean non-crossing rectangle."
            return
        }
        busy = true
        statusText = "Running OpenGL ES 2.0 perspective warp + ${mode.label}…"
        runCatching {
            GlImageProcessor.processOnGpu(
                source = bmp,
                corners = corners,
                mode = mode,
                maxDimension = 2048
            )
        }.fold(
            onSuccess = { res ->
                gpuResult = res
                statusText =
                    "GPU: ${res.gpuRenderer} • ${res.elapsedMs}ms (${res.outputWidth}×${res.outputHeight})"
            },
            onFailure = { err ->
                statusText = "GPU warp failed: ${err.message ?: "GLES error"}"
            }
        )
        busy = false
    }

    fun acceptCapturedBitmap(bmp: Bitmap, originLabel: String) {
        scope.launch {
            busy = true
            cameraActive = false
            exportedDoc = null
            statusText = "Auto-detecting page corners from $originLabel…"
            val estimated = withContext(Dispatchers.Default) {
                AutoQuadEstimator.estimateFromBitmap(bmp)
            }
            sourceBitmap = bmp
            quadCorners = estimated
            runGpuWarp(bmp, estimated, selectedMode)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            cameraActive = true
            cameraError = null
            statusText = "Point at any document and tap Capture Page."
        } else {
            cameraActive = false
            statusText = "Camera permission denied — you can still import any photo from Gallery."
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            statusText = "Decoding EXIF-normalized image…"
            runCatching {
                withContext(Dispatchers.IO) {
                    BitmapDecoder.loadExifNormalizedBitmap(context, uri, maxDimension = 2200)
                }
            }.fold(
                onSuccess = { bmp ->
                    acceptCapturedBitmap(bmp, "gallery image")
                },
                onFailure = { err ->
                    busy = false
                    statusText = "Could not load image: ${err.message ?: "Unsupported format"}"
                }
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "scanner_header") {
            GlassWorkspaceCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DocumentScanner,
                            contentDescription = null,
                            tint = PaperScanBlue,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text("GPU Camera Scanner", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "CameraX capture • OpenGL ES 2.0 warp & filters",
                                style = MaterialTheme.typography.bodySmall,
                                color = PaperMuted
                            )
                        }
                    }
                    StatusBadgePill(label = "GLES 2.0", accent = PaperScanBlue)
                }

                Text(statusText, style = MaterialTheme.typography.bodySmall, color = PaperInfoText)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (hasCameraPermission) {
                                cameraActive = !cameraActive
                                cameraError = null
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (cameraActive) PaperDanger else PaperViolet
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (cameraActive) "Close Camera" else "Live Camera", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { pickImageLauncher.launch(arrayOf("image/*")) },
                        enabled = !busy,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Gallery Photo", maxLines = 1)
                    }
                }
            }
        }

        if (cameraActive && hasCameraPermission) {
            item(key = "scanner_camera_viewfinder") {
                GlassWorkspaceCard(elevated = true) {
                    CameraXViewfinderBlock(
                        onImageCaptureReady = { imageCaptureRef = it },
                        onCameraError = { msg ->
                            cameraError = msg
                            statusText = msg
                        }
                    )

                    cameraError?.let { err ->
                        Text(err, style = MaterialTheme.typography.bodySmall, color = PaperDanger)
                    }

                    Button(
                        onClick = {
                            val capture = imageCaptureRef
                            if (capture == null) {
                                statusText = "Camera is still initializing…"
                                return@Button
                            }
                            busy = true
                            statusText = "Capturing full-resolution frame…"
                            capture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        scope.launch {
                                            val decoded = withContext(Dispatchers.IO) {
                                                decodeImageProxyToBitmap(image, maxDimension = 2200)
                                            }
                                            if (decoded != null) {
                                                acceptCapturedBitmap(decoded, "CameraX")
                                            } else {
                                                busy = false
                                                statusText = "Failed to decode captured frame."
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        busy = false
                                        statusText = "Capture error: ${exception.message ?: "Camera failure"}"
                                    }
                                }
                            )
                        },
                        enabled = !busy && cameraError == null,
                        colors = ButtonDefaults.buttonColors(containerColor = PaperMint),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (busy) "Capturing…" else "Capture Page")
                    }
                }
            }
        }

        val src = sourceBitmap
        if (src != null) {
            item(key = "scanner_quad_editor") {
                GlassWorkspaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Drag 4 Page Corners", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val snapped = withContext(Dispatchers.Default) {
                                            AutoQuadEstimator.estimateFromBitmap(src)
                                        }
                                        quadCorners = snapped
                                        runGpuWarp(src, snapped, selectedMode)
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Auto-Snap", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    val full = QuadCorners(
                                        Point2D(0f, 0f),
                                        Point2D(1f, 0f),
                                        Point2D(1f, 1f),
                                        Point2D(0f, 1f)
                                    )
                                    quadCorners = full
                                    scope.launch { runGpuWarp(src, full, selectedMode) }
                                },
                                enabled = !busy,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.Outlined.CropFree, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Full", maxLines = 1)
                            }
                        }
                    }

                    QuadCornerDragEditor(
                        bitmap = src,
                        corners = quadCorners,
                        onCornersChanged = { quadCorners = it }
                    )

                    Text("GPU Shader Mode", style = MaterialTheme.typography.labelLarge, color = PaperMuted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(ScanFilterMode.entries, key = { _, m -> m.name }) { _, mode ->
                            FilterChip(
                                selected = mode == selectedMode,
                                onClick = {
                                    selectedMode = mode
                                    scope.launch { runGpuWarp(src, quadCorners, mode) }
                                },
                                enabled = !busy,
                                label = { Text(mode.label, maxLines = 1) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { scope.launch { runGpuWarp(src, quadCorners, selectedMode) } },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(if (busy) "Processing on GPU…" else "Re-Run GPU Warp & Filter")
                    }
                }
            }
        }

        val processed = gpuResult
        if (processed != null) {
            item(key = "scanner_gpu_output") {
                GlassWorkspaceCard(elevated = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("GPU Processed Output", style = MaterialTheme.typography.titleMedium)
                        StatusBadgePill(label = "${processed.elapsedMs} ms", accent = PaperMint)
                    }

                    Text(
                        "Renderer: ${processed.gpuRenderer} (${processed.gpuVendor}) • ${processed.outputWidth}×${processed.outputHeight}px",
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperMuted
                    )

                    GpuZoomableBitmapPreview(
                        bitmap = processed.bitmap,
                        contentDescription = "GPU processed scan",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val entry = BatchScanPage(
                                    id = nextBatchId++,
                                    bitmap = processed.bitmap,
                                    mode = processed.mode
                                )
                                batchPages = batchPages + entry
                                exportedDoc = null
                                statusText = "Added page to batch queue (${batchPages.size} total)."
                            },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = PaperMint),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Outlined.PlaylistAdd, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Add to Batch (${batchPages.size})", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    statusText = "Saving single scan page to Workspace PDF…"
                                    runCatching {
                                        PdfEngine.saveBitmapsToWorkspace(
                                            context = context,
                                            pages = listOf(
                                                processed.bitmap to (processed.mode == ScanFilterMode.ADAPTIVE_BW)
                                            ),
                                            displayName = "PaperPlane-Scan-${System.currentTimeMillis() % 10000}.pdf"
                                        )
                                    }.fold(
                                        onSuccess = { doc ->
                                            exportedDoc = doc
                                            onWorkspaceChanged()
                                            statusText = "Saved single-page GPU scan PDF."
                                        },
                                        onFailure = { err ->
                                            statusText = "PDF export failed: ${err.message ?: "Unknown error"}"
                                        }
                                    )
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Quick 1p PDF", maxLines = 1)
                        }
                    }
                }
            }
        }

        if (batchPages.isNotEmpty()) {
            item(key = "scanner_batch_queue") {
                GlassWorkspaceCard(elevated = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Multi-Page Batch (${batchPages.size})", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(
                            onClick = { batchPages = emptyList() },
                            enabled = !busy,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Clear Batch")
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(batchPages, key = { _, p -> p.id }) { idx, page ->
                            val thumb = remember(page.id) { page.bitmap.asImageBitmap() }
                            Box(
                                modifier = Modifier
                                    .size(width = 96.dp, height = 128.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0A0B10))
                                    .border(1.dp, PaperGlassBorder, RoundedCornerShape(10.dp))
                            ) {
                                Image(
                                    bitmap = thumb,
                                    contentDescription = "Batch page ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = {
                                        batchPages = batchPages.filterNot { it.id == page.id }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(36.dp)
                                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Remove page",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "P.${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                statusText = "Building ${batchPages.size}-page scan PDF…"
                                val payload = batchPages.map {
                                    it.bitmap to (it.mode == ScanFilterMode.ADAPTIVE_BW)
                                }
                                val name = "PaperPlane-Scan-${batchPages.size}p-${System.currentTimeMillis() % 10000}.pdf"
                                runCatching {
                                    PdfEngine.saveBitmapsToWorkspace(context, payload, name)
                                }.fold(
                                    onSuccess = { doc ->
                                        exportedDoc = doc
                                        onWorkspaceChanged()
                                        statusText = "Exported ${doc.pageCount}-page scan PDF to Workspace."
                                    },
                                    onFailure = { err ->
                                        statusText = "Batch export failed: ${err.message ?: "Unknown error"}"
                                    }
                                )
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (busy) "Exporting Batch…" else "Export ${batchPages.size}-Page PDF")
                    }
                }
            }
        }

        val doc = exportedDoc
        if (doc != null) {
            item(key = "scanner_exported_${doc.id}") {
                GlassWorkspaceCard(elevated = true) {
                    WorkspaceDocActionBar(
                        doc = doc,
                        onOpenViewer = onOpenViewer,
                        onStatusMessage = { statusText = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraXViewfinderBlock(
    onImageCaptureReady: (ImageCapture) -> Unit,
    onCameraError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var boundProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            runCatching {
                val provider = cameraProviderFuture.get()
                boundProvider = provider
                val selector = when {
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> error("No hardware camera available on this device.")
                }
                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }
                val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    previewView.display?.rotation ?: Surface.ROTATION_0
                } else {
                    @Suppress("DEPRECATION")
                    previewView.display?.rotation ?: Surface.ROTATION_0
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetRotation(rotation)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                onImageCaptureReady(capture)
            }.onFailure { err ->
                onCameraError("Camera unavailable: ${err.message ?: "Use Gallery Photo below"}")
            }
        }, executor)

        onDispose {
            runCatching { boundProvider?.unbindAll() }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
    )
}

@Composable
private fun QuadCornerDragEditor(
    bitmap: Bitmap,
    corners: QuadCorners,
    onCornersChanged: (QuadCorners) -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val currentCorners by rememberUpdatedState(corners)
    val latestOnChanged by rememberUpdatedState(onCornersChanged)
    var activeHandle by remember { mutableIntStateOf(-1) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A0B10))
            .border(1.dp, PaperGlassBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val scale = min(maxW / bitmap.width.coerceAtLeast(1), maxH / bitmap.height.coerceAtLeast(1))
        val fittedW = (bitmap.width * scale).coerceAtLeast(1f)
        val fittedH = (bitmap.height * scale).coerceAtLeast(1f)
        val widthDp = with(density) { fittedW.toDp() }
        val heightDp = with(density) { fittedH.toDp() }

        Box(
            modifier = Modifier
                .size(width = widthDp, height = heightDp)
                .pointerInput(bitmap.width, bitmap.height, fittedW, fittedH) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val nx = (start.x / fittedW).coerceIn(0f, 1f)
                            val ny = (start.y / fittedH).coerceIn(0f, 1f)
                            val list = currentCorners.asList()
                            var bestIdx = 0
                            var bestDist = Float.MAX_VALUE
                            list.forEachIndexed { idx, p ->
                                val d = hypot((p.x - nx).toDouble(), (p.y - ny).toDouble()).toFloat()
                                if (d < bestDist) {
                                    bestDist = d
                                    bestIdx = idx
                                }
                            }
                            activeHandle = bestIdx
                        },
                        onDragEnd = { activeHandle = -1 },
                        onDragCancel = { activeHandle = -1 },
                        onDrag = { change, _ ->
                            if (activeHandle !in 0..3) return@detectDragGestures
                            change.consume()
                            val nx = (change.position.x / fittedW).coerceIn(0.01f, 0.99f)
                            val ny = (change.position.y / fittedH).coerceIn(0.01f, 0.99f)
                            val updated = Point2D(nx, ny)
                            val c = currentCorners
                            val next = when (activeHandle) {
                                0 -> c.copy(topLeft = updated)
                                1 -> c.copy(topRight = updated)
                                2 -> c.copy(bottomRight = updated)
                                else -> c.copy(bottomLeft = updated)
                            }
                            latestOnChanged(next)
                        }
                    )
                }
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Source document corners",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pts = corners.asList().map { Offset(it.x * size.width, it.y * size.height) }
                val quadPath = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    lineTo(pts[1].x, pts[1].y)
                    lineTo(pts[2].x, pts[2].y)
                    lineTo(pts[3].x, pts[3].y)
                    close()
                }
                drawPath(path = quadPath, color = PaperMint, style = Stroke(width = 4f))
                pts.forEach { p ->
                    drawCircle(color = PaperViolet, radius = 16f, center = p)
                    drawCircle(color = Color.White, radius = 8f, center = p)
                }
            }
        }
    }
}

private fun defaultInsetQuad(): QuadCorners = QuadCorners(
    topLeft = Point2D(0.08f, 0.08f),
    topRight = Point2D(0.92f, 0.08f),
    bottomRight = Point2D(0.92f, 0.92f),
    bottomLeft = Point2D(0.08f, 0.92f)
)

private fun decodeImageProxyToBitmap(image: ImageProxy, maxDimension: Int): Bitmap? {
    val rotation = image.imageInfo.rotationDegrees
    val bytes = try {
        val buffer = image.planes.firstOrNull()?.buffer ?: return null
        ByteArray(buffer.remaining()).also { buffer.get(it) }
    } finally {
        image.close()
    }

    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
    var inSample = 1
    val largest = max(boundsOpts.outWidth, boundsOpts.outHeight)
    while (largest / inSample > maxDimension) {
        inSample *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = inSample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
    if (rotation == 0) return raw
    val m = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
}

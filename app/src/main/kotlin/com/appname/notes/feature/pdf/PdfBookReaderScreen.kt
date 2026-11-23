package com.appname.notes.feature.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appname.notes.core.common.result.getOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

enum class BookReadingTheme(val label: String, val backdrop: Color, val pageTint: Color, val inkColor: Color) {
    PAPER("Paper", Color(0xFFECE6DA), Color(0xFFFAF6EE), Color(0xFF1C1A17)),
    SEPIA("Sepia", Color(0xFFE2D3B8), Color(0xFFF3E6CE), Color(0xFF2B2114)),
    NIGHT("Night", Color(0xFF0A0B0E), Color(0xFF15171C), Color(0xFFEAECEF)),
}

/**
 * Thread-safe Android `PdfRenderer` wrapper with in-memory LRU page caching for 120Hz book swiping.
 */
class BookPdfController private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val sourceKey: String,
) {
    val pageCount: Int = renderer.pageCount
    private val renderMutex = Mutex()
    private val pageCache = object : android.util.LruCache<Int, Bitmap>(6) {}

    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (index !in 0 until pageCount) return@withContext null
        pageCache.get(index)?.takeIf { !it.isRecycled }?.let { return@withContext it }

        renderMutex.withLock {
            pageCache.get(index)?.takeIf { !it.isRecycled }?.let { return@withLock it }
            runCatching {
                renderer.openPage(index).use { page ->
                    val safeWidth = targetWidthPx.coerceIn(720, 1800)
                    val scale = safeWidth.toFloat() / page.width.coerceAtLeast(1).toFloat()
                    val targetHeight = (page.height * scale).toInt().coerceIn(720, 2600)
                    val bitmap = Bitmap.createBitmap(safeWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pageCache.put(index, bitmap)
                    bitmap
                }
            }.getOrNull()
        }
    }

    fun close() {
        runCatching { pageCache.evictAll() }
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    companion object {
        fun openFile(file: File): BookPdfController? = runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            BookPdfController(pfd, PdfRenderer(pfd), file.absolutePath)
        }.getOrNull()

        fun openUri(context: Context, uri: Uri): BookPdfController? = runCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            BookPdfController(pfd, PdfRenderer(pfd), uri.toString())
        }.getOrNull()

        /**
         * Resolves the stored PDF file for a note by querying Room's PdfRepository & AttachmentRepository
         * (since imported PDFs are stored under UUID filenames in `files/pdfs/`), falling back to a local scan.
         */
        suspend fun resolvePdfFileForNote(context: Context, noteId: String, noteTitle: String): File? =
            withContext(Dispatchers.IO) {
                val ep = runCatching {
                    dagger.hilt.android.EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        com.appname.notes.di.AppDataEntryPoint::class.java,
                    )
                }.getOrNull()

                if (ep != null) {
                    val id = com.appname.notes.core.domain.model.NoteId(noteId)
                    // 1. Primary: Imported PDF note stored via PdfRepository + PdfFileStore
                    val doc = runCatching { ep.pdf().document(id).getOrNull() }.getOrNull()
                    if (doc != null) {
                        val stored = ep.pdfFiles().file(doc.fileName)
                        if (stored.exists() && stored.length() > 0L) return@withContext stored
                    }
                    // 2. Secondary: PDF attachment stored in files/attachments
                    val attachments = runCatching { ep.attachments().list(id).getOrNull() }.getOrNull().orEmpty()
                    val pdfAttachment = attachments.firstOrNull {
                        it.kind == com.appname.notes.core.domain.model.AttachmentKind.PDF ||
                            it.fileName.endsWith(".pdf", ignoreCase = true)
                    }
                    if (pdfAttachment != null) {
                        val attachFile = File(File(context.filesDir, "attachments"), pdfAttachment.fileName)
                        if (attachFile.exists() && attachFile.length() > 0L) return@withContext attachFile
                    }
                }

                // 3. Fallback: Scan local app storage by noteId or title
                findLocalPdfForNote(context, noteId, noteTitle)
            }

        /**
         * Attempts to locate an imported PDF file matching a note ID or title inside app-private storage.
         */
        fun findLocalPdfForNote(context: Context, noteId: String, noteTitle: String): File? {
            val dirs = listOfNotNull(context.filesDir, context.cacheDir)
            val cleanTitle = noteTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
            var fallbackMatch: File? = null
            for (dir in dirs) {
                dir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
                    .forEach { file ->
                        val name = file.name.lowercase()
                        if (name.contains(noteId.lowercase())) return file
                        val cleanName = file.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9]"), "")
                        if (cleanTitle.length >= 4 && cleanName.contains(cleanTitle.take(12))) {
                            fallbackMatch = file
                        }
                    }
            }
            return fallbackMatch
        }
    }
}

private val NIGHT_INVERT_MATRIX = ColorMatrix(
    floatArrayOf(
        -0.90f, 0f, 0f, 0f, 235f,
        0f, -0.90f, 0f, 0f, 235f,
        0f, 0f, -0.88f, 0f, 232f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

private val SEPIA_TINT_MATRIX = ColorMatrix(
    floatArrayOf(
        0.96f, 0f, 0f, 0f, 4f,
        0f, 0.91f, 0f, 0f, 0f,
        0f, 0f, 0.80f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/**
 * Full-Screen 3D Book Mode PDF Reader Dialog (`PdfBookReaderDialog`).
 * Features realistic 3D book page-turn rotation, spine lighting shadow, Paper/Sepia/Night themes,
 * pinch-to-zoom, tap-to-hide distraction-free HUD, and smooth page scrubbing.
 */
@Composable
fun PdfBookReaderDialog(
    bookTitle: String,
    initialFile: File? = null,
    initialUri: Uri? = null,
    noteId: String? = null,
    initialPage: Int = 0,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember(initialUri) { mutableStateOf(initialUri) }
    var selectedFile by remember(initialFile) { mutableStateOf(initialFile) }
    var resolvingPdf by remember(noteId, initialFile, initialUri) {
        mutableStateOf(initialFile == null && initialUri == null && !noteId.isNullOrBlank())
    }
    var readingTheme by remember { mutableStateOf(BookReadingTheme.PAPER) }
    var hudVisible by remember { mutableStateOf(true) }
    var controller by remember { mutableStateOf<BookPdfController?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(noteId, initialFile, initialUri) {
        if (initialFile != null) {
            selectedFile = initialFile
            resolvingPdf = false
            return@LaunchedEffect
        }
        if (initialUri != null) {
            selectedUri = initialUri
            resolvingPdf = false
            return@LaunchedEffect
        }
        if (!noteId.isNullOrBlank()) {
            resolvingPdf = true
            val found = BookPdfController.resolvePdfFileForNote(context, noteId, bookTitle)
            if (found != null) {
                selectedFile = found
                loadError = null
            }
            resolvingPdf = false
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFile = null
            loadError = null
        }
    }

    DisposableEffect(selectedFile, selectedUri) {
        val opened = when {
            selectedFile != null -> BookPdfController.openFile(selectedFile!!)
            selectedUri != null -> BookPdfController.openUri(context, selectedUri!!)
            else -> null
        }
        controller = opened
        if ((selectedFile != null || selectedUri != null) && opened == null) {
            loadError = "Unable to open this PDF file. Please pick a valid PDF document."
        }
        onDispose {
            opened?.close()
            if (controller === opened) controller = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(readingTheme.backdrop),
        ) {
            val activeController = controller
            if (resolvingPdf) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        color = readingTheme.inkColor,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        text = "Opening in Book Mode…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = readingTheme.inkColor.copy(alpha = 0.78f),
                    )
                }
            } else if (activeController == null || activeController.pageCount <= 0) {
                // Clean Book Picker / Empty State when no local PDF file is attached yet
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = readingTheme.inkColor.copy(alpha = 0.08f),
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.AutoStories,
                                contentDescription = null,
                                tint = readingTheme.inkColor,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                    Text(
                        text = bookTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = readingTheme.inkColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = loadError ?: "Select a PDF file from your device to read it in 3D Book Mode with page-turn animations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = readingTheme.inkColor.copy(alpha = 0.72f),
                    )
                    Button(
                        onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E3F32),
                            contentColor = Color(0xFFFAF7F2),
                        ),
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open PDF in Book Mode")
                    }
                }
            } else {
                val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, (activeController.pageCount - 1).coerceAtLeast(0)), pageCount = { activeController.pageCount })
                var zoomScale by remember { mutableFloatStateOf(1f) }
                var zoomOffset by remember { mutableStateOf(Offset.Zero) }

                // Reset zoom whenever page changes
                LaunchedEffect(pagerState.currentPage) {
                    zoomScale = 1f
                    zoomOffset = Offset.Zero
                }

                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = zoomScale <= 1.02f,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { tapPos ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    when {
                                        zoomScale <= 1.02f && tapPos.x < w * 0.22f && pagerState.currentPage > 0 -> {
                                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                        }
                                        zoomScale <= 1.02f && tapPos.x > w * 0.78f && pagerState.currentPage < activeController.pageCount - 1 -> {
                                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                        }
                                        else -> hudVisible = !hudVisible
                                    }
                                },
                                onDoubleTap = {
                                    if (zoomScale > 1.05f) {
                                        zoomScale = 1f
                                        zoomOffset = Offset.Zero
                                    } else {
                                        zoomScale = 2.0f
                                    }
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (zoomScale * zoom).coerceIn(1f, 3.2f)
                                zoomScale = newScale
                                zoomOffset = if (newScale > 1.02f) {
                                    Offset(
                                        x = (zoomOffset.x + pan.x).coerceIn(-600f, 600f),
                                        y = (zoomOffset.y + pan.y).coerceIn(-900f, 900f),
                                    )
                                } else {
                                    Offset.Zero
                                }
                            }
                        },
                ) { pageIndex ->
                    val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                    val clampedOffset = pageOffset.coerceIn(-1f, 1f)
                    val absOffset = abs(clampedOffset)

                    var pageBitmap by remember(activeController, pageIndex) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(activeController, pageIndex) {
                        pageBitmap = activeController.renderPage(pageIndex, targetWidthPx = 1240)
                    }

                    // 3D Realistic Book Page-Turn Transform + Dynamic Spine Lighting Shadow
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 14.dp)
                            .graphicsLayer {
                                cameraDistance = 16f * density
                                // Hinge along the left book spine as the page flips
                                transformOrigin = if (clampedOffset >= 0f) {
                                    TransformOrigin(0f, 0.5f)
                                } else {
                                    TransformOrigin(1f, 0.5f)
                                }
                                rotationY = -clampedOffset * 48f
                                val depthScale = (1f - absOffset * 0.06f).coerceIn(0.92f, 1f)
                                scaleX = depthScale * zoomScale
                                scaleY = depthScale * zoomScale
                                translationX = zoomOffset.x
                                translationY = zoomOffset.y
                                alpha = (1f - absOffset * 0.28f).coerceIn(0.65f, 1f)
                                shadowElevation = (14.dp.toPx() * (1f - absOffset * 0.5f)).coerceAtLeast(2f)
                            }
                            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp, topStart = 4.dp, bottomStart = 4.dp))
                            .background(readingTheme.pageTint)
                            .drawWithContent {
                                drawContent()
                                // Subtle book-spine crease shadow on the left edge
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0.0f to Color.Black.copy(alpha = 0.16f),
                                        0.045f to Color.Black.copy(alpha = 0.05f),
                                        0.10f to Color.Transparent,
                                    ),
                                )
                                // Dynamic page-turn lighting shadow proportional to turn angle
                                if (absOffset > 0.01f) {
                                    drawRect(color = Color.Black.copy(alpha = (absOffset * 0.24f).coerceIn(0f, 0.26f)))
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = pageBitmap
                        if (bmp != null && !bmp.isRecycled) {
                            val filter = when (readingTheme) {
                                BookReadingTheme.PAPER -> null
                                BookReadingTheme.SEPIA -> ColorFilter.colorMatrix(SEPIA_TINT_MATRIX)
                                BookReadingTheme.NIGHT -> ColorFilter.colorMatrix(NIGHT_INVERT_MATRIX)
                            }
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Page ${pageIndex + 1}",
                                contentScale = ContentScale.Fit,
                                colorFilter = filter,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp),
                            )
                        } else {
                            CircularProgressIndicator(
                                color = readingTheme.inkColor.copy(alpha = 0.5f),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        // Subtle bottom book page folio number
                        Text(
                            text = "${pageIndex + 1}",
                            fontSize = 11.sp,
                            color = readingTheme.inkColor.copy(alpha = 0.45f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp),
                        )
                    }
                }

                // Bottom Page Scrubber & Theme Switcher HUD
                AnimatedVisibility(
                    visible = hudVisible,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = if (readingTheme == BookReadingTheme.NIGHT) Color(0xEE181A20) else Color(0xF0FAF7F2),
                        border = BorderStroke(
                            0.8.dp,
                            if (readingTheme == BookReadingTheme.NIGHT) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f),
                        ),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                IconButton(
                                    onClick = {
                                        if (pagerState.currentPage > 0) {
                                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                        }
                                    },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Previous page",
                                        tint = readingTheme.inkColor,
                                    )
                                }
                                Text(
                                    text = "Page ${pagerState.currentPage + 1} of ${activeController.pageCount}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = readingTheme.inkColor,
                                )
                                IconButton(
                                    onClick = {
                                        if (pagerState.currentPage < activeController.pageCount - 1) {
                                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                        }
                                    },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Next page",
                                        tint = readingTheme.inkColor,
                                    )
                                }
                            }
                            if (activeController.pageCount > 1) {
                                Slider(
                                    value = pagerState.currentPage.toFloat(),
                                    onValueChange = { target ->
                                        val idx = target.toInt().coerceIn(0, activeController.pageCount - 1)
                                        scope.launch { pagerState.scrollToPage(idx) }
                                    },
                                    valueRange = 0f..(activeController.pageCount - 1).toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF1E3F32),
                                        activeTrackColor = Color(0xFF1E3F32),
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(28.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            ) {
                                BookReadingTheme.entries.forEach { theme ->
                                    val selected = theme == readingTheme
                                    Surface(
                                        shape = CircleShape,
                                        color = theme.pageTint,
                                        border = BorderStroke(
                                            if (selected) 1.6.dp else 0.8.dp,
                                            if (selected) Color(0xFF1E3F32) else readingTheme.inkColor.copy(alpha = 0.22f),
                                        ),
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) { readingTheme = theme },
                                    ) {
                                        Text(
                                            text = theme.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = theme.inkColor,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Top Floating Reader Header (always accessible when HUD is visible or no PDF loaded)
            AnimatedVisibility(
                visible = hudVisible || controller == null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (readingTheme == BookReadingTheme.NIGHT) Color(0xEE181A20) else Color(0xF0FAF7F2),
                    border = BorderStroke(
                        0.8.dp,
                        if (readingTheme == BookReadingTheme.NIGHT) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f),
                    ),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Book Reader", tint = readingTheme.inkColor)
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            Text(
                                text = bookTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = readingTheme.inkColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Book Mode",
                                style = MaterialTheme.typography.labelSmall,
                                color = readingTheme.inkColor.copy(alpha = 0.62f),
                            )
                        }
                        IconButton(
                            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = "Switch PDF", tint = readingTheme.inkColor)
                        }
                    }
                }
            }
        }
    }
}

package ru.warehouse.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI state for a file shown in the auto-receive queue.
 *
 * This is a presentation type for this screen, not a persisted/domain model.
 */
data class AutoReceiveFileUiState(
    val id: String,
    val name: String,
    val status: AutoReceiveFileStatus,
    val statusMessage: String? = null,
    val details: String? = null,
    val canOpenResult: Boolean = false,
)

enum class AutoReceiveFileStatus {
    Waiting,
    Processing,
    Success,
    Error,
    WaitingNetwork,
}

/**
 * UI for the incoming-file queue. Folder access, rescanning, background
 * processing, retries, and opening saved results are owned by the host app.
 */
@Composable
fun AutoReceiveScreen(
    files: List<AutoReceiveFileUiState>,
    folderName: String?,
    isFolderConnected: Boolean,
    onChooseFolder: () -> Unit,
    onRefresh: () -> Unit,
    onFileAction: (AutoReceiveFileUiState) -> Unit,
    modifier: Modifier = Modifier,
    isSelectingFolder: Boolean = false,
    isRefreshing: Boolean = false,
    tokenUsageProgress: Float? = null,
    globalNotice: String? = null,
    globalNoticeIsError: Boolean = false,
    statusMessage: String? = null,
    statusMessageIsError: Boolean = false,
) {
    val colors = autoReceiveColors(isSystemInDarkTheme())
    val waitingCount = files.count {
        it.status == AutoReceiveFileStatus.Waiting ||
            it.status == AutoReceiveFileStatus.WaitingNetwork
    }
    val processingCount = files.count { it.status == AutoReceiveFileStatus.Processing }
    val successCount = files.count { it.status == AutoReceiveFileStatus.Success }
    val errorCount = files.count { it.status == AutoReceiveFileStatus.Error }

    var selectedFilter by remember { mutableStateOf(AutoReceiveFilter.All) }
    LaunchedEffect(errorCount) {
        if (errorCount == 0 && selectedFilter == AutoReceiveFilter.Error) {
            selectedFilter = AutoReceiveFilter.All
        }
    }
    val visibleFiles = when (selectedFilter) {
        AutoReceiveFilter.All -> files
        AutoReceiveFilter.Waiting -> files.filter {
            it.status == AutoReceiveFileStatus.Waiting ||
                it.status == AutoReceiveFileStatus.WaitingNetwork
        }
        AutoReceiveFilter.Processing ->
            files.filter { it.status == AutoReceiveFileStatus.Processing }
        AutoReceiveFilter.Success ->
            files.filter { it.status == AutoReceiveFileStatus.Success }
        AutoReceiveFilter.Error ->
            files.filter { it.status == AutoReceiveFileStatus.Error }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        AutoReceiveHeader(
            folderName = folderName,
            colors = colors,
            isRefreshing = isRefreshing,
            isSelectingFolder = isSelectingFolder,
            onChooseFolder = onChooseFolder,
            onRefresh = onRefresh,
        )

        if (tokenUsageProgress != null) {
            TokenUsageBar(progress = tokenUsageProgress, colors = colors)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutoReceiveFilterChip(
                label = "Ожидают",
                count = waitingCount,
                dotColor = colors.waiting,
                selected = selectedFilter == AutoReceiveFilter.Waiting,
                colors = colors,
                onClick = {
                    selectedFilter = if (selectedFilter == AutoReceiveFilter.Waiting) {
                        AutoReceiveFilter.All
                    } else {
                        AutoReceiveFilter.Waiting
                    }
                },
            )
            AutoReceiveFilterChip(
                label = "Обработка",
                count = processingCount,
                dotColor = colors.processing,
                selected = selectedFilter == AutoReceiveFilter.Processing,
                colors = colors,
                onClick = {
                    selectedFilter = if (selectedFilter == AutoReceiveFilter.Processing) {
                        AutoReceiveFilter.All
                    } else {
                        AutoReceiveFilter.Processing
                    }
                },
            )
            AutoReceiveFilterChip(
                label = "Готово",
                count = successCount,
                dotColor = colors.success,
                selected = selectedFilter == AutoReceiveFilter.Success,
                colors = colors,
                onClick = {
                    selectedFilter = if (selectedFilter == AutoReceiveFilter.Success) {
                        AutoReceiveFilter.All
                    } else {
                        AutoReceiveFilter.Success
                    }
                },
            )
            if (errorCount > 0) {
                AutoReceiveFilterChip(
                    label = "Ошибки",
                    count = errorCount,
                    dotColor = colors.error,
                    selected = selectedFilter == AutoReceiveFilter.Error,
                    colors = colors,
                    onClick = {
                        selectedFilter = if (selectedFilter == AutoReceiveFilter.Error) {
                            AutoReceiveFilter.All
                        } else {
                            AutoReceiveFilter.Error
                        }
                    },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 2.dp,
                end = 12.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!isFolderConnected) {
                item(key = "choose-folder") {
                    ChooseFolderCard(
                        isSelectingFolder = isSelectingFolder,
                        colors = colors,
                        onClick = onChooseFolder,
                    )
                }
            }

            if (!globalNotice.isNullOrBlank()) {
                item(key = "global-notice") {
                    AutoReceiveNotice(
                        message = globalNotice,
                        isError = globalNoticeIsError,
                        colors = colors,
                    )
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                item(key = "status-message") {
                    AutoReceiveNotice(
                        message = statusMessage,
                        isError = statusMessageIsError,
                        colors = colors,
                    )
                }
            }

            if (files.isEmpty()) {
                item(key = "empty-files") {
                    EmptyQueueCard(
                        hasFolder = isFolderConnected,
                        colors = colors,
                    )
                }
            } else if (visibleFiles.isEmpty()) {
                item(key = "empty-filter") {
                    Text(
                        text = "Нет файлов с таким статусом.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        color = colors.muted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                itemsIndexed(
                    items = visibleFiles,
                    key = { index, file ->
                        file.id.ifBlank { "auto-receive-$index-${file.name}" }
                    },
                ) { _, file ->
                    AutoReceiveFileCard(
                        file = file,
                        colors = colors,
                        onClick = { onFileAction(file) },
                    )
                }
            }
        }
    }
}

private enum class AutoReceiveFilter {
    All,
    Waiting,
    Processing,
    Success,
    Error,
}

@Composable
private fun AutoReceiveHeader(
    folderName: String?,
    colors: AutoReceiveColors,
    isRefreshing: Boolean,
    isSelectingFolder: Boolean,
    onChooseFolder: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0F1F38), Color(0xFF1A2F4A)),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .heightIn(min = 62.dp)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Автоприём",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Surface(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = !isSelectingFolder,
                        role = Role.Button,
                        onClick = onChooseFolder,
                    ),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f),
            ) {
                Text(
                    text = folderName
                        ?.takeIf { it.isNotBlank() }
                        ?: "Папка не выбрана",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        HeaderAction(
            symbol = if (isSelectingFolder) "…" else "📁",
            description = "Выбрать папку",
            enabled = !isSelectingFolder,
            onClick = onChooseFolder,
        )
        HeaderAction(
            symbol = if (isRefreshing) "…" else "↻",
            description = "Обновить список файлов",
            enabled = !isRefreshing,
            onClick = onRefresh,
        )
    }
}

@Composable
private fun HeaderAction(
    symbol: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = description }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = if (enabled) 0.14f else 0.07f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.65f),
                fontSize = if (symbol == "↻") 25.sp else 18.sp,
            )
        }
    }
}

@Composable
private fun TokenUsageBar(
    progress: Float,
    colors: AutoReceiveColors,
) {
    val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val progressColor = when {
        safeProgress >= 1f -> colors.error
        safeProgress >= 0.8f -> colors.processing
        else -> colors.primary
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(colors.tokenTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(safeProgress)
                .height(3.dp)
                .background(progressColor),
        )
    }
}

@Composable
private fun AutoReceiveFilterChip(
    label: String,
    count: Int,
    dotColor: Color,
    selected: Boolean,
    colors: AutoReceiveColors,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        shape = CircleShape,
        color = if (selected) colors.primary else colors.chip,
        border = if (selected) null else BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(if (selected) Color.White else dotColor, CircleShape),
            )
            Text(
                text = label,
                color = if (selected) Color.White else colors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = count.toString(),
                color = if (selected) Color.White else colors.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun ChooseFolderCard(
    isSelectingFolder: Boolean,
    colors: AutoReceiveColors,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = !isSelectingFolder,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(16.dp),
        color = colors.folderCard,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(11.dp),
                color = colors.primary.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("📁", fontSize = 19.sp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSelectingFolder) "Выбираем папку…" else "Выбрать папку",
                    color = colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Папка для входящих PDF и изображений",
                    color = colors.muted,
                    fontSize = 11.sp,
                )
            }
            Text("›", color = colors.primary, fontSize = 24.sp)
        }
    }
}

@Composable
private fun AutoReceiveNotice(
    message: String,
    isError: Boolean,
    colors: AutoReceiveColors,
) {
    val foreground = if (isError) colors.error else colors.noticeText
    val background = if (isError) colors.errorBackground else colors.noticeBackground
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isError) "!" else "ℹ",
                color = foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = message,
                color = foreground,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun EmptyQueueCard(
    hasFolder: Boolean,
    colors: AutoReceiveColors,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("⚡", fontSize = 28.sp)
        Text(
            text = if (hasFolder) {
                "В выбранной папке пока нет файлов."
            } else {
                "Выберите папку, чтобы увидеть её содержимое."
            },
            color = colors.muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AutoReceiveFileCard(
    file: AutoReceiveFileUiState,
    colors: AutoReceiveColors,
    onClick: () -> Unit,
) {
    val statusColor = file.status.statusColor(colors)
    val statusLabel = file.statusMessage
        ?.takeIf { it.isNotBlank() }
        ?: file.status.defaultLabel()
    val clickable = file.status == AutoReceiveFileStatus.Error ||
        (file.status == AutoReceiveFileStatus.Success && file.canOpenResult)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (clickable) {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(10.dp),
                color = colors.fileIconBackground,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = autoReceiveFileIcon(file.name),
                        fontSize = 18.sp,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = file.name.ifBlank { "Без названия" },
                    color = colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!file.details.isNullOrBlank()) {
                    Text(
                        text = file.details,
                        color = colors.muted,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FileStatusIndicator(status = file.status, colors = colors)
        }
    }
}

@Composable
private fun FileStatusIndicator(
    status: AutoReceiveFileStatus,
    colors: AutoReceiveColors,
) {
    val background = when (status) {
        AutoReceiveFileStatus.Waiting -> colors.waitingBackground
        AutoReceiveFileStatus.WaitingNetwork -> colors.errorBackground
        AutoReceiveFileStatus.Processing -> colors.processingBackground
        AutoReceiveFileStatus.Success -> colors.successBackground
        AutoReceiveFileStatus.Error -> colors.errorBackground
    }
    val foreground = when (status) {
        AutoReceiveFileStatus.Waiting -> colors.waiting
        AutoReceiveFileStatus.WaitingNetwork, AutoReceiveFileStatus.Error -> colors.error
        AutoReceiveFileStatus.Processing -> colors.processing
        AutoReceiveFileStatus.Success -> colors.success
    }
    val symbol = when (status) {
        AutoReceiveFileStatus.Waiting -> "·"
        AutoReceiveFileStatus.WaitingNetwork -> "!"
        AutoReceiveFileStatus.Processing -> "…"
        AutoReceiveFileStatus.Success -> "✓"
        AutoReceiveFileStatus.Error -> "!"
    }
    Surface(
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = background,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                color = foreground,
                fontSize = if (status == AutoReceiveFileStatus.Waiting) 23.sp else 16.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 20.sp,
            )
        }
    }
}

private fun AutoReceiveFileStatus.defaultLabel(): String = when (this) {
    AutoReceiveFileStatus.Waiting -> "Ожидает обработки"
    AutoReceiveFileStatus.WaitingNetwork -> "Ожидает подключения к сети"
    AutoReceiveFileStatus.Processing -> "Обрабатывается"
    AutoReceiveFileStatus.Success -> "Готово"
    AutoReceiveFileStatus.Error -> "Ошибка · нажмите, чтобы повторить"
}

private fun AutoReceiveFileStatus.statusColor(colors: AutoReceiveColors): Color = when (this) {
    AutoReceiveFileStatus.Waiting -> colors.waiting
    AutoReceiveFileStatus.WaitingNetwork, AutoReceiveFileStatus.Error -> colors.error
    AutoReceiveFileStatus.Processing -> colors.processing
    AutoReceiveFileStatus.Success -> colors.success
}

private fun autoReceiveFileIcon(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "📄"
        "xlsx", "xls", "csv" -> "📊"
        "jpg", "jpeg", "png", "webp", "gif" -> "🖼️"
        "zip", "rar", "7z" -> "🗜️"
        else -> "📎"
    }
}

private data class AutoReceiveColors(
    val background: Color,
    val card: Color,
    val folderCard: Color,
    val chip: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val tokenTrack: Color,
    val fileIconBackground: Color,
    val noticeBackground: Color,
    val noticeText: Color,
    val errorBackground: Color,
    val error: Color,
    val waitingBackground: Color,
    val waiting: Color,
    val processingBackground: Color,
    val processing: Color,
    val successBackground: Color,
    val success: Color,
)

private fun autoReceiveColors(darkTheme: Boolean) = if (darkTheme) {
    AutoReceiveColors(
        background = Color(0xFF101318),
        card = Color(0xFF171A1F),
        folderCard = Color(0xFF171A1F),
        chip = Color(0xFF20252D),
        border = Color(0xFF363C46),
        text = Color(0xFFE8EAED),
        muted = Color(0xFF9AA3AF),
        primary = Color(0xFF8AB4F8),
        tokenTrack = Color(0x332B8AE0),
        fileIconBackground = Color(0xFF242A33),
        noticeBackground = Color(0xFF202B38),
        noticeText = Color(0xFFBBD2F4),
        errorBackground = Color(0xFF3B2428),
        error = Color(0xFFFF8A80),
        waitingBackground = Color(0xFF3A3220),
        waiting = Color(0xFFFFD166),
        processingBackground = Color(0xFF26354A),
        processing = Color(0xFF8AB4F8),
        successBackground = Color(0xFF21372A),
        success = Color(0xFF8FD19E),
    )
} else {
    AutoReceiveColors(
        background = Color(0xFFF6F8FC),
        card = Color.White,
        folderCard = Color.White,
        chip = Color.White,
        border = Color(0xFFC9CFDB),
        text = Color(0xFF171B22),
        muted = Color(0xFF414853),
        primary = Color(0xFF2B5CB0),
        tokenTrack = Color(0x1F2B7FD6),
        fileIconBackground = Color(0xFFF1F3F5),
        noticeBackground = Color(0xFFE7F5FF),
        noticeText = Color(0xFF28527A),
        errorBackground = Color(0xFFFFE8E6),
        error = Color(0xFFC92A2A),
        waitingBackground = Color(0xFFFFF3CD),
        waiting = Color(0xFF9A6B12),
        processingBackground = Color(0xFFFFE8CC),
        processing = Color(0xFFE67700),
        successBackground = Color(0xFFE6F4EA),
        success = Color(0xFF2B8A3E),
    )
}

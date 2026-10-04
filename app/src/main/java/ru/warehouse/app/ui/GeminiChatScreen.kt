package ru.warehouse.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.warehouse.app.data.ChatMessage
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI-only file information. The host owns the underlying Android Uri/File data
 * and launches the system picker.
 */
data class GeminiChatAttachmentUiState(
    val id: String,
    val name: String,
    val sizeBytes: Long = 0L,
    val mimeType: String = "",
)

/**
 * Display values for the daily token meter. Usage calculation and persistence
 * remain in the host app.
 */
data class GeminiTokenUsageUiState(
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val totalTokens: Long = 0L,
    val dailyLimit: Long = 200_000L,
)

/**
 * Gemini warehouse chat. Gemini requests, file picking, history persistence,
 * and warehouse context are all handled by host callbacks.
 */
@Composable
fun GeminiChatScreen(
    messages: List<ChatMessage>,
    draftText: String,
    pendingAttachments: List<GeminiChatAttachmentUiState>,
    onDraftTextChange: (String) -> Unit,
    onAddAttachments: () -> Unit,
    onRemovePendingAttachment: (attachmentId: String) -> Unit,
    onSendMessage: (text: String, attachments: List<GeminiChatAttachmentUiState>) -> Unit,
    onBack: () -> Unit,
    onRetryMessage: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
    messageAttachments: Map<String, List<GeminiChatAttachmentUiState>> = emptyMap(),
    tokenUsage: GeminiTokenUsageUiState = GeminiTokenUsageUiState(),
    statusMessage: String? = null,
    statusIsError: Boolean = false,
    isSending: Boolean = false,
    streamingResponseText: String? = null,
    retryMessageId: String? = null,
) {
    val colors = geminiChatColors(isSystemInDarkTheme())
    val listState = rememberLazyListState()
    val canSend = !isSending &&
        (draftText.isNotBlank() || pendingAttachments.isNotEmpty())
    val formattedInputTokens = formatGeminiTokenCount(tokenUsage.inputTokens)
    val formattedOutputTokens = formatGeminiTokenCount(tokenUsage.outputTokens)
    val formattedTotalTokens = formatGeminiTokenCount(tokenUsage.totalTokens)
    val safeLimit = tokenUsage.dailyLimit.coerceAtLeast(0L)
    val progress = if (safeLimit > 0L) {
        (tokenUsage.totalTokens.toFloat() / safeLimit.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    LaunchedEffect(messages.lastOrNull()?.id, isSending, streamingResponseText) {
        val visibleItemCount = messages.size + (if (isSending) 1 else 0)
        if (visibleItemCount > 0) {
            listState.animateScrollToItem(visibleItemCount - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        GeminiChatHeader(
            colors = colors,
            onBack = onBack,
        )

        GeminiTokenUsageBar(
            usage = tokenUsage,
            inputTokens = formattedInputTokens,
            outputTokens = formattedOutputTokens,
            totalTokens = formattedTotalTokens,
            progress = progress,
            colors = colors,
        )

        if (!statusMessage.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (statusIsError) colors.errorStatus else colors.notice,
            ) {
                Text(
                    text = statusMessage,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    color = if (statusIsError) colors.error else colors.noticeText,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(
                horizontal = 14.dp,
                vertical = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (messages.isEmpty() && !isSending) {
                item(key = "gemini-empty") {
                    GeminiChatEmptyState(colors = colors)
                }
            }

            items(
                items = messages,
                key = { message -> message.id },
            ) { message ->
                GeminiMessageBubble(
                    message = message,
                    attachments = messageAttachments[message.id].orEmpty(),
                    canRetry = message.id == retryMessageId && !isSending,
                    colors = colors,
                    onRetry = { onRetryMessage(message) },
                )
            }

            if (isSending) {
                item(key = "gemini-typing") {
                    GeminiAssistantTyping(
                        text = streamingResponseText.orEmpty(),
                        colors = colors,
                    )
                }
            }
        }

        GeminiChatComposer(
            draftText = draftText,
            pendingAttachments = pendingAttachments,
            canSend = canSend,
            isSending = isSending,
            colors = colors,
            onDraftTextChange = onDraftTextChange,
            onAddAttachments = onAddAttachments,
            onRemoveAttachment = onRemovePendingAttachment,
            onSend = {
                if (canSend) {
                    onSendMessage(draftText.trim(), pendingAttachments)
                }
            },
        )
    }
}

@Composable
private fun GeminiChatHeader(
    colors: GeminiChatColors,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .semantics { contentDescription = "Назад" }
                .clickable(role = Role.Button, onClick = onBack),
            shape = CircleShape,
            color = colors.control,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("‹", color = colors.text, fontSize = 32.sp, lineHeight = 32.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = "Чат с Gemini",
                color = colors.text,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Помощник склада",
                color = colors.muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun GeminiTokenUsageBar(
    usage: GeminiTokenUsageUiState,
    inputTokens: String,
    outputTokens: String,
    totalTokens: String,
    progress: Float,
    colors: GeminiChatColors,
) {
    val progressColor = when {
        progress >= 1f -> colors.error
        progress >= 0.75f -> colors.warning
        else -> colors.primary
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(colors.progressTrack),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(progressColor),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.usageBackground)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Расход токенов",
                color = colors.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            TokenCounter(label = "Вход", value = inputTokens, colors = colors)
            TokenCounter(label = "Выход", value = outputTokens, colors = colors)
            TokenCounter(label = "Всего", value = totalTokens, colors = colors)
            Spacer(modifier = Modifier.weight(1f))
            if (usage.dailyLimit > 0L) {
                Text(
                    text = formatGeminiTokenCount(usage.dailyLimit),
                    color = colors.muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TokenCounter(
    label: String,
    value: String,
    colors: GeminiChatColors,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "$label:", color = colors.muted, fontSize = 10.sp)
        Text(text = value, color = colors.success, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GeminiChatEmptyState(colors: GeminiChatColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 250.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 330.dp).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("✦", color = colors.primary, fontSize = 38.sp)
            Text(
                text = "Напишите вопрос Gemini или прикрепите документ для анализа.",
                color = colors.muted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GeminiMessageBubble(
    message: ChatMessage,
    attachments: List<GeminiChatAttachmentUiState>,
    canRetry: Boolean,
    colors: GeminiChatColors,
    onRetry: () -> Unit,
) {
    val fromUser = message.isFromUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (message.text.isNotBlank()) {
                val bubbleShape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomEnd = if (fromUser) 5.dp else 16.dp,
                    bottomStart = if (fromUser) 16.dp else 5.dp,
                )
                if (fromUser) {
                    Text(
                        text = message.text,
                        modifier = Modifier
                            .clip(bubbleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1563B8), Color(0xFF1C7ED6)),
                                ),
                            )
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                } else {
                    Surface(
                        shape = bubbleShape,
                        color = colors.assistantBubble,
                        border = BorderStroke(1.dp, colors.border),
                        shadowElevation = 1.dp,
                    ) {
                        Text(
                            text = message.text,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                            color = colors.text,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                        )
                    }
                }
            }

            if (attachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                ) {
                    items(
                        items = attachments,
                        key = { attachment -> attachment.id },
                    ) { attachment ->
                        GeminiAttachmentCard(
                            attachment = attachment,
                            colors = colors,
                        )
                    }
                }
            }

            Text(
                text = formatGeminiChatTime(message.timestamp),
                modifier = Modifier.padding(horizontal = 4.dp),
                color = colors.muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )

            if (canRetry) {
                TextButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "Повторить отправку",
                        color = colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun GeminiAssistantTyping(
    text: String,
    colors: GeminiChatColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomEnd = 16.dp,
                bottomStart = 5.dp,
            ),
            color = colors.assistantBubble,
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = 1.dp,
        ) {
            Text(
                text = text.ifBlank { "•••" },
                modifier = Modifier
                    .widthIn(max = 330.dp)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                color = if (text.isBlank()) colors.muted else colors.text,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun GeminiAttachmentCard(
    attachment: GeminiChatAttachmentUiState,
    colors: GeminiChatColors,
    onRemove: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.widthIn(max = 230.dp),
        shape = RoundedCornerShape(10.dp),
        color = colors.attachmentBackground,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(
                start = 9.dp,
                top = 7.dp,
                end = if (onRemove != null) 4.dp else 9.dp,
                bottom = 7.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(attachmentIcon(attachment), fontSize = 16.sp)
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = attachment.name,
                    color = colors.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatGeminiAttachmentSize(attachment.sizeBytes),
                    color = colors.muted,
                    fontSize = 10.sp,
                )
            }
            if (onRemove != null) {
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .semantics {
                            contentDescription = "Удалить файл ${attachment.name}"
                        }
                        .clickable(role = Role.Button, onClick = onRemove),
                    shape = CircleShape,
                    color = Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("×", color = colors.muted, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GeminiChatComposer(
    draftText: String,
    pendingAttachments: List<GeminiChatAttachmentUiState>,
    canSend: Boolean,
    isSending: Boolean,
    colors: GeminiChatColors,
    onDraftTextChange: (String) -> Unit,
    onAddAttachments: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.composerBackground,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 10.dp, top = 9.dp, end = 10.dp, bottom = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (pendingAttachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 76.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                ) {
                    items(
                        items = pendingAttachments,
                        key = { attachment -> attachment.id },
                    ) { attachment ->
                        GeminiAttachmentCard(
                            attachment = attachment,
                            colors = colors,
                            onRemove = { onRemoveAttachment(attachment.id) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.inputBackground)
                    .padding(6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                ComposerButton(
                    label = "+",
                    description = "Добавить файл",
                    colors = colors,
                    background = colors.attachButton,
                    enabled = true,
                    onClick = onAddAttachments,
                )

                BasicTextField(
                    value = draftText,
                    onValueChange = onDraftTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp, max = 130.dp)
                        .padding(horizontal = 3.dp, vertical = 8.dp),
                    textStyle = TextStyle(
                        color = colors.text,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { if (canSend) onSend() },
                    ),
                    maxLines = 6,
                    decorationBox = { innerTextField ->
                        Box {
                            if (draftText.isBlank()) {
                                Text(
                                    text = "Напишите сообщение…",
                                    color = colors.muted,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                ComposerButton(
                    label = if (isSending) "…" else "↑",
                    description = if (isSending) "Gemini обрабатывает запрос" else "Отправить",
                    colors = colors,
                    background = colors.primary,
                    enabled = canSend,
                    onClick = onSend,
                )
            }
        }
    }
}

@Composable
private fun ComposerButton(
    label: String,
    description: String,
    colors: GeminiChatColors,
    background: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .semantics { contentDescription = description }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(11.dp),
        color = background.copy(alpha = if (enabled) 1f else 0.45f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (background == colors.primary) Color.White else colors.text,
                fontSize = if (label == "↑") 21.sp else 23.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 24.sp,
            )
        }
    }
}

private data class GeminiChatColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val border: Color,
    val assistantBubble: Color,
    val composerBackground: Color,
    val inputBackground: Color,
    val attachButton: Color,
    val attachmentBackground: Color,
    val usageBackground: Color,
    val progressTrack: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val notice: Color,
    val noticeText: Color,
    val errorStatus: Color,
)

private fun geminiChatColors(darkTheme: Boolean) = if (darkTheme) {
    GeminiChatColors(
        background = Color(0xFF101318),
        surface = Color(0xFF171A1F),
        text = Color(0xFFE8EAED),
        muted = Color(0xFF9AA3AF),
        primary = Color(0xFF8AB4F8),
        border = Color(0xFF363C46),
        assistantBubble = Color(0xFF171A1F),
        composerBackground = Color(0xFF171A1F),
        inputBackground = Color(0xFF242A33),
        attachButton = Color(0xFF303741),
        attachmentBackground = Color(0xFF20252D),
        usageBackground = Color(0xFF172536),
        progressTrack = Color(0xFF263346),
        success = Color(0xFF69DB7C),
        warning = Color(0xFFFFC04D),
        error = Color(0xFFFF8A80),
        notice = Color(0xFF332B18),
        noticeText = Color(0xFFFFD166),
        errorStatus = Color(0xFF3A2022),
    )
} else {
    GeminiChatColors(
        background = Color(0xFFF6F8FC),
        surface = Color.White,
        text = Color(0xFF171B22),
        muted = Color(0xFF68717F),
        primary = Color(0xFF1C7ED6),
        border = Color(0xFFDDE3ED),
        assistantBubble = Color.White,
        composerBackground = Color.White,
        inputBackground = Color(0xFFF8F9FA),
        attachButton = Color(0xFFE9ECEF),
        attachmentBackground = Color.White,
        usageBackground = Color(0xFFEAF3FC),
        progressTrack = Color(0xFFDCEAF8),
        success = Color(0xFF2B8A3E),
        warning = Color(0xFFF08C00),
        error = Color(0xFFC92A2A),
        notice = Color(0xFFFFF8E6),
        noticeText = Color(0xFF9A6B12),
        errorStatus = Color(0xFFFFECEC),
    )
}

private fun formatGeminiTokenCount(value: Long): String =
    NumberFormat.getIntegerInstance().format(value.coerceAtLeast(0L))

private fun formatGeminiChatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatGeminiAttachmentSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    return when {
        safeBytes < 1_024L -> "$safeBytes Б"
        safeBytes < 1_048_576L ->
            String.format(Locale.getDefault(), "%.1f КБ", safeBytes / 1_024.0)
        else ->
            String.format(Locale.getDefault(), "%.1f МБ", safeBytes / 1_048_576.0)
    }
}

private fun attachmentIcon(attachment: GeminiChatAttachmentUiState): String {
    val mimeType = attachment.mimeType.lowercase(Locale.ROOT)
    val extension = attachment.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        mimeType.startsWith("image/") -> "🖼"
        extension == "pdf" || mimeType == "application/pdf" -> "📄"
        extension in setOf("xlsx", "xls", "csv") -> "📊"
        extension in setOf("doc", "docx", "txt", "json") -> "📝"
        else -> "📎"
    }
}

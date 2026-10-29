package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.example.util.AppStrings
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Interactive drag-and-drop vertical list that allows users to drag any item by its drag handle
 * to reorder priorities smoothly with automatic edge-scrolling (top/bottom of screen) and
 * guaranteed snap-to-nearest-slot on release so items never get stuck mid-drag.
 */
@Composable
fun <T> DraggableReorderColumn(
    items: List<T>,
    keySelector: (T) -> Any,
    onReorderFinished: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    verticalSpacing: Dp = 10.dp,
    itemContent: @Composable (index: Int, item: T, isDragging: Boolean, dragHandleModifier: Modifier) -> Unit
) {
    var localItems by remember { mutableStateOf(items) }
    var draggedKey by remember { mutableStateOf<Any?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var activeFingerWindowY by remember { mutableFloatStateOf(-1f) }

    val itemHeights = remember { mutableStateMapOf<Any, Float>() }
    val itemWindowY = remember { mutableStateMapOf<Any, Float>() }
    val latestOnReorderFinished by rememberUpdatedState(onReorderFinished)
    val spacingPx = with(LocalDensity.current) { verticalSpacing.toPx() }
    val view = LocalView.current

    // Always keep localItems synced with external updates (e.g., toggling Switches, clearing suspensions)
    LaunchedEffect(items) {
        if (draggedKey == null) {
            localItems = items
        } else {
            val byKey = items.associateBy { keySelector(it) }
            val updated = localItems.mapNotNull { existing -> byKey[keySelector(existing)] }
            val existingKeys = updated.map { keySelector(it) }.toSet()
            val added = items.filter { keySelector(it) !in existingKeys }
            localItems = updated + added
        }
    }

    fun stepSwapIfNeeded(snapThresholdRatio: Float = 0.48f) {
        val currentKey = draggedKey ?: return
        var curIdx = localItems.indexOfFirst { keySelector(it) == currentKey }
        if (curIdx !in localItems.indices) return

        // Support multi-step swaps if dragged fast or auto-scrolled
        var swapped = true
        var safetyCounter = 0
        while (swapped && safetyCounter < 15) {
            swapped = false
            safetyCounter++
            curIdx = localItems.indexOfFirst { keySelector(it) == currentKey }
            if (curIdx !in localItems.indices) break

            val curHeight = (itemHeights[currentKey] ?: 96f).coerceAtLeast(40f)
            if (dragOffsetY > 0f && curIdx < localItems.lastIndex) {
                val nextKey = keySelector(localItems[curIdx + 1])
                val nextHeight = (itemHeights[nextKey] ?: curHeight).coerceAtLeast(40f)
                val stepDistance = (curHeight + nextHeight) * 0.5f + spacingPx
                if (dragOffsetY > nextHeight * snapThresholdRatio) {
                    val mut = localItems.toMutableList()
                    val moved = mut.removeAt(curIdx)
                    mut.add(curIdx + 1, moved)
                    localItems = mut
                    dragOffsetY -= stepDistance
                    swapped = true
                }
            } else if (dragOffsetY < 0f && curIdx > 0) {
                val prevKey = keySelector(localItems[curIdx - 1])
                val prevHeight = (itemHeights[prevKey] ?: curHeight).coerceAtLeast(40f)
                val stepDistance = (curHeight + prevHeight) * 0.5f + spacingPx
                if (dragOffsetY < -prevHeight * snapThresholdRatio) {
                    val mut = localItems.toMutableList()
                    val moved = mut.removeAt(curIdx)
                    mut.add(curIdx - 1, moved)
                    localItems = mut
                    dragOffsetY += stepDistance
                    swapped = true
                }
            }
        }
    }

    fun finishDragAndSnapCleanly() {
        if (draggedKey != null) {
            // Snap to nearest slot if dragged past 35% of neighbor before releasing
            stepSwapIfNeeded(snapThresholdRatio = 0.35f)
            draggedKey = null
            dragOffsetY = 0f
            activeFingerWindowY = -1f
            latestOnReorderFinished(localItems)
        } else {
            dragOffsetY = 0f
            activeFingerWindowY = -1f
        }
    }

    // Continuous smooth auto-scroll when dragging near the top or bottom edge of the screen
    LaunchedEffect(draggedKey, scrollState) {
        val currentKey = draggedKey ?: return@LaunchedEffect
        val scroller = scrollState ?: return@LaunchedEffect

        while (draggedKey == currentKey) {
            val fingerY = activeFingerWindowY
            val viewportHeight = view.height.toFloat().coerceAtLeast(600f)
            val topZone = viewportHeight * 0.22f
            val bottomZone = viewportHeight * 0.78f

            if (fingerY > 0f) {
                if (fingerY < topZone && scroller.value > 0) {
                    val intensity = ((topZone - fingerY) / topZone).coerceIn(0.15f, 1f)
                    val delta = -26f * intensity
                    val consumed = scroller.scrollBy(delta)
                    if (abs(consumed) > 0.5f) {
                        dragOffsetY += consumed
                        stepSwapIfNeeded(0.45f)
                    }
                } else if (fingerY > bottomZone && scroller.value < scroller.maxValue) {
                    val intensity = ((fingerY - bottomZone) / (viewportHeight - bottomZone)).coerceIn(0.15f, 1f)
                    val delta = 26f * intensity
                    val consumed = scroller.scrollBy(delta)
                    if (abs(consumed) > 0.5f) {
                        dragOffsetY += consumed
                        stepSwapIfNeeded(0.45f)
                    }
                }
            }
            delay(16L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (draggedKey != null) {
                draggedKey = null
                dragOffsetY = 0f
                activeFingerWindowY = -1f
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        localItems.forEachIndexed { index, item ->
            val itemKey = keySelector(item)
            key(itemKey) {
                val isDragging = itemKey == draggedKey

                val dragModifier = Modifier.pointerInput(itemKey) {
                    try {
                        detectVerticalDragGestures(
                            onDragStart = { startOffset ->
                                draggedKey = itemKey
                                dragOffsetY = 0f
                                val baseWinY = itemWindowY[itemKey] ?: 0f
                                activeFingerWindowY = baseWinY + startOffset.y
                            },
                            onDragEnd = {
                                finishDragAndSnapCleanly()
                            },
                            onDragCancel = {
                                finishDragAndSnapCleanly()
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (draggedKey != itemKey) {
                                    draggedKey = itemKey
                                }
                                dragOffsetY += dragAmount
                                val baseWinY = itemWindowY[itemKey] ?: 0f
                                activeFingerWindowY = baseWinY + dragOffsetY + change.position.y
                                stepSwapIfNeeded(0.48f)
                            }
                        )
                    } finally {
                        if (draggedKey == itemKey) {
                            finishDragAndSnapCleanly()
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 12f else 0f)
                        .onGloballyPositioned { coords ->
                            itemHeights[itemKey] = coords.size.height.toFloat()
                            itemWindowY[itemKey] = coords.positionInWindow().y
                        }
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                            scaleX = if (isDragging) 1.02f else 1f
                            scaleY = if (isDragging) 1.02f else 1f
                            shadowElevation = if (isDragging) 18f else 0f
                        }
                ) {
                    itemContent(index, item, isDragging, dragModifier)
                }
            }
        }
    }
}

/**
 * Visual drag handle pill that users can grab and drag vertically to change priority order.
 */
@Composable
fun DragHandleBadge(
    priorityNumber: Int,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    labelText: String? = null,
    isCompact: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(if (isCompact) 8.dp else 10.dp),
        color = if (isDragging) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        },
        border = BorderStroke(
            1.dp,
            if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = dragHandleModifier
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isCompact) 7.dp else 10.dp,
                vertical = if (isCompact) 4.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = AppStrings.tr("גרור לשינוי סדר עדיפות", "Drag to reorder priority"),
                tint = if (isDragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (isCompact) 15.dp else 18.dp)
            )
            Text(
                text = labelText ?: if (isCompact) {
                    "#$priorityNumber"
                } else {
                    AppStrings.tr("גרור (#$priorityNumber)", "Drag (#$priorityNumber)")
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = if (isCompact) 10.sp else 11.sp,
                color = if (isDragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

data class CenteredMenuOption<T>(
    val value: T,
    val title: String,
    val subtitle: String? = null,
    val badge: String? = null
)

/**
 * Centered, well-padded modal selection menu that replaces cramped corner DropdownMenus.
 */
@Composable
fun <T> CenteredSelectionMenuDialog(
    title: String,
    subtitle: String? = null,
    options: List<CenteredMenuOption<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = AppStrings.tr("סגור", "Close")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(options) { _, option ->
                            val isSelected = option.value == selectedValue
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelect(option.value)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                onSelect(option.value)
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = option.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (!option.subtitle.isNullOrBlank()) {
                                                Text(
                                                    text = option.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    if (!option.badge.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = option.badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(AppStrings.tr("סגור", "Close"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

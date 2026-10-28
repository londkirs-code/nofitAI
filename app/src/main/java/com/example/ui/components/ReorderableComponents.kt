package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.example.util.AppStrings

/**
 * Interactive drag-and-drop vertical list that allows users to drag any item by its drag handle
 * (or drag modifier) to reorder priorities smoothly without Up/Down buttons.
 */
@Composable
fun <T> DraggableReorderColumn(
    items: List<T>,
    keySelector: (T) -> Any,
    onReorderFinished: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = 10.dp,
    itemContent: @Composable (index: Int, item: T, isDragging: Boolean, dragHandleModifier: Modifier) -> Unit
) {
    var localItems by remember { mutableStateOf(items) }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Int, Float>() }
    val latestOnReorderFinished by rememberUpdatedState(onReorderFinished)

    LaunchedEffect(items) {
        if (draggedIndex == -1) {
            localItems = items
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        localItems.forEachIndexed { index, item ->
            val isDragging = index == draggedIndex
            val itemKey = keySelector(item)

            val dragModifier = Modifier.pointerInput(itemKey, localItems.size) {
                detectVerticalDragGestures(
                    onDragStart = {
                        draggedIndex = localItems.indexOfFirst { keySelector(it) == itemKey }
                        dragOffsetY = 0f
                    },
                    onDragEnd = {
                        if (draggedIndex != -1) {
                            draggedIndex = -1
                            dragOffsetY = 0f
                            latestOnReorderFinished(localItems)
                        }
                    },
                    onDragCancel = {
                        if (draggedIndex != -1) {
                            draggedIndex = -1
                            dragOffsetY = 0f
                            latestOnReorderFinished(localItems)
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val curIdx = draggedIndex
                        if (curIdx in localItems.indices) {
                            dragOffsetY += dragAmount
                            val curHeight = (itemHeights[curIdx] ?: 140f).coerceAtLeast(48f)

                            if (dragOffsetY > curHeight * 0.55f && curIdx < localItems.lastIndex) {
                                val nextHeight = (itemHeights[curIdx + 1] ?: curHeight).coerceAtLeast(48f)
                                val mut = localItems.toMutableList()
                                val moved = mut.removeAt(curIdx)
                                mut.add(curIdx + 1, moved)
                                localItems = mut
                                draggedIndex = curIdx + 1
                                dragOffsetY -= nextHeight * 0.85f
                            } else if (dragOffsetY < -curHeight * 0.55f && curIdx > 0) {
                                val prevHeight = (itemHeights[curIdx - 1] ?: curHeight).coerceAtLeast(48f)
                                val mut = localItems.toMutableList()
                                val moved = mut.removeAt(curIdx)
                                mut.add(curIdx - 1, moved)
                                localItems = mut
                                draggedIndex = curIdx - 1
                                dragOffsetY += prevHeight * 0.85f
                            }
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 10f else 0f)
                    .onGloballyPositioned { coords ->
                        itemHeights[index] = coords.size.height.toFloat()
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

/**
 * Visual drag handle pill that users can grab and drag vertically to change priority order.
 */
@Composable
fun DragHandleBadge(
    priorityNumber: Int,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    labelText: String? = null
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = AppStrings.tr("גרור לשינוי סדר עדיפות", "Drag to reorder priority"),
                tint = if (isDragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = labelText ?: AppStrings.tr("גרור למיקום (#$priorityNumber)", "Drag (#$priorityNumber)"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
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

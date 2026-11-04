package com.example.beiwang.ui.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beiwang.data.entity.TagEntity

/**
 * 标签多选器：横向滚动的 chips，点选即切换选中态。
 * 用于备忘录详情与购物详情为条目打标签。
 */
@Composable
fun TagSelector(
    allTags: List<TagEntity>,
    selectedTagIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (allTags.isEmpty()) {
        Text(
            text = "暂无标签，可在「我的 → 标签管理」中创建",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        allTags.forEach { tag ->
            val selected = selectedTagIds.contains(tag.id)
            val color = Color(tag.color)
            FilterChip(
                selected = selected,
                onClick = { onToggle(tag.id) },
                label = { Text(tag.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.2f),
                    selectedLabelColor = color,
                    selectedLeadingIconColor = color
                )
            )
        }
    }
}

/**
 * 把一组标签 ID 解析为 TagEntity 列表（按传入 id 过滤，保持 allTags 的顺序）。
 */
fun resolveTags(allTags: List<TagEntity>, tagIds: Set<String>): List<TagEntity> =
    allTags.filter { tagIds.contains(it.id) }

/**
 * 用于列表项上的小标签 chip。
 */
@Composable
fun TagChip(tag: TagEntity) {
    val color = Color(tag.color)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = tag.name,
            fontSize = 11.sp,
            color = color
        )
    }
}

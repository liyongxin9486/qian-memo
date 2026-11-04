package com.example.beiwang.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 带「上移 / 下移」按钮的排序列表（购物清单与待办共用）。
 *
 * 点击调整顺序后显示 ↑ / ↓ 两个按钮：点一下把该行与相邻行交换。
 * 首行的 ↑、末行的 ↓ 自动置灰（disabled）。
 * 顺序变化后通过 [onReorder] 回调出去，由调用方写入 sortOrder 并触发同步。
 *
 * 注：原先的长按拖动排序已按需求移除（模拟器与部分真机上的长按拖动识别不稳定），
 * 统一改用点击箭头移动，行为可预期、也更容易被无障碍服务识别。
 */
@Composable
fun <T> OrderableList(
    items: List<T>,
    keyOf: (T) -> String,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 4.dp,
    content: @Composable (item: T) -> Unit
) {
    var sorting by remember { mutableStateOf(false) }
    // 本地顺序：点击后立即反馈，不等待 DB 回写；items 变化时以数据库顺序为准重新同步。
    val ordered = remember { mutableStateListOf<T>() }
    LaunchedEffect(items) {
        ordered.clear()
        ordered.addAll(items)
    }

    fun move(from: Int, to: Int) {
        if (from == to || to < 0 || to >= ordered.size) return
        val moved = ordered.removeAt(from)
        ordered.add(to, moved)
        onReorder(ordered.toList())
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(itemSpacing)
    ) {
        if (ordered.size > 1) {
            TextButton(onClick = { sorting = !sorting }) { Text(if (sorting) "完成排序" else "调整顺序") }
        }
        ordered.forEachIndexed { index, item ->
            key(keyOf(item)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sorting) {
                IconButton(
                    onClick = { move(index, index - 1) },
                    enabled = index > 0,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "上移",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { move(index, index + 1) },
                    enabled = index < ordered.lastIndex,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "下移",
                        modifier = Modifier.size(18.dp)
                    )
                }
                }
                Column(modifier = Modifier.weight(1f)) {
                    content(item)
                    HorizontalDivider()
                }
            }
            }
        }
    }
}

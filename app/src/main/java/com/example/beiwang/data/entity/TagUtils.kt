package com.example.beiwang.data.entity

/**
 * tagIds 在实体中以逗号分隔的字符串存储（如 "id1,id2"）。
 * 这里提供拆分/合并的纯函数，供 ViewModel 与列表渲染统一使用。
 */
fun String.splitTagIds(): List<String> =
    this.split(',').map { it.trim() }.filter { it.isNotEmpty() }

fun List<String>.joinTagIds(): String = this.joinToString(",")

fun Set<String>.joinTagIds(): String = this.joinToString(",")

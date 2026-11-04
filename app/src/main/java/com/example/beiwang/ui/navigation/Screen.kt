package com.example.beiwang.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object MemoList : Screen("memo_list", "备忘录", Icons.Default.CheckCircle)
    object ShoppingList : Screen("shopping_list", "购物清单", Icons.Default.ShoppingCart)
    object TodoList : Screen("todo_list", "待办", Icons.Default.List)
    object Profile : Screen("profile", "我的", Icons.Default.Person)
    
    // 详情页
    object MemoDetail : Screen("memo_detail/{memoId}", "备忘录详情", Icons.Default.Edit) {
        fun createRoute(memoId: String) = "memo_detail/$memoId"
    }
    object ShoppingDetail : Screen("shopping_detail/{itemId}", "商品详情", Icons.Default.Edit) {
        fun createRoute(itemId: String) = "shopping_detail/$itemId"
    }
    object TodoDetail : Screen("todo_detail/{todoId}", "待办详情", Icons.Default.Edit) {
        fun createRoute(todoId: String) = "todo_detail/$todoId"
    }
    
    // 搜索
    object Search : Screen("search", "搜索", Icons.Default.Search)
    
    // 回收站
    object Trash : Screen("trash", "回收站", Icons.Default.Delete)
    
    // 标签管理
    object TagManage : Screen("tag_manage", "标签管理", Icons.AutoMirrored.Filled.List)
}

// 底部导航栏项目
val bottomNavItems = listOf(
    Screen.MemoList,
    Screen.ShoppingList,
    Screen.TodoList,
    Screen.Profile
)

package com.example.beiwang.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.beiwang.data.AuthManager
import com.example.beiwang.widget.WidgetUpdater
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.beiwang.ui.login.LoginScreen
import com.example.beiwang.ui.memo.MemoListScreen
import com.example.beiwang.ui.memo.MemoDetailScreen
import com.example.beiwang.ui.shopping.ShoppingListScreen
import com.example.beiwang.ui.shopping.ShoppingDetailScreen
import com.example.beiwang.ui.todo.TodoDetailScreen
import com.example.beiwang.ui.todo.TodoListScreen
import com.example.beiwang.ui.profile.ProfileScreen
import com.example.beiwang.ui.search.SearchScreen
import com.example.beiwang.ui.trash.TrashScreen
import com.example.beiwang.ui.tag.TagManageScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(openMemoId: Pair<String, Int>? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 确保加密存储已初始化（理论上 BeiwangApp.onCreate 已做，这里兜底）。
    AuthManager.init(context)
    var isLoggedIn by remember { mutableStateOf(AuthManager.isLoggedIn) }

    // 监听强制登出信号（token 刷新彻底失败 / WS 被踢 / 关闭码 4001）：跳回登录页。
    // 注意：此处不再清本地库——被动登出属于「同账号会话过期」，BeiwangApp 会尝试用
    // 「记住我」凭证静默自动重登挽回；若重登成功，本地数据（同账号）应当保留并重新同步。
    // 仅在用户主动「退出登录」（onLogout）时才清库，避免误删。
    LaunchedEffect(Unit) {
        AuthManager.logoutSignal.collect {
            isLoggedIn = false
        }
    }

    // 登录成功信号（手动登录 / 自动重登挽回）：切回主界面。
    LaunchedEffect(Unit) {
        AuthManager.loginSignal.collect {
            isLoggedIn = true
        }
    }

    if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = {
            isLoggedIn = true
        })
    } else {
        MainApp(onLogout = {
            // 主动「退出账号」：只清会话态、保留「记住我」凭证（含密码）——这正是此前
            // 「记住我也无效」的根因修复点。置位 isManualLogout 抑制 BeiwangApp 的自动重登，
            // 让退出能真正停在登录页；进程被回收重建后该标志复位，下次冷启动 tryAutoLogin
            // 会用凭证静默重登，用户无需重新输入密码（记住我 / 保持登录生效）。
            // 本地库不清：仍属同账号，清库会丢本地草稿；若用户换号登录，
            // LoginViewModel.submit 会 AppDatabase.clearAll 兜底多账号隔离。
            // 想彻底清除凭证（注销/换号且不再自动登录）：在登录界面取消勾选「记住我」后再登录。
            AuthManager.isManualLogout = true
            AuthManager.clear()
            isLoggedIn = false
            scope.launch(Dispatchers.IO) {
                WidgetUpdater.refresh(context)
            }
        }, openMemoId = openMemoId)
    }
}

@Composable
fun MainApp(onLogout: () -> Unit, openMemoId: Pair<String, Int>? = null) {
    val navController = rememberNavController()
    
    LaunchedEffect(openMemoId) {
        val memoId = openMemoId?.first
        if (memoId != null) {
            // launchSingleTop：重复点击同一条深链时复用栈顶页面并刷新参数，而不是叠加重复页面
            navController.navigate(Screen.MemoDetail.createRoute(memoId)) { launchSingleTop = true }
        }
    }
    
    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            
            val showBottomBar = bottomNavItems.any { screen ->
                currentDestination?.hierarchy?.any { it.route == screen.route } == true
            }
            
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.MemoList.route,
            // 仅保留底部导航栏留白；状态栏由各个页面自己的 TopAppBar 按
            // WindowInsets.statusBars 处理，避免状态栏 inset 被算两遍导致
            // 标题离状态栏过远（不同机型自适应）。
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Screen.MemoList.route) {
                MemoListScreen(
                    onSearchClick = { navController.navigate(Screen.Search.route) },
                    onMemoClick = { memoId ->
                        navController.navigate(Screen.MemoDetail.createRoute(memoId))
                    },
                    onNewMemoClick = {
                        // 新建备忘把草稿 id 带进路由参数：旋转屏幕/进程重建后仍保持同一 id，
                        // 附件与清单不会因重建而丢失或产生重复记录
                        navController.navigate(Screen.MemoDetail.createRoute("new_${java.util.UUID.randomUUID()}"))
                    }
                )
            }
            
            composable(
                route = Screen.MemoDetail.route,
                arguments = listOf(navArgument("memoId") { type = NavType.StringType })
            ) { backStackEntry ->
                val memoId = backStackEntry.arguments?.getString("memoId") ?: "new"
                MemoDetailScreen(
                    memoId = memoId,
                    onBackClick = { 
                        navController.popBackStack(Screen.MemoList.route, false)
                    }
                )
            }
            
            composable(Screen.ShoppingList.route) {
                ShoppingListScreen(
                    onSearchClick = { navController.navigate(Screen.Search.route) },
                    onItemClick = { itemId ->
                        navController.navigate(Screen.ShoppingDetail.createRoute(itemId))
                    },
                    onNewItemClick = {
                        navController.navigate(Screen.ShoppingDetail.createRoute("new"))
                    }
                )
            }
            
            composable(
                route = Screen.ShoppingDetail.route,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType })
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId") ?: "new"
                ShoppingDetailScreen(
                    itemId = itemId,
                    onBackClick = { 
                        navController.popBackStack(Screen.ShoppingList.route, false)
                    }
                )
            }
            
            composable(Screen.TodoList.route) {
                TodoListScreen(
                    onSearchClick = { navController.navigate(Screen.Search.route) },
                    onTodoClick = { todoId ->
                        navController.navigate(Screen.TodoDetail.createRoute(todoId))
                    },
                    onNewTodoClick = {
                        navController.navigate(Screen.TodoDetail.createRoute("new"))
                    }
                )
            }

            composable(
                route = Screen.TodoDetail.route,
                arguments = listOf(navArgument("todoId") { type = NavType.StringType })
            ) { backStackEntry ->
                val todoId = backStackEntry.arguments?.getString("todoId") ?: "new"
                TodoDetailScreen(
                    todoId = todoId,
                    onBackClick = {
                        navController.popBackStack(Screen.TodoList.route, false)
                    }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onTagManageClick = { navController.navigate(Screen.TagManage.route) },
                    onTrashClick = { navController.navigate(Screen.Trash.route) },
                    onLogoutClick = onLogout
                )
            }
            
            composable(Screen.Search.route) {
                SearchScreen(
                    onBackClick = { navController.popBackStack() },
                    onMemoClick = { memoId ->
                        navController.navigate(Screen.MemoDetail.createRoute(memoId))
                    },
                    onItemClick = { itemId ->
                        navController.navigate(Screen.ShoppingDetail.createRoute(itemId))
                    },
                    onTodoClick = { todoId ->
                        navController.navigate(Screen.TodoDetail.createRoute(todoId))
                    }
                )
            }
            
            composable(Screen.Trash.route) {
                TrashScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            
            composable(Screen.TagManage.route) {
                TagManageScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}

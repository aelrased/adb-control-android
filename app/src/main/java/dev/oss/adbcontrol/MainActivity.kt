package dev.oss.adbcontrol

import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT <= 28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
        setContent {
            OpenAdbTheme { Root(vm) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root(vm: AppViewModel) {
    val context = LocalContext.current
    val apps by vm.apps.collectAsStateWithLifecycle()
    val adb by vm.adbState.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    var pendingBulk by remember { mutableStateOf<String?>(null) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var screen by rememberSaveable { mutableStateOf("apps") }
    var showSearch by remember { mutableStateOf(false) }
    var actionsApp by remember { mutableStateOf<AppEntry?>(null) }
    var showPairing by remember { mutableStateOf(false) }
    var showAddDevice by remember { mutableStateOf(false) }

    val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.installApk(uri)
    }

    LaunchedEffect(Unit) {
        vm.toast.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                adb = adb,
                onNavigate = { target ->
                    screen = target
                    scope.launch { drawerState.close() }
                },
                onConnectSelf = { showPairing = true },
                onSelectDevice = { id ->
                    vm.selectDevice(id)
                    scope.launch { drawerState.close() }
                },
                onRemoveDevice = { id -> vm.removeDevice(id) },
                onAddDevice = { showAddDevice = true },
                onSyncRemote = { vm.syncRemoteIcons() }
            )
        }
    ) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                Column {
                    Header(
                        connected = adb.connected,
                        label = adb.label.ifEmpty { "${Build.MANUFACTURER} ${Build.MODEL}".uppercase() },
                        serial = adb.serial,
                        showSearch = showSearch,
                        query = query,
                        onQuery = { vm.query.value = it },
                        onMenu = { scope.launch { drawerState.open() } },
                        onRefresh = { vm.refresh() },
                        onMore = { screen = "settings" }
                    )
                    if (loading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentGreen,
                            trackColor = Bg
                        )
                    }
                }
            },
            bottomBar = {
                if (selection.isNotEmpty()) {
                    SelectionBar(
                        count = selection.size,
                        onClose = { vm.clearSelection() },
                        onSelectAll = { vm.selectAll(apps.map { it.packageName }) },
                        onDisable = { vm.bulkDisable() },
                        onEnable = { vm.bulkEnable() },
                        onBackup = { vm.backupSelected() },
                        onClearCache = { vm.bulkClearCache() },
                        onUninstall = { pendingBulk = "uninstall" },
                        onClear = { pendingBulk = "clear" }
                    )
                } else {
                    BottomBar(
                        filter = filter,
                        disabledCount = apps.count { !it.enabled },
                        onEnableAll = { vm.enableAllDisabled() },
                        onFilter = {
                            vm.filter.value = it
                            screen = "apps"
                        },
                        onSearch = {
                            screen = "apps"
                            showSearch = !showSearch
                        }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize().background(Bg)) {
                when (screen) {
                    "settings" -> SettingsScreen(vm)
                    "clean" -> CleanScreen(vm)
                    "library" -> LibraryScreen(
                        vm = vm,
                        onInstall = { installLauncher.launch(arrayOf("*/*")) }
                    )
                    else -> AppList(
                        apps = apps,
                        query = query,
                        filter = filter,
                        sort = sort,
                        adb = adb,
                        needsRemoteIcons = adb.active?.let { !it.isSelf && !vm.hasRemoteIcons() } == true,
                        selection = selection,
                        onConnect = { showPairing = true },
                        onSyncIcons = { vm.syncRemoteIcons() },
                        onApp = { actionsApp = it },
                        onToggle = { vm.toggleSelection(it) },
                        onLongPress = { vm.beginSelection(it) }
                    )
                }
            }
        }
    }

    if (showPairing) {
        PairingDialog(
            adb = adb,
            onStart = { vm.startSelfPairing() },
            onPair = { vm.pairSelf(it) },
            onPairManual = { host, pairingPort, connectHost, connectPort, code ->
                vm.pairRemote(host, pairingPort, connectHost, connectPort, code)
            },
            onConnectManual = { host, port -> vm.addDevice(host, port, null, null) },
            onConnect = { vm.connectSelf() },
            onLocal = {
                vm.connectLocal()
                showPairing = false
            },
            onClose = { showPairing = false }
        )
    }

    if (showAddDevice) {
        AddDeviceDialog(
            onAdd = { host, port, pairingPort, code ->
                vm.addDevice(host, port, pairingPort, code)
                showAddDevice = false
            },
            onClose = { showAddDevice = false }
        )
    }

    actionsApp?.let { app ->
        ActionsSheet(
            app = app,
            adbConnected = adb.connected,
            onDismiss = { actionsApp = null },
            onAction = { action ->
                vm.perform(action, app)
                actionsApp = null
            }
        )
    }

    pendingBulk?.let { kind ->
        AlertDialog(
            onDismissRequest = { pendingBulk = null },
            title = {
                Text(
                    if (kind == "uninstall") "حذف التطبيقات المحددة؟"
                    else "مسح بيانات التطبيقات المحددة؟"
                )
            },
            text = { Text("عدد التطبيقات: ${selection.size}. لا يمكن التراجع.") },
            confirmButton = {
                TextButton(onClick = {
                    if (kind == "uninstall") vm.bulkUninstall() else vm.bulkClear()
                    pendingBulk = null
                }) { Text("تأكيد") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulk = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun Header(
    connected: Boolean,
    label: String,
    serial: String,
    showSearch: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onMenu: () -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(AccentBlue, AccentGreen)))
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Outlined.Menu,
                    contentDescription = "Menu",
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(color = Color(0x33000000), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            if (connected) "ADB" else "ADB OFF",
                            color = Color.White,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    if (serial.isNotEmpty()) serial else "غير متصل",
                    color = Color(0xE6FFFFFF),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onMore, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "More",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (showSearch) {
            TextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
                singleLine = true,
                placeholder = { Text("Search apps or packages…", color = Color(0xCCFFFFFF), fontSize = 13.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedIndicatorColor = Color.White.copy(alpha = 0.7f),
                    unfocusedIndicatorColor = Color.White.copy(alpha = 0.4f)
                )
            )
        }
    }
}

@Composable
private fun BottomBar(
    filter: AppFilter,
    disabledCount: Int,
    onFilter: (AppFilter) -> Unit,
    onEnableAll: () -> Unit,
    onSearch: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF12161D))) {
        if (filter == AppFilter.DISABLED) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "معطّل: $disabledCount",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onEnableAll,
                    enabled = disabledCount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) { Text("تفعيل الكل") }
            }
        }
        NavigationBar(containerColor = Color(0xFF12161D), tonalElevation = 0.dp) {
        val items = listOf(
            Triple("All", Icons.Outlined.Apps, AppFilter.ALL),
            Triple("User", Icons.Outlined.Person, AppFilter.USER),
            Triple("System", Icons.Outlined.PhoneAndroid, AppFilter.SYSTEM),
            Triple("Disabled", Icons.Outlined.Block, AppFilter.DISABLED),
            Triple("Search", Icons.Outlined.Search, null)
        )
        items.forEach { (label, icon, value) ->
            NavigationBarItem(
                selected = value != null && filter == value,
                onClick = { if (value != null) onFilter(value) else onSearch() },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = AccentGreen.copy(alpha = 0.35f),
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                )
            )
        }
        }
    }
}

@Composable
private fun AppList(
    apps: List<AppEntry>,
    query: String,
    filter: AppFilter,
    sort: AppSort,
    adb: AdbUiState,
    needsRemoteIcons: Boolean,
    selection: Set<String>,
    onConnect: () -> Unit,
    onSyncIcons: () -> Unit,
    onApp: (AppEntry) -> Unit,
    onToggle: (String) -> Unit,
    onLongPress: (String) -> Unit
) {
    val filtered = remember(apps, query, filter, sort) {
        val q = query.trim().lowercase(Locale.getDefault())
        val base = apps.filter { app ->
            when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.system
                AppFilter.SYSTEM -> app.system
                AppFilter.DISABLED -> !app.enabled
            } && (q.isEmpty() || app.label.lowercase(Locale.getDefault()).contains(q) ||
                app.packageName.lowercase(Locale.getDefault()).contains(q))
        }
        when (sort) {
            AppSort.NAME -> base
            AppSort.DATE -> base.sortedByDescending { it.updatedAt }
            AppSort.SIZE -> base.sortedByDescending { it.sizeBytes }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!adb.connected) {
            item {
                ConnectBanner(adb = adb, onConnect = onConnect)
            }
        } else if (needsRemoteIcons) {
            item {
                RemoteIconsBanner(
                    label = adb.active?.label ?: "",
                    onSync = onSyncIcons
                )
            }
        }
        items(filtered, key = { it.packageName }) { app ->
            AppRow(
                app = app,
                selected = selection.contains(app.packageName),
                selectionMode = selection.isNotEmpty(),
                onClick = {
                    if (selection.isNotEmpty()) onToggle(app.packageName) else onApp(app)
                },
                onLongClick = { onLongPress(app.packageName) }
            )
        }
        item {
            Text(
                "ADB Control Android v0.1.0 · ${filtered.size} apps",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDisable: () -> Unit,
    onEnable: () -> Unit,
    onBackup: () -> Unit,
    onClearCache: () -> Unit,
    onUninstall: () -> Unit,
    onClear: () -> Unit
) {
    Surface(color = Color(0xFF12161D)) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = TextMuted)
                }
                Text(
                    "$count محدد",
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSelectAll) { Text("الكل") }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BarAction("تعطيل", Icons.Outlined.Block, onDisable)
                BarAction("تفعيل", Icons.Outlined.CheckCircle, onEnable)
                BarAction("نسخة احتياطية", Icons.Outlined.SaveAlt, onBackup)
                BarAction("مسح الكاش", Icons.Outlined.CleaningServices, onClearCache)
                BarAction("مسح البيانات", Icons.Outlined.DeleteSweep, onClear)
                BarAction("حذف", Icons.Outlined.Delete, onUninstall, danger = true)
            }
        }
    }
}

@Composable
private fun BarAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    TextButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) Danger else TextMuted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = if (danger) Danger else TextMain, fontSize = 12.sp)
    }
}

@Composable
private fun ConnectBanner(adb: AdbUiState, onConnect: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2A22))) {
        Column(Modifier.padding(14.dp)) {
            Text("ADB غير متصل", color = Color(0xFF8CE0AE), fontWeight = FontWeight.Bold)
            Text(
                "فعّل Wireless debugging ثم اضغط Connect لمنح التطبيق صلاحية shell لإدارة التطبيقات.",
                color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
            )
            if (adb.message.isNotEmpty()) {
                Text(adb.message, color = Color(0xFFFFCF70), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Button(onClick = onConnect, modifier = Modifier.padding(top = 8.dp)) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun RemoteIconsBanner(label: String, onSync: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF16233A))) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "أيقونات وأسماء الجهاز البعيد",
                color = Color(0xFF9DC1FF),
                fontWeight = FontWeight.Bold
            )
            Text(
                "سيتم تثبيت جسر صغير على «$label» لجلب الأيقونات والأسماء الحقيقية (مرة واحدة).",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Button(onClick = onSync, modifier = Modifier.padding(top = 8.dp)) {
                Text("تحميل الأيقونات")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        color = if (selected) Color(0xFF16233A) else CardBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            AppIcon(app)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.packageName,
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(app.version.ifEmpty { "—" }, color = TextMuted, fontSize = 11.sp, maxLines = 1)
                Text(formatSize(app.sizeBytes), color = TextMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (app.enabled) AccentGreen else Color(0xFF4A5160))
            )
        }
    }
}

@Composable
private fun AppIcon(app: AppEntry) {
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.size(46.dp).clip(shape), contentAlignment = Alignment.Center) {
        val icon = app.icon
        if (icon != null) {
            Image(bitmap = icon, contentDescription = app.label, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                Modifier.fillMaxSize().background(AccentBlue.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(app.label.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionsSheet(
    app: AppEntry,
    adbConnected: Boolean,
    onDismiss: () -> Unit,
    onAction: (AppAction) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardBg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(app.label, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(app.packageName, color = TextMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Divider)
            ActionItem("Open", Icons.Outlined.OpenInNew) { onAction(AppAction.OPEN) }
            ActionItem("App info", Icons.Outlined.Info) { onAction(AppAction.INFO) }
            ActionItem(
                if (app.enabled) "Disable (ADB)" else "Enable (ADB)",
                if (app.enabled) Icons.Outlined.Block else Icons.Outlined.CheckCircle
            ) { onAction(if (app.enabled) AppAction.DISABLE else AppAction.ENABLE) }
            ActionItem("Clear cache (ADB)", Icons.Outlined.CleaningServices) { onAction(AppAction.CLEAR_CACHE) }
            ActionItem("Clear data (ADB)", Icons.Outlined.DeleteSweep) { onAction(AppAction.CLEAR) }
            ActionItem("Extract APK", Icons.Outlined.SaveAlt) { onAction(AppAction.EXTRACT) }
            ActionItem("Uninstall", Icons.Outlined.Delete, danger = true) { onAction(AppAction.UNINSTALL) }
            if (!adbConnected) {
                Text(
                    "الإجراءات المعلّمة بـ ADB تحتاج اتصالاً عبر زر Connect.",
                    color = Color(0xFFFFCF70),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionItem(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (danger) Danger else TextMuted)
            Spacer(Modifier.width(14.dp))
            Text(label, color = if (danger) Danger else TextMain, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PairingDialog(
    adb: AdbUiState,
    onStart: () -> Unit,
    onPair: (String) -> Unit,
    onPairManual: (String, Int, String?, Int?, String) -> Unit,
    onConnectManual: (String, Int) -> Unit,
    onConnect: () -> Unit,
    onLocal: () -> Unit,
    onClose: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var pairHost by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { onStart() }
    LaunchedEffect(adb.pairingEndpoint, adb.connectEndpoint) {
        adb.pairingEndpoint?.let {
            if (pairHost.isBlank()) pairHost = it.host
            if (pairPort.isBlank()) pairPort = it.port.toString()
        }
        adb.connectEndpoint?.let {
            if (pairHost.isBlank()) pairHost = it.host
            if (connectPort.isBlank()) connectPort = it.port.toString()
        }
    }

    val manualHost = pairHost.substringBefore(":").trim()
    val manualPairPort = pairHost.substringAfter(":", "").toIntOrNull() ?: pairPort.toIntOrNull()
    val manualConnect = connectPort.toIntOrNull()
    val manualReady = manualHost.isNotBlank() && manualPairPort != null

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF12161D),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f)
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Connect over ADB",
                        color = TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "منفذ الاقتران (Pairing): " + (adb.pairingEndpoint?.let { "${it.host}:${it.port}" } ?: "لم يُعثر عليه — أدخله يدوياً"),
                        fontSize = 12.sp,
                        color = if (adb.pairingEndpoint != null) AccentGreen else Color(0xFFFFCF70)
                    )
                    Text(
                        "منفذ الاتصال (Connect): " + (adb.connectEndpoint?.let { "${it.host}:${it.port}" } ?: "غير متاح"),
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 8.dp))
                    Text("إدخال يدوي", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                    Text(
                        "انسخ الأرقام من شاشة Wireless debugging: منفذ الاقتران من نافذة «Pair with device»، " +
                            "ومنفذ Connect من الشاشة الرئيسية تحت مفتاح On.",
                        fontSize = 10.5.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        OutlinedTextField(
                            value = pairHost,
                            onValueChange = { pairHost = it.trim() },
                            label = { Text("IP / host:pairing-port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = pairPort,
                            onValueChange = { pairPort = it.filter(Char::isDigit).take(5) },
                            label = { Text("Pair port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(110.dp)
                        )
                    }
                    OutlinedTextField(
                        value = connectPort,
                        onValueChange = { connectPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("Connect port (بعد الاقتران)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        label = { Text("6-digit pairing code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                    if (adb.message.isNotEmpty()) {
                        Text(
                            adb.message,
                            fontSize = 11.sp,
                            color = Color(0xFFFFCF70),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (manualReady) {
                                onPairManual(
                                    manualHost,
                                    manualPairPort!!,
                                    manualHost,
                                    manualConnect,
                                    code
                                )
                            } else {
                                onPair(code)
                            }
                        },
                        enabled = !adb.busy && code.length == 6 && (manualReady || adb.pairingEndpoint != null),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Pair") }
                    OutlinedButton(
                        onClick = {
                            if (manualHost.isNotBlank() && manualConnect != null) {
                                onConnectManual(manualHost, manualConnect)
                            } else {
                                onConnect()
                            }
                        },
                        enabled = !adb.busy && (manualConnect != null || adb.connectEndpoint != null),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Text(
                            if (manualConnect != null) "Connect إلى $manualHost:$manualConnect"
                            else "Connect"
                        )
                    }
                    TextButton(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("إعادة البحث عن منفذ الاقتران") }

                    HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 8.dp))

                    TextButton(onClick = onLocal, modifier = Modifier.fillMaxWidth()) {
                        Text("استخدام هذا الجهاز (IP الواي فاي تلقائياً)")
                    }
                    Text(
                        "يُكتشف عنوان IP للواي فاي تلقائياً ثم يتصل بالمنفذ 5555، مع محاولة 127.0.0.1 كبديل. " +
                            "وافق على Allow USB debugging على الشاشة ثم أعد المحاولة.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        Text(
                            "الاقتران الذاتي يتطلب Android 11 أو أحدث. على Android 10 يلزم تفعيل adb tcpip من كمبيوتر.",
                            fontSize = 11.sp,
                            color = Danger,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun CleanScreen(vm: AppViewModel) {
    val adb by vm.adbState.collectAsStateWithLifecycle()
    var profiles by remember { mutableStateOf<List<CytProfile>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var matched by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var count by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        profiles = vm.cytProfiles()
        matched = vm.cytMatch()
        selected = matched ?: profiles.firstOrNull()?.id
    }
    LaunchedEffect(selected) {
        count = selected?.let { vm.cytPackages(it).size } ?: 0
    }

    val filtered = remember(profiles, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) profiles else profiles.filter {
            it.title.lowercase().contains(q) || it.id.contains(q)
        }
    }
    val current = profiles.firstOrNull { it.id == selected }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("CleanYourTV", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("الجهاز: ${adb.active?.label ?: "غير متصل"}", color = TextMuted, fontSize = 12.sp)
        if (matched != null) {
            val name = profiles.firstOrNull { it.id == matched }?.title ?: matched
            Text("تمت مطابقة ملف: $name", color = AccentGreen, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("ابحث عن موديل التلفزيون") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "${filtered.size} من ${profiles.size} ملف تعريف — مرّر للأسفل لعرضها كلها",
            color = TextMuted,
            fontSize = 10.5.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )

        Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
            Column(Modifier.padding(12.dp)) {
                Text(current?.title ?: "اختر ملفاً", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("$count حزمة قابلة للتعطيل", color = TextMuted, fontSize = 12.sp)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { selected?.let { vm.applyDebloat(it, false) } },
                        enabled = selected != null && adb.connected
                    ) { Text("تعطيل") }
                    OutlinedButton(
                        onClick = { selected?.let { vm.applyDebloat(it, true) } },
                        enabled = selected != null && adb.connected
                    ) { Text("استعادة") }
                }
                Text(
                    "تعطيل فقط عبر pm disable-user وليس حذفاً. استخدم «استعادة» للتراجع.",
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (!adb.connected) {
                    Text("يتطلب اتصال ADB بالجهاز", color = Danger, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filtered, key = { it.id }) { profile ->
                Surface(
                    color = if (profile.id == selected) Color(0xFF17301F) else CardBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().clickable { selected = profile.id }
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            profile.title,
                            color = TextMain,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            profile.id + if (profile.os.isNotEmpty()) " · ${profile.os}" else "",
                            color = TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val monitoring by vm.monitoring.collectAsStateWithLifecycle()
    val beta by vm.beta.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)
    ) {
        SectionTitle("About")
        ValueRow("Software version", "0.1.0 (1)")
        ValueRow("Your license", "GPL-3.0 · Free & open source")

        SectionTitle("Appearance")
        ValueRow("Application theme", "Gradient (dark)")
        ValueRow("Language", "English")

        SectionTitle("Common")
        SwitchRow(
            title = "Apps monitoring",
            subtitle = "إشعار عند تثبيت/تحديث التطبيقات (بيتا)",
            checked = monitoring,
            onChange = vm::setMonitoring
        )
        ValueRow("Folder for APK files", "/storage/emulated/0/${Storage.displayPath}")

        SectionTitle("Debugging via TCP/IP")
        Text(
            "يستخدم التطبيق Wireless Debugging المحلي (TLS) لإدارة هذا الجهاز دون كمبيوتر.",
            color = TextMuted, fontSize = 12.sp
        )
        ValueRow("TCP/IP port", "5555")

        SectionTitle("Development")
        SwitchRow(
            title = "Beta testing",
            subtitle = "تحديثات تجريبية وميزات مبكرة",
            checked = beta,
            onChange = vm::setBeta
        )

        SectionTitle("Open source")
        TextButton(onClick = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }) { Text("Source code") }
        TextButton(onClick = {
            context.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:dev@example.com"))
                    .putExtra(Intent.EXTRA_SUBJECT, "ADB Control Android feedback")
            )
        }) { Text("Feedback") }

        Spacer(Modifier.height(30.dp))
        Text("© 2026 ADB Control Android · GPL-3.0", color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = AccentGreen,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun ValueRow(title: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, color = TextMain, fontSize = 14.sp)
        Text(value, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 14.sp)
            Text(subtitle, color = TextMuted, fontSize = 11.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen)
        )
    }
}

@Composable
private fun LibraryScreen(vm: AppViewModel, onInstall: () -> Unit) {
    var files by remember { mutableStateOf<List<Storage.SavedApk>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }
    LaunchedEffect(refreshKey) {
        files = withContext(Dispatchers.IO) { vm.savedApks() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Library", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { refreshKey++ }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = TextMuted)
                }
            }
            Text(
                "/storage/emulated/0/${Storage.displayPath}",
                color = TextMuted,
                fontSize = 11.sp
            )
            Button(
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Icon(Icons.Outlined.InstallMobile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("تثبيت APK / .apks لهذا الجهاز")
            }
            Text(
                "هذا هو المكان الوحيد للتثبيت: اختر ملفاً من الهاتف، أو اضغط أي ملف في المكتبة لتثبيته.",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
            )
        }
        if (files.isEmpty()) {
            item {
                Text(
                    "لا توجد ملفات بعد. استخدم «Extract APK» من قائمة أي تطبيق أو زر التثبيت أعلاه.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
        }
        items(files, key = { it.uri.toString() }) { file ->
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().clickable { vm.installApk(file.uri) }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.LibraryBooks, contentDescription = null, tint = AccentGreen)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.name,
                            color = TextMain,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(formatSize(file.size), color = TextMuted, fontSize = 11.sp)
                    }
                    Icon(Icons.Outlined.InstallMobile, contentDescription = null, tint = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    adb: AdbUiState,
    onNavigate: (String) -> Unit,
    onConnectSelf: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onAddDevice: () -> Unit,
    onSyncRemote: () -> Unit
) {
    val context = LocalContext.current
    ModalDrawerSheet(drawerContainerColor = Color(0xFF12161D)) {
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Brush.horizontalGradient(listOf(AccentBlue, AccentGreen)))
            ) {
                Row(
                    Modifier.fillMaxSize().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painterResource(R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("ADB Control Android", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Android · Open Source · GPL-3.0", color = Color(0xFFEAF7EF), fontSize = 11.sp)
                    }
                }
            }

            DrawerItem(Icons.Outlined.LibraryBooks, "Library") { onNavigate("library") }
            DrawerItem(Icons.Outlined.Settings, "Settings") { onNavigate("settings") }
            DrawerItem(Icons.Outlined.Feedback, "Feedback") {
                context.startActivity(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:dev@example.com"))
                        .putExtra(Intent.EXTRA_SUBJECT, "ADB Control Android feedback")
                )
            }
            DrawerItem(Icons.Outlined.Sync, "Sync remote data") { onSyncRemote() }
            DrawerItem(Icons.Outlined.CleaningServices, "CleanYourTV") { onNavigate("clean") }
            DrawerItem(Icons.Outlined.Add, "Add network device") { onAddDevice() }
            DrawerItem(Icons.Outlined.PhoneAndroid, "Connect over ADB") { onConnectSelf() }

            HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 6.dp))

            Text(
                "Connected devices",
                color = TextMuted, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )

            adb.devices.forEach { device ->
                DrawerDeviceRow(
                    device = device,
                    active = device.id == adb.activeId,
                    onClick = { onSelectDevice(device.id) },
                    onRemove = if (device.isSelf) null else ({ onRemoveDevice(device.id) })
                )
            }

            if (adb.devices.none { it.isSelf }) {
                Surface(
                    color = Color(0xFF1A1F27),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clickable(onClick = onConnectSelf)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = TextMuted)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("This device", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Tap to pair over Wireless debugging", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onAddDevice,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth()
            ) { Text("+ Add network device") }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2430)),
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Multiple devices", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "اختر جهازاً من القائمة أعلاه. الأجهزة المضافة تُحفظ وتُعاد محاولة الاتصال عند اختيارها.",
                        color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "© 2026 ADB Control Android",
                color = TextMuted, fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun DrawerDeviceRow(
    device: DeviceInfo,
    active: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?
) {
    Surface(
        color = if (active) Color(0xFF17301F) else Color(0xFF1A1F27),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = if (active) AccentGreen else TextMuted
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.label,
                    color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (device.isSelf) "This device" else "${device.host}:${device.port}",
                    color = if (active) AccentGreen else TextMuted, fontSize = 11.sp
                )
            }
            if (active) {
                Text("ADB", color = AccentGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AddDeviceDialog(
    onAdd: (String, Int, Int?, String?) -> Unit,
    onClose: () -> Unit
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5555") }
    var pairingPort by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    val targetHost = host.substringBefore(":").trim()
    val typedPort = host.substringAfter(":", "").toIntOrNull()
    val connectPort = typedPort ?: port.toIntOrNull()

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF12161D),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(0.96f)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Add network device",
                        color = TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "أدخل IP الجهاز. للأندرويد 11+ عبر Wireless debugging أدخل أيضاً منفذ الاقتران والرمز 6 أرقام.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it.trim() },
                        label = { Text("IP / host:port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = { Text("Connect port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                    OutlinedTextField(
                        value = pairingPort,
                        onValueChange = { pairingPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("Pairing port (من نافذة Pair with device)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        label = { Text("Pairing code (6 أرقام)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                    Text(
                        "USB: adb tcpip 5555. اللاسلكي: Wireless debugging ← Pair with device ← انسخ IP:port والرمز.",
                        fontSize = 10.5.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onClose) { Text("Cancel") }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = {
                            val p = connectPort
                            if (targetHost.isNotBlank() && p != null) {
                                onAdd(targetHost, p, pairingPort.toIntOrNull(), code.ifBlank { null })
                            }
                        },
                        enabled = targetHost.isNotBlank() && connectPort != null
                    ) { Text("Add") }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = TextMuted)
            Spacer(Modifier.width(16.dp))
            Text(label, color = TextMain, fontSize = 15.sp)
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

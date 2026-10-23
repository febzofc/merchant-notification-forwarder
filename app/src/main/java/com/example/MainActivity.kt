package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

class MainActivity : ComponentActivity() {

    private val isServiceEnabledState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.initChannels(this)
        NotificationStateHolder.init(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainScreen(
                        isServiceEnabled = isServiceEnabledState.value,
                        onOpenNotificationSettings = { openNotificationSettings(this) },
                        onOpenAppDetails = { openAppDetailsSettings(this) },
                        onOpenManageApps = { openManageAppsSettings(this) },
                        onOpenBatterySettings = { openBatteryOptimizationSettings(this) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isServiceEnabledState.value = isNotificationServiceEnabled(this)
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == pkgName) {
                    return true
                }
            }
        }
        return false
    }

    private fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun openManageAppsSettings(context: Context) {
        try {
            // First try to open Infinix Phone Master if available
            val pmIntent = context.packageManager.getLaunchIntentForPackage("com.transsion.phonemanager")
                ?: context.packageManager.getLaunchIntentForPackage("com.transsion.phonemaster")
            if (pmIntent != null) {
                pmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(pmIntent)
                return
            }
        } catch (e: Exception) {
            // fallback
        }

        try {
            val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                openAppDetailsSettings(context)
            }
        }
    }

    private fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                openAppDetailsSettings(context)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isServiceEnabled: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onOpenManageApps: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe StateHolder flows
    val webhookUrl by NotificationStateHolder.webhookUrl.collectAsState()
    val filterMode by NotificationStateHolder.filterMode.collectAsState()
    val customPackage by NotificationStateHolder.customPackage.collectAsState()
    val blockPromos by NotificationStateHolder.blockPromos.collectAsState()
    val requireAmount by NotificationStateHolder.requireAmount.collectAsState()
    val logs by NotificationStateHolder.recentLogs.collectAsState()

    var inputUrl by remember { mutableStateOf(webhookUrl) }
    var inputCustomPkg by remember { mutableStateOf(customPackage) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var showRestrictedSettingsDialog by remember { mutableStateOf(false) }

    val postNotificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            NotificationHelper.sendTestNotification(context)
            Toast.makeText(context, "Izin notifikasi aktif! Notifikasi tes berhasil dikirim.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Izin notifikasi belum diizinkan.", Toast.LENGTH_SHORT).show()
        }
    }

    val onRequestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val sent = NotificationHelper.sendTestNotification(context)
                if (sent) {
                    Toast.makeText(context, "Notifikasi tes dikirim! Periksa bilah notifikasi HP.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Gagal mengirim notifikasi tes.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val sent = NotificationHelper.sendTestNotification(context)
            if (sent) {
                Toast.makeText(context, "Notifikasi tes dikirim! Periksa bilah notifikasi HP.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Sync input field states when saved flow values update
    LaunchedEffect(webhookUrl) {
        inputUrl = webhookUrl
    }
    LaunchedEffect(customPackage) {
        inputCustomPkg = customPackage
    }

    // Auto-request notification permission on launch so Android registers the channel
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                NotificationHelper.sendTestNotification(context)
            }
        } else {
            NotificationHelper.sendTestNotification(context)
        }
    }

    if (showRestrictedSettingsDialog) {
        RestrictedSettingsDialog(
            onDismiss = { showRestrictedSettingsDialog = false },
            onOpenAppDetails = onOpenAppDetails,
            onOpenManageApps = onOpenManageApps,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onOpenBatterySettings = onOpenBatterySettings,
            onRequestNotificationPermission = onRequestNotificationPermission
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Bayarin Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Bayarin",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Penerus QRIS DANA & GoPay",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = {
                    NotificationStateHolder.clearLogs()
                    Toast.makeText(context, "Log berhasil dihapus", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Logs",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Listener Switch Status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServiceEnabled) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (isServiceEnabled) Color(0xFF4CAF50) else Color(0xFFF44336))
                                    )
                                    Text(
                                        text = if (isServiceEnabled) "Listener Aktif" else "Listener Belum Aktif",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isServiceEnabled) {
                                        "Bayarin siap memantau & meneruskan notifikasi QRIS DANA & GoPay ke server."
                                    } else {
                                        "Izin akses belum aktif. Berikan Akses Notifikasi agar Bayarin dapat membaca transaksi QRIS."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isServiceEnabled,
                                onCheckedChange = {
                                    onOpenNotificationSettings()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onOpenNotificationSettings,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Akses Notifikasi",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Akses Notifikasi", fontSize = 12.sp, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = onOpenAppDetails,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = "Info Aplikasi",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Info Aplikasi", fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Section 1b: Dedicated Card for Infinix (XOS) & Android 13/14 Troubleshooting
            item {
                InfinixAndPermissionsHelpCard(
                    isServiceEnabled = isServiceEnabled,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenBatterySettings = onOpenBatterySettings,
                    onOpenAppDetails = onOpenAppDetails,
                    onOpenManageApps = onOpenManageApps,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenFullGuide = { showRestrictedSettingsDialog = true }
                )
            }

            // Section 2: Webhook Configuration
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Konfigurasi Webhook",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            label = { Text("Webhook URL") },
                            placeholder = { Text("https://yourserver.com/webhook") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = "URL link icon"
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val validationError = validateWebhookUrl(inputUrl)
                                    if (validationError != null) {
                                        Toast.makeText(context, validationError, Toast.LENGTH_LONG).show()
                                    } else {
                                        NotificationStateHolder.saveWebhookUrl(inputUrl.trim())
                                        Toast.makeText(context, "URL Webhook berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Save Icon",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simpan URL", fontSize = 13.sp)
                            }

                            FilledTonalButton(
                                onClick = {
                                    val currentUrl = inputUrl.trim()
                                    val validationError = validateWebhookUrl(currentUrl)
                                    if (validationError != null) {
                                        Toast.makeText(context, validationError, Toast.LENGTH_LONG).show()
                                        return@FilledTonalButton
                                    }
                                    if (currentUrl.isEmpty()) {
                                        Toast.makeText(context, "Masukkan URL terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                        return@FilledTonalButton
                                    }
                                    isTestingConnection = true
                                    coroutineScope.launch {
                                        val result = testWebhookConnection(currentUrl)
                                        isTestingConnection = false
                                        if (result.success) {
                                            Toast.makeText(context, "Uji coba berhasil! ${result.message}", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Gagal: ${result.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = !isTestingConnection,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Test Icon",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Uji Kirim", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Section 3: Filter Settings
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Filter Sumber Pembayaran",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Option 1: Merchant GoPay, DANA & OVO
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = filterMode == "merchant" || filterMode == "qris" || filterMode == "gopay",
                                    onClick = { NotificationStateHolder.saveFilterMode("merchant") }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Merchant: GoPay Merchant, DANA & OVO",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Hanya pembayaran/QRIS. Transfer pribadi diabaikan (disarankan)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Option 2: All QRIS E-Wallets
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = filterMode == "all_qris",
                                    onClick = { NotificationStateHolder.saveFilterMode("all_qris") }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Semua E-Wallet QRIS",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "DANA, GoPay, OVO, ShopeePay, LinkAja, Bank",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Option 3: All Apps
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = filterMode == "all",
                                    onClick = { NotificationStateHolder.saveFilterMode("all") }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Semua Notifikasi Aplikasi",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                            // Option 4: Custom
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = filterMode == "custom",
                                    onClick = { NotificationStateHolder.saveFilterMode("custom") }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Kustom Paket Aplikasi",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        if (filterMode == "custom") {
                            OutlinedTextField(
                                value = inputCustomPkg,
                                onValueChange = {
                                    inputCustomPkg = it
                                    NotificationStateHolder.saveCustomPackage(it.trim())
                                },
                                label = { Text("Package Name") },
                                placeholder = { Text("id.dana atau com.example.app") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Section 3.5: Anti-Spam & Payment Validation Settings
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Anti-Spam",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Perlindungan Anti-Spam DANA & QRIS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Switch 1: Block Promos & Ads
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Blokir Notifikasi Promo & Iklan DANA",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Abaikan otomatis DANA Kaget, voucher diskon, promo cashback, & misi agar tidak spam ke server webhook.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = blockPromos,
                                onCheckedChange = { NotificationStateHolder.saveBlockPromos(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Switch 2: Require Amount
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Wajib Memiliki Nominal Uang (Rp)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Hanya teruskan jika terdapat nominal uang transaksi (menghindari error server 'Notifikasi diabaikan').",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = requireAmount,
                                onCheckedChange = { NotificationStateHolder.saveRequireAmount(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Simulation Test Buttons
                        Text(
                            text = "Uji Coba Filter Anti-Spam:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val testEval = NotificationForwarderService.evaluateFilter(
                                        packageName = "id.dana",
                                        title = "DANA Bisnis",
                                        text = "Pembayaran QRIS Rp 50.000 berhasil diterima dari Pelanggan",
                                        subText = "",
                                        mode = filterMode,
                                        customPackage = customPackage,
                                        blockPromos = blockPromos,
                                        requireAmount = requireAmount,
                                        amount = 50000L,
                                        provider = "DANA"
                                    )
                                    if (testEval.allowed) {
                                        Toast.makeText(context, "✅ Lolos: Notifikasi pembayaran QRIS sah akan diteruskan!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "❌ Ditolak: ${testEval.reason}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text("Simulasi QRIS Sah", fontSize = 11.sp, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    val testEval = NotificationForwarderService.evaluateFilter(
                                        packageName = "id.dana",
                                        title = "DANA",
                                        text = "Promo Serbu Cashback 50% hingga Rp 15.000 menunggumu hari ini!",
                                        subText = "",
                                        mode = filterMode,
                                        customPackage = customPackage,
                                        blockPromos = blockPromos,
                                        requireAmount = requireAmount,
                                        amount = 15000L,
                                        provider = "DANA"
                                    )
                                    if (!testEval.allowed) {
                                        Toast.makeText(context, "🛡️ Dicegah! ${testEval.reason} (Server aman dari spam)", Toast.LENGTH_LONG).show()
                                        NotificationStateHolder.addLog(
                                            ForwardLog(
                                                id = "test_promo_${System.currentTimeMillis()}",
                                                timestamp = System.currentTimeMillis(),
                                                packageName = "[DANA] id.dana",
                                                title = "DANA (Promo)",
                                                text = "Promo Serbu Cashback 50%...\n🛡️ ${testEval.reason}",
                                                status = "BLOCKED",
                                                error = testEval.reason
                                            )
                                        )
                                    } else {
                                        Toast.makeText(context, "⚠️ Tidak terblokir: cek pengaturan", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text("Simulasi Promo DANA", fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Section 4: Log Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Riwayat Pengiriman",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${logs.size} log",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Section 5: List of Logs
            if (logs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = "Empty Log",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Belum ada riwayat pengiriman.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(logs, key = { it.id }) { log ->
                    LogCard(log = log)
                }
            }
        }
    }
}

@Composable
fun LogCard(log: ForwardLog) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeString = remember(log.timestamp) { formatter.format(Date(log.timestamp)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = log.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Tag
                val (bgColor, textColor, label) = when (log.status) {
                    "SUCCESS" -> Triple(
                        Color(0xFFE8F5E9),
                        Color(0xFF2E7D32),
                        "SUCCESS ${log.responseCode ?: ""}"
                    )
                    "FAILED" -> Triple(
                        Color(0xFFFFEBEE),
                        Color(0xFFC62828),
                        "FAILED"
                    )
                    "BLOCKED" -> Triple(
                        Color(0xFFFFF3E0),
                        Color(0xFFE65100),
                        "DITOLAK (ANTI-SPAM)"
                    )
                    else -> Triple(
                        Color(0xFFE3F2FD),
                        Color(0xFF1565C0),
                        "SENDING"
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = label.trim(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (log.title.isNotEmpty()) {
                    Text(
                        text = log.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (log.text.isNotEmpty()) {
                    Text(
                        text = log.text,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (log.error != null) {
                val isBlocked = log.status == "BLOCKED"
                Text(
                    text = if (isBlocked) "Alasan: ${log.error}" else "Error: ${log.error}",
                    fontSize = 11.sp,
                    color = if (isBlocked) Color(0xFFE65100) else Color(0xFFC62828),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isBlocked) Color(0xFFFFF3E0) else Color(0xFFFFEBEE).copy(alpha = 0.5f))
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

data class WebhookTestResult(
    val success: Boolean,
    val statusCode: Int? = null,
    val message: String
)

/**
 * Validasi URL webhook sebelum disimpan/diuji.
 * Hanya HTTPS yang diizinkan: data notifikasi transaksi dikirim sebagai
 * JSON polos, sehingga koneksi tanpa TLS tidak aman (dan diblokir OS).
 *
 * @return pesan kesalahan, atau null bila URL valid.
 */
internal fun validateWebhookUrl(rawUrl: String): String? {
    val url = rawUrl.trim()
    if (url.isEmpty()) return "Masukkan URL webhook terlebih dahulu."
    val uri = try {
        java.net.URI(url)
    } catch (e: Exception) {
        return "Format URL tidak valid."
    }
    if (!uri.scheme.equals("https", ignoreCase = true)) {
        return "URL harus diawali https:// — koneksi HTTP tidak diizinkan."
    }
    if (uri.host.isNullOrBlank()) return "Host pada URL tidak valid."
    return null
}

// Helper to test Webhook connection
private suspend fun testWebhookConnection(url: String): WebhookTestResult {
    return withContext(Dispatchers.IO) {
        val testId = "test_connection_${System.currentTimeMillis()}"
        val sampleText = "Pembayaran QRIS Rp 10.000 berhasil diterima dari Pelanggan"

        // Log SENDING immediately so user sees it in Real-time Activity Log
        NotificationStateHolder.addLog(
            ForwardLog(
                id = testId,
                timestamp = System.currentTimeMillis(),
                packageName = "[UJI COBA] Webhook Test",
                title = "Uji Coba Bayarin (QRIS DANA & GoPay)",
                text = sampleText,
                status = "SENDING"
            )
        )

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val payload = JSONObject().apply {
                put("app", "Bayarin")
                put("id", "test_id")
                put("uniqueId", testId)
                put("provider", "DANA")
                put("packageName", "id.dana")
                put("title", "Koneksi Uji Coba Bayarin")
                put("text", sampleText)
                put("message", sampleText)
                put("rawText", "Uji Coba QRIS: $sampleText")
                put("amount", 10000)
                put("nominal", 10000)
                put("subText", "Uji Coba QRIS")
                put("postTime", System.currentTimeMillis())
                put("timestamp", System.currentTimeMillis())
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", "Bayarin-Android/${BuildConfig.VERSION_NAME}")
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()?.take(250) ?: ""
                val isSuccess = response.isSuccessful

                NotificationStateHolder.addLog(
                    ForwardLog(
                        id = testId,
                        timestamp = System.currentTimeMillis(),
                        packageName = "[UJI COBA] Webhook Test",
                        title = "Uji Coba Bayarin (QRIS DANA & GoPay)",
                        text = if (responseBody.isNotEmpty()) "Respon: $responseBody" else sampleText,
                        status = if (isSuccess) "SUCCESS" else "FAILED",
                        responseCode = response.code,
                        error = if (!isSuccess) "HTTP ${response.code}: $responseBody" else null
                    )
                )

                if (isSuccess) {
                    val preview = if (responseBody.isNotEmpty()) " - $responseBody" else ""
                    WebhookTestResult(true, response.code, "HTTP ${response.code}$preview")
                } else {
                    WebhookTestResult(false, response.code, "HTTP ${response.code}: $responseBody")
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.message ?: "Koneksi gagal"
            NotificationStateHolder.addLog(
                ForwardLog(
                    id = testId,
                    timestamp = System.currentTimeMillis(),
                    packageName = "[UJI COBA] Webhook Test",
                    title = "Uji Coba Bayarin",
                    text = "Gagal menghubungi URL webhook",
                    status = "FAILED",
                    error = errorMsg
                )
            )
            WebhookTestResult(false, null, errorMsg)
        }
    }
}

@Composable
fun InfinixAndPermissionsHelpCard(
    isServiceEnabled: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onOpenManageApps: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenFullGuide: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E262C)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4FC3F7).copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = "Phone Help",
                    tint = Color(0xFF4FC3F7),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Bantuan Khusus HP Infinix (XOS) & Android 13/14",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFFE1F5FE)
                )
            }

            // Sub-item 1: Setelan Terbatas Alert (Top priority when service is not enabled)
            if (!isServiceEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF281C08)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Shield",
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Kenapa Keterangan Masih 'Akses Terbatas'?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFFFE082)
                            )
                        }

                        Text(
                            text = "Android 13/14 memblokir sakelar secara otomatis untuk aplikasi APK. Sakelar TIDAK BISA dinyalakan sebelum Anda membuka kuncinya di Info Aplikasi:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFECB3),
                            lineHeight = 16.sp
                        )

                        // 4 Steps directly visible
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1505))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "1. PANCING DULU: Ketuk '1. Akses Notifikasi', coba ketuk sakelar Bayarin sampai muncul pesan 'Setelan Terbatas', lalu klik OK.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFFD54F)
                                )
                                Text(
                                    text = "2. PENTING DI INFINIX: Keluar ke Layar Utama HP, TAHAN LAMA ikon aplikasi Bayarin > pilih tanda (i) Detail Aplikasi.\n(Jika buka dari tombol aplikasi, sistem Infinix sering menyembunyikan titik 3).",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFFE082)
                                )
                                Text(
                                    text = "3. BUKA KUNCI:\n• Jika ada titik 3 (⋮) di pojok kanan atas: ketuk & pilih 'Izinkan setelan terbatas'.\n• JIKA TIDAK ADA TITIK 3: Gulir ke bagian PALING BAWAH layar Info Aplikasi, cari tombol 'Izinkan pengaturan terbatas'.\n• Masukkan PIN/Pola/Sidik Jari HP Anda.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFFD54F)
                                )
                                Text(
                                    text = "4. SUKSES: Buka kembali Akses Notifikasi, sakelar sekarang SUDAH BISA DINYALAKAN (ON)!",
                                    fontSize = 11.sp,
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = onOpenNotificationSettings,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFB74D),
                                    contentColor = Color(0xFF261900)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("1. Akses Notifikasi", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onOpenAppDetails,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("2. Info Aplikasi", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        FilledTonalButton(
                            onClick = onOpenManageApps,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF3E2C0B),
                                contentColor = Color(0xFFFFD54F)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Manage Apps",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buka Pengaturan Aplikasi HP (Phone Master / Daftar Aplikasi)", fontSize = 11.sp, maxLines = 1)
                        }

                        // ADB/Shizuku Quick Copy
                        OutlinedButton(
                            onClick = {
                                val adbCmd = "adb shell appops set ${context.packageName} ACCESS_RESTRICTED_SETTINGS allow && adb shell cmd notification allow_listener ${context.packageName}/com.example.NotificationForwarderService"
                                clipboardManager.setText(AnnotatedString(adbCmd))
                                Toast.makeText(context, "Perintah ADB / Shizuku berhasil disalin!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy ADB",
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFFFFD54F)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salin Kode ADB / Shizuku (Bagi yang Pakai PC/Shizuku)", fontSize = 10.sp, color = Color(0xFFFFD54F))
                        }
                    }
                }
            }

            // Sub-item 2: "Aplikasi ini tidak mengirimkan notif"
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161E23))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Tertulis 'Aplikasi ini tidak mengirimkan notifikasi'?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF81D4FA)
                    )
                    Text(
                        text = "Di Android 13/14, izin notifikasi biasa harus dipicu sekali agar sistem mendaftarkan saluran notifikasi aplikasi ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0BEC5),
                        lineHeight = 16.sp
                    )
                    Button(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0288D1),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Send Test",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aktifkan Izin & Kirim Notifikasi Tes", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Sub-item 3: Background battery / Listener mati sendiri di Infinix
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161E23))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Agar Listener Tetap Aktif di Infinix (Tidak Mati Sendiri):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF81D4FA)
                    )
                    Text(
                        text = "HP Infinix (XOS) sangat agresif mematikan aplikasi latar belakang. Ubah pengaturan baterai ke 'Tidak Dibatasi / Jangan Optimalkan' agar listener selalu siaga.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0BEC5),
                        lineHeight = 16.sp
                    )
                    FilledTonalButton(
                        onClick = onOpenBatterySettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF263238),
                            contentColor = Color(0xFF81D4FA)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = "Battery Settings",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Matikan Pengoptimalan Baterai", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            TextButton(
                onClick = onOpenFullGuide,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "Help Icon",
                    tint = Color(0xFF81D4FA),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Buka Panduan Lengkap Titik Tiga (⋮) & Infinix XOS",
                    fontSize = 12.sp,
                    color = Color(0xFF81D4FA),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun RestrictedSettingsHelpCard(
    onOpenAppDetails: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenFullGuide: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C2416)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Shield Alert",
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Muncul 'Setelan Terbatas' / Blokir?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFFFFE082)
                )
            }

            Text(
                text = "Pada Android 13, 14, & 15, aplikasi yang diinstal ulang secara otomatis dibatasi sistem demi keamanan saat mengaktifkan listener. Anda perlu mengizinkannya sekali di halaman Info Aplikasi:",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFECB3),
                lineHeight = 18.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenAppDetails,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB74D),
                        contentColor = Color(0xFF261900)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Info App",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("1. Info Aplikasi", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                FilledTonalButton(
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF4E3708),
                        contentColor = Color(0xFFFFE082)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Notif Access",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("2. Akses Notif", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            TextButton(
                onClick = onOpenFullGuide,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "Help Icon",
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Lihat Panduan Lengkap Titik Tiga (⋮)",
                    fontSize = 12.sp,
                    color = Color(0xFFFFD54F),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun RestrictedSettingsDialog(
    onDismiss: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onOpenManageApps: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Security Guide",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Panduan HP Infinix & Android 13/14",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Solusi Tuntas Setelan Terbatas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Dialog",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Steps
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StepItemCard(
                        step = "1",
                        title = "Pancing Pembatasan di Akses Notifikasi",
                        description = "Buka 'Akses Notifikasi' lalu coba ketuk sakelar Bayarin hingga muncul pop-up 'Setelan Terbatas'. Ketuk OK. Langkah ini wajib dilakukan agar sistem Infinix mencatat bahwa Anda ingin memberikan izin ini."
                    )

                    StepItemCard(
                        step = "2",
                        title = "PENTING: Buka Info Aplikasi dari Layar Utama HP",
                        description = "Jangan buka dari tombol di dalam aplikasi jika titik 3 di HP Anda tidak muncul! Keluar ke Layar Utama HP Infinix Anda, TEKAN DAN TAHAN ikon Bayarin sampai muncul menu kecil, lalu ketuk tanda (i) (Detail Aplikasi / Info Aplikasi).\nAtau buka: Pengaturan HP > Manajemen Aplikasi > Daftar Aplikasi > Bayarin."
                    )

                    StepItemCard(
                        step = "3",
                        title = "Buka Blokir (Titik 3 ATAU Scroll ke Paling Bawah)",
                        description = "Di layar Info Aplikasi:\n• Jika ada titik 3 (⋮) di pojok kanan atas: ketuk dan pilih 'Izinkan setelan terbatas' (Allow restricted settings).\n• JIKA TIDAK ADA TITIK 3 (Khusus Infinix XOS): Gulir layar Info Aplikasi sampai ke bagian PALING BAWAH, lalu ketuk 'Izinkan pengaturan terbatas'!\n• Masukkan PIN / Pola / Sidik Jari HP Anda untuk verifikasi keamanan."
                    )

                    StepItemCard(
                        step = "4",
                        title = "Nyalakan Sakelar di Akses Notifikasi",
                        description = "Kembali ke aplikasi Bayarin dan ketuk 'Buka Akses Notifikasi'. Sekarang sakelar 'Bayarin Notification Forwarder' sudah TIDAK ABU-ABU lagi dan bisa digeser ke posisi ON!"
                    )

                    StepItemCard(
                        step = "5",
                        title = "Izin Notifikasi Biasa ('Aplikasi Tidak Mengirimkan Notif')",
                        description = "Jika di menu notifikasi HP tertulis 'aplikasi ini tidak mengirimkan notifikasi', ketuk tombol 'Izin & Tes Notif' di bawah untuk mengaktifkan notifikasi dan mengirim pesan tes."
                    )

                    StepItemCard(
                        step = "6",
                        title = "Agar Listener Tidak Mati Sendiri di Infinix (XOS)",
                        description = "1. Di Info Aplikasi Bayarin > Penggunaan Baterai: pilih 'Tidak Dibatasi / Unrestricted'.\n2. Buka aplikasi bawaan Infinix 'Phone Master' > Manajemen Mulai Otomatis (Auto-start) > Izinkan Bayarin.\n3. Di layar Recent Apps HP Anda, kunci/gembok aplikasi Bayarin."
                    )

                    // ADB / Shizuku option card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Alternatif Cepat bagi Pengguna PC / Shizuku / ADB",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Jika Anda menghubungkan HP ke PC via ADB atau memakai Shizuku, Anda bisa mengaktifkan izin secara instan tanpa lewat menu pengaturan:",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    val adbCmd = "adb shell appops set ${context.packageName} ACCESS_RESTRICTED_SETTINGS allow && adb shell cmd notification allow_listener ${context.packageName}/com.example.NotificationForwarderService"
                                    clipboardManager.setText(AnnotatedString(adbCmd))
                                    Toast.makeText(context, "Perintah ADB / Shizuku berhasil disalin!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salin Kode ADB / Shizuku", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onRequestNotificationPermission()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Test Notif",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Izin & Tes Notif", fontSize = 11.sp, maxLines = 1)
                        }

                        FilledTonalButton(
                            onClick = {
                                onOpenBatterySettings()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = "Baterai",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Atur Baterai", fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenAppDetails()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Text("1. Info Aplikasi (Titik 3)", fontSize = 11.sp, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenNotificationSettings()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Text("2. Akses Notifikasi", fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onOpenManageApps()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("3. Manajemen Aplikasi (Phone Master / Pengaturan)", fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun StepItemCard(
    step: String,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = step,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}


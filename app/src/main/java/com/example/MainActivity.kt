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

class MainActivity : ComponentActivity() {

    private val isServiceEnabledState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationStateHolder.init(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainScreen(
                        isServiceEnabled = isServiceEnabledState.value,
                        onToggleAccess = { openNotificationSettings(this) },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isServiceEnabled: Boolean,
    onToggleAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe StateHolder flows
    val webhookUrl by NotificationStateHolder.webhookUrl.collectAsState()
    val filterMode by NotificationStateHolder.filterMode.collectAsState()
    val customPackage by NotificationStateHolder.customPackage.collectAsState()
    val logs by NotificationStateHolder.recentLogs.collectAsState()

    var inputUrl by remember { mutableStateOf(webhookUrl) }
    var inputCustomPkg by remember { mutableStateOf(customPackage) }
    var isTestingConnection by remember { mutableStateOf(false) }

    // Sync input field states when saved flow values update
    LaunchedEffect(webhookUrl) {
        inputUrl = webhookUrl
    }
    LaunchedEffect(customPackage) {
        inputCustomPkg = customPackage
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SendAndArchive,
                        contentDescription = "Forwarder Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "GoPay Forwarder",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                                    text = if (isServiceEnabled) "Listener Aktif" else "Listener Tidak Aktif",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isServiceEnabled) "Aplikasi siap meneruskan notifikasi." else "Ketuk tombol untuk memberikan izin akses notifikasi.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isServiceEnabled,
                            onCheckedChange = { onToggleAccess() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
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
                                    NotificationStateHolder.saveWebhookUrl(inputUrl.trim())
                                    Toast.makeText(context, "URL Webhook berhasil disimpan!", Toast.LENGTH_SHORT).show()
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
                                    if (currentUrl.isEmpty()) {
                                        Toast.makeText(context, "Masukkan URL terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                        return@FilledTonalButton
                                    }
                                    isTestingConnection = true
                                    coroutineScope.launch {
                                        val success = testWebhookConnection(currentUrl)
                                        isTestingConnection = false
                                        if (success) {
                                            Toast.makeText(context, "Uji coba sukses! Webhook merespon.", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Koneksi gagal. Silakan periksa URL Anda.", Toast.LENGTH_LONG).show()
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
                            text = "Filter Aplikasi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Option 1: GoPay & GoBiz
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = filterMode == "gopay",
                                    onClick = { NotificationStateHolder.saveFilterMode("gopay") }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Hanya GoPay Merchant & GoBiz",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                            // Option 2: All Apps
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
                                    text = "Semua Aplikasi",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                            // Option 3: Custom
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
                                placeholder = { Text("com.example.app") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
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
                Text(
                    text = "Error: ${log.error}",
                    fontSize = 11.sp,
                    color = Color(0xFFC62828),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFEBEE).copy(alpha = 0.5f))
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

// Helper to test Webhook connection
private suspend fun testWebhookConnection(url: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val payload = JSONObject().apply {
                put("id", "test_id")
                put("uniqueId", "test_connection_id_${System.currentTimeMillis()}")
                put("packageName", "com.aistudio.gopayforwarder")
                put("title", "Koneksi Uji Coba")
                put("text", "Koneksi berhasil terhubung dari aplikasi GoPay Forwarder.")
                put("subText", "Uji Coba")
                put("postTime", System.currentTimeMillis())
                put("timestamp", System.currentTimeMillis())
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

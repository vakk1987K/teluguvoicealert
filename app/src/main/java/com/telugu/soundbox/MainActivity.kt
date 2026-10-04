package com.telugu.soundbox

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

enum class TimePeriod {
    TODAY, SEVEN_DAYS, FIFTEEN_DAYS, THIRTY_DAYS
}

class MainActivity : ComponentActivity() {

    private val paymentListState = mutableStateListOf<StoredPayment>()
    private val summaryState = mutableStateOf(PaymentSummary(0.0, 0))
    private val activeLanguageState = mutableStateOf(SoundboxLanguage.TELUGU)

    private val paymentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SmsBroadcastReceiver.ACTION_NEW_PAYMENT) {
                refreshData()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        activeLanguageState.value = LanguageManager.getLanguage(this)
        TeluguTtsManager.init(this)
        SoundboxForegroundService.startService(this)
        AdMobManager.initialize(applicationContext)

        val filter = IntentFilter(SmsBroadcastReceiver.ACTION_NEW_PAYMENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(paymentReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(paymentReceiver, filter)
        }

        refreshData()

        setContent {
            SoundboxApp(
                payments = paymentListState,
                summary = summaryState.value,
                currentLanguage = activeLanguageState.value,
                onLanguageChanged = { lang ->
                    LanguageManager.saveLanguage(this, lang)
                    activeLanguageState.value = lang
                    TeluguTtsManager.switchLanguage(lang)
                    TeluguTtsManager.speakAmount("100", lang)
                },
                onTestSpeech = { amount ->
                    TeluguTtsManager.speakAmount(amount, activeLanguageState.value)
                },
                onSimulateNotification = { text, provider ->
                    PaymentParser.parsePayment(text, provider)?.let { result ->
                        val stored = StoredPayment(
                            id = System.currentTimeMillis().toString(),
                            amount = result.amount,
                            formattedAmount = result.formattedAmount,
                            bank = result.provider,
                            timestamp = System.currentTimeMillis(),
                            fullSms = text
                        )
                        PaymentStorage.savePayment(this, stored)
                        TeluguTtsManager.speakAmount(result.formattedAmount, activeLanguageState.value)
                        refreshData()
                    }
                },
                onClearAll = {
                    PaymentStorage.clearPayments(this)
                    refreshData()
                },
                onRequestBatteryOptimization = {
                    requestIgnoreBatteryOptimization()
                },
                onRequestNotificationAccess = {
                    requestNotificationAccessPermission()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(paymentReceiver)
        } catch (_: Exception) {}
        TeluguTtsManager.shutdown()
    }

    private fun refreshData() {
        val list = PaymentStorage.getPayments(this)
        paymentListState.clear()
        paymentListState.addAll(list)
        summaryState.value = PaymentStorage.calculateSummary(list)
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun requestNotificationAccessPermission() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to open notification settings: ${e.message}")
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val cn = ComponentName(context, PaymentNotificationListenerService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundboxApp(
    payments: List<StoredPayment>,
    summary: PaymentSummary,
    currentLanguage: SoundboxLanguage,
    onLanguageChanged: (SoundboxLanguage) -> Unit,
    onTestSpeech: (amount: String) -> Unit,
    onSimulateNotification: (text: String, provider: String) -> Unit,
    onClearAll: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestNotificationAccess: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isNotificationAccessGranted by remember {
        mutableStateOf(
            try {
                val cn = ComponentName(context, PaymentNotificationListenerService::class.java)
                val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                flat != null && flat.contains(cn.flattenToString())
            } catch (_: Exception) { false }
        )
    }

    var selectedPeriod by remember { mutableStateOf(TimePeriod.TODAY) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isBatteryOptIgnored by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasSmsPermission = perms[Manifest.permission.RECEIVE_SMS] == true
        if (hasSmsPermission) {
            SoundboxForegroundService.startService(context)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val filteredSummary = remember(payments, selectedPeriod) {
        val now = System.currentTimeMillis()
        val cutoff = when (selectedPeriod) {
            TimePeriod.TODAY -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.timeInMillis
            }
            TimePeriod.SEVEN_DAYS -> now - (7L * 24 * 60 * 60 * 1000)
            TimePeriod.FIFTEEN_DAYS -> now - (15L * 24 * 60 * 60 * 1000)
            TimePeriod.THIRTY_DAYS -> now - (30L * 24 * 60 * 60 * 1000)
        }
        val subset = payments.filter { it.timestamp >= cutoff }
        val sum = subset.sumOf { it.amount }
        Pair(sum, subset.size)
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All Transactions?", color = Color.White) },
            text = { Text("This will reset your transaction log and totals.", color = Color(0xFF94A3B8)) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Clear All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Privacy Policy Dialog (For 100% Google Play Store Compliance)
    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Privacy Policy",
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Privacy Policy",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "100% On-Device Local Processing",
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "UPI Sound Box: Payment Alert does NOT collect, upload, share, or sell any personal or financial data. All SMS and notification parsing happens strictly on your physical device.",
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "No Cloud Storage & Zero Server Transmission",
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your bank transactions, customer phone numbers, and payment details are never transmitted to external servers. Payment totals are stored locally in device private memory and can be cleared at any time.",
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Permissions Used & Purpose",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Notification Access: Detects UPI payment amounts from PhonePe, GPay, Paytm, etc., to speak voice alerts.\n• SMS Permissions: Reads incoming bank credit alerts to announce received payments.\n• Foreground Service: Keeps voice active when your phone screen is locked.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Developer Contact",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Email: kvakk1986@gmail.com\nCompliant with Indian DPDP Act 2023 & Google Play Policies.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            try {
                                val browserIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://sites.google.com/view/privacypolicy-upisoundbox/home")
                                )
                                context.startActivity(browserIntent)
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🌐 View Web Policy", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showPrivacyPolicyDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Close", color = Color.White, fontSize = 12.sp)
                    }
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "UPI Sound Box",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0F2E3A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
                            modifier = Modifier.clickable { showPrivacyPolicyDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Privacy Policy",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Policy", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = "Payment Alert • ${currentLanguage.bannerSubtitle}",
                        color = Color(0xFF10B981),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (hasSmsPermission) Color(0xFF064E3B) else Color(0xFF7C2D12),
                        modifier = Modifier.clickable {
                            val permsToRequest = mutableListOf(
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.READ_SMS
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(permsToRequest.toTypedArray())
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (hasSmsPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasSmsPermission) Color(0xFF34D399) else Color(0xFFF87171),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (hasSmsPermission) "SMS OK" else "Enable SMS",
                                color = if (hasSmsPermission) Color(0xFFA7F3D0) else Color(0xFFFECACA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isNotificationAccessGranted) Color(0xFF064E3B) else Color(0xFF7C2D12),
                        modifier = Modifier.clickable {
                            onRequestNotificationAccess()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = if (isNotificationAccessGranted) Color(0xFF34D399) else Color(0xFFF87171),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isNotificationAccessGranted) "Apps OK" else "GPay/PhonePe",
                                color = if (isNotificationAccessGranted) Color(0xFFA7F3D0) else Color(0xFFFECACA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!isBatteryOptIgnored) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF7C2D12),
                            modifier = Modifier.clickable { onRequestBatteryOptimization() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BatteryChargingFull,
                                    contentDescription = null,
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Battery",
                                    color = Color(0xFFFECACA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Language Selector Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Language",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )

                SoundboxLanguage.values().forEach { lang ->
                    val isSelected = currentLanguage == lang
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF10B981) else Color(0xFF1E293B),
                        modifier = Modifier.clickable { onLanguageChanged(lang) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${lang.nativeLabel} (${lang.displayName})",
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Period Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PeriodTab(
                    label = "Today",
                    isSelected = selectedPeriod == TimePeriod.TODAY,
                    onClick = { selectedPeriod = TimePeriod.TODAY }
                )
                PeriodTab(
                    label = "7 Days",
                    isSelected = selectedPeriod == TimePeriod.SEVEN_DAYS,
                    onClick = { selectedPeriod = TimePeriod.SEVEN_DAYS }
                )
                PeriodTab(
                    label = "15 Days",
                    isSelected = selectedPeriod == TimePeriod.FIFTEEN_DAYS,
                    onClick = { selectedPeriod = TimePeriod.FIFTEEN_DAYS }
                )
                PeriodTab(
                    label = "30 Days",
                    isSelected = selectedPeriod == TimePeriod.THIRTY_DAYS,
                    onClick = { selectedPeriod = TimePeriod.THIRTY_DAYS }
                )
            }

            // Main Collection Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (selectedPeriod) {
                                TimePeriod.TODAY -> "TODAY (ఈరోజు) RECEIVED"
                                TimePeriod.SEVEN_DAYS -> "LAST 7 DAYS RECEIVED"
                                TimePeriod.FIFTEEN_DAYS -> "LAST 15 DAYS RECEIVED"
                                TimePeriod.THIRTY_DAYS -> "LAST 30 DAYS RECEIVED"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        Text(
                            text = "${filteredSummary.second} Payments",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "₹" + String.format(java.util.Locale.US, "%,.2f", filteredSummary.first),
                        color = Color(0xFF34D399),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Soundbox Speaker Center Disc
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .scale(if (hasSmsPermission || isNotificationAccessGranted) pulseScale else 1.0f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF10B981), Color(0xFF065F46), Color(0xFF022C22))
                            )
                        )
                        .border(2.dp, Color(0xFF34D399), CircleShape)
                        .clickable {
                            onTestSpeech("210")
                        }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speaker",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = "TEST",
                            color = Color(0xFFA7F3D0),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Screen Audio",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Amount-Only Voice Alert: Announces in ${currentLanguage.displayName} on lock screen",
                            color = Color(0xFFE2E8F0),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            // Quick Test Buttons (Amount Only: 10, 50, 100, 500, 1000, 2000, 5000)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { onTestSpeech("10") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+ ₹10", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onTestSpeech("50") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+ ₹50", fontSize = 10.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onTestSpeech("100") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+ ₹100", fontSize = 10.sp, color = Color(0xFFFDE047), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onTestSpeech("500") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+ ₹500", fontSize = 10.sp, color = Color(0xFFF472B6), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onTestSpeech("1000") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+ ₹1,000", fontSize = 10.sp, color = Color(0xFF818CF8), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onTestSpeech("2000") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+ ₹2,000", fontSize = 10.sp, color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onTestSpeech("5000") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+ ₹5,000", fontSize = 10.sp, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                }
            }

            // AdMob Test Ads Controls (Ad #2 Interstitial & Ad #3 Rewarded)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        (context as? Activity)?.let { act ->
                            AdMobManager.showInterstitialAd(act)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 5.dp)
                ) {
                    Text("📺 Test Interstitial Ad", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        (context as? Activity)?.let { act ->
                            AdMobManager.showRewardedAd(act) { reward ->
                                Toast.makeText(context, "🎉 Test Reward Earned! (+$reward)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 5.dp)
                ) {
                    Text("🎁 Test Rewarded Ad", fontSize = 10.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                }
            }

            // Recent Payments List
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT PAYMENTS (${payments.size})",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showPrivacyPolicyDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Privacy Policy",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Privacy",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    IconButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear All",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (payments.isEmpty()) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No payments received yet",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pay using PhonePe, GPay, Paytm or send a bank credit SMS to test instant audio announcements.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 15.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(payments) { item ->
                        val timeStr = java.text.SimpleDateFormat(
                            "dd MMM, hh:mm a",
                            java.util.Locale.getDefault()
                        ).format(java.util.Date(item.timestamp))

                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "₹${item.formattedAmount}",
                                            color = Color(0xFF34D399),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Credited",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = "${item.bank} • $timeStr",
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp
                                    )
                                }

                                IconButton(
                                    onClick = { onTestSpeech(item.formattedAmount) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Replay Voice",
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPrivacyPolicyDialog = true }
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "100% On-Device Privacy • Tap to View Policy",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // AdMob Banner Ad (Test Ad #1)
                Spacer(modifier = Modifier.height(4.dp))
                AdMobBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )
            }
        }
    }
}

@Composable
fun PeriodTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFF10B981) else Color(0xFF0F172A),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

/* -------------------------------------------------------------------------------------------------
 *  AdMob Manager & Banner Composable (All-In-One File)
 * ------------------------------------------------------------------------------------------------- */
object AdMobManager {
    private const val TAG = "AdMobManager"

    // Official Google AdMob Sample Test Ad Unit IDs
    const val TEST_BANNER_AD_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_AD_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_AD_ID = "ca-app-pub-3940256099942544/5224354917"

    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false

    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoading = false

    fun initialize(context: Context) {
        try {
            MobileAds.initialize(context) { status ->
                Log.i(TAG, "Google Mobile Ads initialized: $status")
                loadInterstitialAd(context)
                loadRewardedAd(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MobileAds: ${e.message}")
        }
    }

    fun loadInterstitialAd(context: Context) {
        if (interstitialAd != null || isInterstitialLoading) return
        isInterstitialLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            TEST_INTERSTITIAL_AD_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    Log.i(TAG, "AdMob Interstitial Ad loaded")
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            loadInterstitialAd(context)
                        }
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            interstitialAd = null
                            isInterstitialLoading = false
                        }
                    }
                }
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    Log.w(TAG, "AdMob Interstitial failed: ${loadAdError.message}")
                }
            }
        )
    }

    fun showInterstitialAd(activity: Activity, onDismissed: (() -> Unit)? = null) {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitialAd(activity)
                    onDismissed?.invoke()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    onDismissed?.invoke()
                }
            }
            ad.show(activity)
        } else {
            loadInterstitialAd(activity)
            onDismissed?.invoke()
        }
    }

    fun loadRewardedAd(context: Context) {
        if (rewardedAd != null || isRewardedLoading) return
        isRewardedLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            TEST_REWARDED_AD_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isRewardedLoading = false
                    Log.i(TAG, "AdMob Rewarded Ad loaded")
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            rewardedAd = null
                            loadRewardedAd(context)
                        }
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            rewardedAd = null
                            isRewardedLoading = false
                        }
                    }
                }
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isRewardedLoading = false
                    Log.w(TAG, "AdMob Rewarded failed: ${loadAdError.message}")
                }
            }
        )
    }

    fun showRewardedAd(activity: Activity, onUserEarnedReward: (Int) -> Unit) {
        val ad = rewardedAd
        if (ad != null) {
            ad.show(activity) { rewardItem ->
                onUserEarnedReward(rewardItem.amount)
            }
        } else {
            loadRewardedAd(activity)
        }
    }
}

@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = AdMobManager.TEST_BANNER_AD_ID
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0F19)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    this.adUnitId = adUnitId
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}

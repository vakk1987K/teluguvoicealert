package com.telugu.soundbox

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
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.core.content.ContextCompat

enum class TimePeriod {
    TODAY, SEVEN_DAYS, FIFTEEN_DAYS, THIRTY_DAYS
}

class MainActivity : ComponentActivity() {

    private val paymentListState = mutableStateListOf<StoredPayment>()
    private val summaryState = mutableStateOf(
        PaymentSummary(
            today = PeriodSummary(0.0, 0),
            sevenDays = PeriodSummary(0.0, 0),
            fifteenDays = PeriodSummary(0.0, 0),
            thirtyDays = PeriodSummary(0.0, 0)
        )
    )
    private val activeLanguageState = mutableStateOf(SoundboxLanguage.TELUGU)

    private val paymentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PaymentStorage.ACTION_NEW_PAYMENT) {
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

        val filter = IntentFilter(PaymentStorage.ACTION_NEW_PAYMENT)
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
                    LanguageManager.setLanguage(this, lang)
                    activeLanguageState.value = lang
                    TeluguTtsManager.previewLanguage(this, lang)
                },
                onTestSpeech = { amount ->
                    TeluguTtsManager.announcePayment(this, amount, language = activeLanguageState.value)
                },
                onSimulateNotification = { text, provider ->
                    TransactionParser.parse(text, provider)?.let { result ->
                        PaymentStorage.addPayment(
                            context = this,
                            amount = result.amount,
                            formattedAmount = result.formattedAmount,
                            payerName = result.payerName ?: "",
                            bank = result.bank,
                            rawText = text
                        )
                        TeluguTtsManager.announcePayment(this, result.formattedAmount, language = activeLanguageState.value)
                        refreshData()
                    }
                },
                onClearAll = {
                    PaymentStorage.clearAll(this)
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
    }

    private fun refreshData() {
        val list = PaymentStorage.getPayments(this)
        paymentListState.clear()
        paymentListState.addAll(list)
        summaryState.value = PaymentStorage.getSummary(this)
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
    var isAlertsActive by remember {
        mutableStateOf(PaymentStorage.isAlertsEnabled(context))
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

    // Auto-refresh permission status whenever user returns to app from system settings
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                try {
                    val cn = ComponentName(context, PaymentNotificationListenerService::class.java)
                    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                    isNotificationAccessGranted = flat != null && flat.contains(cn.flattenToString())
                } catch (_: Exception) {}
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                    isBatteryOptIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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

    val currentPeriodSummary = when (selectedPeriod) {
        TimePeriod.TODAY -> summary.today
        TimePeriod.SEVEN_DAYS -> summary.sevenDays
        TimePeriod.FIFTEEN_DAYS -> summary.fifteenDays
        TimePeriod.THIRTY_DAYS -> summary.thirtyDays
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

    // Privacy Policy Dialog
    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "100% On-Device Privacy",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Zero Data Collection Policy",
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
                        text = "• Notification Access: Detects incoming UPI payments from PhonePe, GPay, Paytm, BHIM, and bank notifications to speak voice alerts.\n• Foreground Service: Keeps soundbox voice active even when your phone screen is locked.",
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "UPI Sound Box",
                            color = Color.White,
                            fontSize = 17.sp,
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
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Policy", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = "Payment Alert • ${currentLanguage.bannerSubtitle}",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Unified Alerts Button: Supports Enable (Grant Permission) and Disable / Re-enable Alerts!
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            !isNotificationAccessGranted -> Color(0xFF7C2D12)
                            isAlertsActive -> Color(0xFF064E3B)
                            else -> Color(0xFF334155)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when {
                                !isNotificationAccessGranted -> Color(0xFFEF4444)
                                isAlertsActive -> Color(0xFF10B981)
                                else -> Color(0xFF64748B)
                            }
                        ),
                        modifier = Modifier.clickable {
                            if (!isNotificationAccessGranted) {
                                onRequestNotificationAccess()
                            } else {
                                val nextState = !isAlertsActive
                                isAlertsActive = nextState
                                PaymentStorage.setAlertsEnabled(context, nextState)
                                if (nextState) {
                                    Toast.makeText(context, "🔔 UPI Voice Alerts: ON", Toast.LENGTH_SHORT).show()
                                    onTestSpeech("10")
                                } else {
                                    Toast.makeText(context, "🔕 UPI Voice Alerts: PAUSED", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    !isNotificationAccessGranted -> Icons.Default.Warning
                                    isAlertsActive -> Icons.Default.Notifications
                                    else -> Icons.Default.Notifications
                                },
                                contentDescription = null,
                                tint = when {
                                    !isNotificationAccessGranted -> Color(0xFFF87171)
                                    isAlertsActive -> Color(0xFF34D399)
                                    else -> Color(0xFF94A3B8)
                                },
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = when {
                                    !isNotificationAccessGranted -> "Enable Alerts"
                                    isAlertsActive -> "Alerts: ON"
                                    else -> "Alerts: OFF"
                                },
                                color = when {
                                    !isNotificationAccessGranted -> Color(0xFFFECACA)
                                    isAlertsActive -> Color(0xFFA7F3D0)
                                    else -> Color(0xFFCBD5E1)
                                },
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
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BatteryChargingFull,
                                    contentDescription = "Battery Optimization",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(14.dp)
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
                                text = "${lang.nativeName} (${lang.displayName})",
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
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TimePeriod.values().forEach { period ->
                    val isSelected = selectedPeriod == period
                    val label = when (period) {
                        TimePeriod.TODAY -> "Today"
                        TimePeriod.SEVEN_DAYS -> "7 Days"
                        TimePeriod.FIFTEEN_DAYS -> "15 Days"
                        TimePeriod.THIRTY_DAYS -> "30 Days"
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedPeriod = period },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF10B981) else Color(0xFF1E293B)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    val periodLabel = when (selectedPeriod) {
                        TimePeriod.TODAY -> "TODAY (${currentLanguage.todayLabel})"
                        TimePeriod.SEVEN_DAYS -> "LAST 7 DAYS"
                        TimePeriod.FIFTEEN_DAYS -> "LAST 15 DAYS"
                        TimePeriod.THIRTY_DAYS -> "LAST 30 DAYS"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$periodLabel RECEIVED",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${currentPeriodSummary.totalOrders} Payments",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "₹${String.format("%.2f", currentPeriodSummary.totalAmount)}",
                        color = Color(0xFF34D399),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Big Center Test Voice Button
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(96.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF059669), Color(0xFF047857), Color(0xFF064E3B))
                        )
                    )
                    .border(3.dp, Color(0xFF34D399), CircleShape)
                    .clickable { onTestSpeech("100") },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Test Speech",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = "TEST",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Lock Screen Voice Announcement Notice Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E293B)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Screen",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Amount-Only Voice Alert: Announces in ${currentLanguage.displayName} on lock screen",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Quick Speech Test Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
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

                Button(
                    onClick = { onTestSpeech("7500") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+ ₹7,500", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // AdMob Test Ads Controls
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
                    Text("🎁 Test Rewarded Ad", fontSize = 10.sp, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                }
            }

            // Transactions Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT PAYMENTS (${payments.size})",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            text = "Pay using PhonePe, GPay, Paytm or banking apps to test instant audio announcements.",
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(Color(0xFF0F2E3A), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "₹",
                                            color = Color(0xFF34D399),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (item.payerName.isNotBlank()) item.payerName else item.bank,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = timeStr,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Text(
                                    text = "+ ₹${item.formattedAmount}",
                                    color = Color(0xFF34D399),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Privacy and Safety Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { showPrivacyPolicyDialog = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "100% On-Device Privacy • Tap to View Policy",
                    color = Color(0xFF38BDF8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Real Google AdMob Banner Ad View
            AdMobBannerView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )
        }
    }
}

package com.telugu.soundbox

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
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

data class PaymentItem(
    val amount: String,
    val payer: String,
    val bank: String,
    val time: String,
    val rawText: String
)

class MainActivity : ComponentActivity() {

    private val paymentListState = mutableStateListOf<PaymentItem>()

    private val paymentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SmsBroadcastReceiver.ACTION_NEW_PAYMENT) {
                val amount = intent.getStringExtra(SmsBroadcastReceiver.EXTRA_AMOUNT) ?: "0"
                val payer = intent.getStringExtra(SmsBroadcastReceiver.EXTRA_PAYER) ?: ""
                val bank = intent.getStringExtra(SmsBroadcastReceiver.EXTRA_BANK) ?: "Bank"
                val raw = intent.getStringExtra(SmsBroadcastReceiver.EXTRA_TEXT) ?: ""

                val timeStr = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                paymentListState.add(0, PaymentItem(amount, payer, bank, timeStr, raw))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen awake while in foreground & allow showing over lockscreen
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

        // Initialize Telugu TTS Engine
        TeluguTtsManager.init(this)

        // Start Foreground Service so soundbox stays alive 24/7 on lockscreen
        SoundboxForegroundService.startService(this)

        // Register broadcast receiver for live UI payments feed
        val filter = IntentFilter(SmsBroadcastReceiver.ACTION_NEW_PAYMENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(paymentReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(paymentReceiver, filter)
        }

        setContent {
            SoundboxApp(
                payments = paymentListState,
                onTestSpeech = { amount, payer ->
                    TeluguTtsManager.announcePayment(this, amount, payer)
                },
                onRequestBatteryOptimization = {
                    requestBatteryOptimizationExemption()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        SoundboxForegroundService.startService(this)
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(paymentReceiver)
        } catch (_: Exception) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundboxApp(
    payments: List<PaymentItem>,
    onTestSpeech: (amount: String, payer: String) -> Unit,
    onRequestBatteryOptimization: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

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
            animation = tween(1200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A) // Slate-900
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Telugu Payment Soundbox",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "తెలుగు చెల్లింపు సౌండ్‌బాక్స్",
                        color = Color(0xFF10B981),
                        fontSize = 12.sp
                    )
                }

                // SMS Permission Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                            text = if (hasSmsPermission) "SMS Active" else "Grant SMS",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Lock Screen 24/7 Status Bar
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Screen Audio",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lock Screen Audio: ACTIVE (Speaks when screen is OFF)",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Battery Optimization (Unrestricted) Button if not yet allowed
            if (!isBatteryOptIgnored) {
                Surface(
                    color = Color(0xFF78350F), // Amber-900
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clickable { onRequestBatteryOptimization() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = "Battery Warning",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tap to enable 'Unrestricted Battery' (Required for Lock Mode)",
                            color = Color(0xFFFEF3C7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Big Interactive Speaker Disc
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .size(150.dp)
                    .scale(if (hasSmsPermission) pulseScale else 1.0f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF10B981), Color(0xFF065F46), Color(0xFF022C22))
                        )
                    )
                    .border(3.dp, Color(0xFF34D399), CircleShape)
                    .clickable {
                        onTestSpeech("500", "రమేష్ కుమార్")
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Speaker",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "LIVE LISTENER",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Tap to Test Alert",
                        color = Color(0xFFA7F3D0),
                        fontSize = 9.sp
                    )
                }
            }

            // Quick Test Presets
            Text(
                text = "TEST VOICE ANNOUNCEMENT:",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start).padding(top = 8.dp, bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onTestSpeech("500", "రమేష్") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("₹500 (రమేష్)", fontSize = 11.sp, color = Color(0xFF34D399))
                }

                Button(
                    onClick = { onTestSpeech("2000", "సురేష్") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("₹2,000 (సురేష్)", fontSize = 11.sp, color = Color(0xFF38BDF8))
                }

                Button(
                    onClick = { onTestSpeech("750", "") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("₹750 (Direct)", fontSize = 11.sp, color = Color(0xFFFDE047))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Recent In-App Live Payments Feed
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECEIVED PAYMENTS (${payments.size})",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (payments.isNotEmpty()) {
                    Text(
                        text = "Auto-Read From SMS",
                        color = Color(0xFF10B981),
                        fontSize = 10.sp
                    )
                }
            }

            if (payments.isEmpty()) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Waiting for incoming bank SMS...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "When an SMS arrives from your bank or UPI with credited amount, it will automatically speak in Telugu — even if the phone screen is locked!",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(payments) { item ->
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "₹${item.amount}",
                                            color = Color(0xFF34D399),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (item.payer.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "from ${item.payer}",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${item.bank} • ${item.time}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                }

                                IconButton(onClick = { onTestSpeech(item.amount, item.payer) }) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Replay",
                                        tint = Color(0xFF34D399)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

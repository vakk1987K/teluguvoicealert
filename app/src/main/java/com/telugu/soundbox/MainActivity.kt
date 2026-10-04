import android.app.Activity
import android.widget.Toast

SoundboxForegroundService.startService(this)
AdMobManager.initialize(applicationContext)

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

         // AdMob Banner Ad (Test Ad #1)
             Spacer(modifier = Modifier.height(6.dp))
             AdMobBanner(
                 modifier = Modifier
                     .fillMaxWidth()
                     .height(50.dp)
             )

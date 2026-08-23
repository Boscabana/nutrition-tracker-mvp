package com.nick.nutritiontracker.ui

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class RewardedAdManager(private val activity: Activity) {
    private var rewardedAd: RewardedAd? = null
    private val adUnitId = "ca-app-pub-3940256099942544/5224354917" // Test Rewarded Ad Unit ID

    fun loadAd(onAdLoaded: () -> Unit = {}) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(activity, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("RewardedAdManager", adError.message)
                rewardedAd = null
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d("RewardedAdManager", "Ad was loaded.")
                rewardedAd = ad
                onAdLoaded()
            }
        })
    }

    fun showAd(onRewardEarned: () -> Unit) {
        rewardedAd?.let { ad ->
            ad.show(activity) { rewardItem ->
                Log.d("RewardedAdManager", "User earned the reward.")
                onRewardEarned()
                loadAd() // Pre-load the next one
            }
        } ?: run {
            Log.d("RewardedAdManager", "The rewarded ad wasn't ready yet.")
            loadAd {
                showAd(onRewardEarned)
            }
        }
    }
}
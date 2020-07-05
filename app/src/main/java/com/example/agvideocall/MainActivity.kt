package com.example.agvideocall

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.formats.MediaView
import com.google.android.gms.ads.formats.UnifiedNativeAd
import com.google.android.gms.ads.formats.UnifiedNativeAdView
import io.agora.rtc.Constants
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.RtcEngine
import io.agora.rtc.video.VideoCanvas
import io.agora.rtc.video.VideoEncoderConfiguration
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_ID = 1001
    private val REQUESTED_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)

    private var mRtcEngine: RtcEngine? = null
    private var doubleBackToExitPressedOnce = false

    private var nativeAd: UnifiedNativeAd? = null
    private lateinit var mHandler: Handler
    private val mInterval = 15000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeMobileAds()
        getAds()

        mHandler = Handler()
        bindAndSetupUi();
    }

    private fun bindAndSetupUi() {
        startCall.setOnClickListener() {
            if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQUEST_ID) &&
                    checkSelfPermission(REQUESTED_PERMISSIONS[1], PERMISSION_REQUEST_ID)) {
                initializeAgoraEngine()
                setupLocalVideoFeed()
                joinChannel()
                startCall.visibility = View.GONE
                endCall.visibility = View.VISIBLE
                ad_parent_container.visibility = View.GONE
                closeCallIfNotConnectedIn(mInterval, endCall)
            }
        }
        endCall.setOnClickListener() {
            leaveChannelClicked()
            endCall.visibility = View.GONE
            startCall.visibility = View.VISIBLE
            ad_parent_container.visibility = View.VISIBLE
            getAds()
            removeHandlerCallbacks()
        }
    }

    private fun closeCallIfNotConnectedIn(mInterval: Int, endCallBtn: TextView) {
        mHandler.postDelayed({
            Toast.makeText(this@MainActivity, getString(R.string.try_again_msg), Toast.LENGTH_LONG).show()
            leaveChannel()
            endCallBtn.performClick()
        }, mInterval.toLong())
    }

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onFirstRemoteVideoDecoded(uid: Int, width: Int, height: Int, elapsed: Int) {
            runOnUiThread { setupRemoteVideoStream(uid) }
        }
        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                endCall.performClick()
            }
        }
    }

    private fun initializeAgoraEngine() {
        try {
            mRtcEngine = RtcEngine.create(baseContext, getString(R.string.agora_app_id), mRtcEventHandler)
        } catch (e: Exception) {
            throw RuntimeException("NEED TO check rtc sdk init fatal error" + Log.getStackTraceString(e))
        }
        setupUserSession()
    }

    private fun setupUserSession() {
        mRtcEngine!!.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
        mRtcEngine!!.enableVideo()
        mRtcEngine!!.setVideoEncoderConfiguration(VideoEncoderConfiguration(VideoEncoderConfiguration.VD_640x480, VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30,
            VideoEncoderConfiguration.STANDARD_BITRATE,
            VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT))
    }

    private fun setupLocalVideoFeed() {
        val videoContainer = findViewById<FrameLayout>(R.id.local_video_view)
        val videoSurface = RtcEngine.CreateRendererView(baseContext)
        videoSurface.setZOrderMediaOverlay(true)
        videoContainer.addView(videoSurface)
        mRtcEngine!!.setupLocalVideo(VideoCanvas(videoSurface, VideoCanvas.RENDER_MODE_FIT, 0))
    }

    private fun setupRemoteVideoStream(uid: Int) {
        val videoContainer = findViewById<FrameLayout>(R.id.remote_video_view)
        val videoSurface = RtcEngine.CreateRendererView(baseContext)
        videoContainer.addView(videoSurface)
        mRtcEngine!!.setupRemoteVideo(VideoCanvas(videoSurface, VideoCanvas.RENDER_MODE_FIT, uid))
        mRtcEngine!!.setRemoteSubscribeFallbackOption(Constants.MEDIA_TYPE_AUDIO_ONLY)
        removeHandlerCallbacks()
    }

    private fun checkSelfPermission(permission: String?, requestCode: Int): Boolean {
        if (ContextCompat.checkSelfPermission(this,
                        permission!!)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    REQUESTED_PERMISSIONS,
                    requestCode)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_ID -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }
        }
    }

    private fun joinChannel() {
        mRtcEngine!!.joinChannel(null, "test-channel", "Extra Optional Data", 0)
        setupLocalVideoFeed()
    }

    private fun leaveChannelClicked() {
        leaveChannel()
        removeVideo(R.id.local_video_view)
        removeVideo(R.id.remote_video_view)
    }

    private fun leaveChannel() {
        try {
            mRtcEngine!!.leaveChannel()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun removeVideo(containerID: Int) {
        val videoContainer = findViewById<FrameLayout>(containerID)
        videoContainer.removeAllViews()
    }

    private fun initializeMobileAds() {
        MobileAds.initialize(
            this
        ) { }
    }

    private fun getAds() {
        val builder =
            AdLoader.Builder(this, getString(R.string.google_native_ad_test_unit_id))
        builder.forUnifiedNativeAd { unifiedNativeAd ->
            if (nativeAd == null) nativeAd = unifiedNativeAd
            Toast.makeText(this@MainActivity, "New ad loaded", Toast.LENGTH_SHORT).show()
            val adView = layoutInflater.inflate(
                R.layout.native_ad_layout,
                null
            ) as UnifiedNativeAdView
            populateWithNativeAd(unifiedNativeAd, adView)
            ad_parent_container.removeAllViews()
            ad_parent_container.addView(adView)
        }
        val adLoader = builder.withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(i: Int) {
                Toast.makeText(this@MainActivity, "Ad failed to load", Toast.LENGTH_SHORT).show()
                super.onAdFailedToLoad(i)
            }
        }).build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun populateWithNativeAd(unifiedNativeAd: UnifiedNativeAd,adView: UnifiedNativeAdView) {

        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.mediaView = adView.findViewById<View>(R.id.ad_media) as MediaView
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_icon)
        (adView.headlineView as TextView).text = unifiedNativeAd.headline
        adView.mediaView.setMediaContent(unifiedNativeAd.mediaContent)
        if (unifiedNativeAd.body == null) {
            adView.bodyView.visibility = View.GONE
        } else {
            (adView.bodyView as TextView).text = unifiedNativeAd.body
            adView.bodyView.visibility = View.VISIBLE
        }
        if (unifiedNativeAd.callToAction == null) {
            adView.callToActionView.visibility = View.GONE
        } else {
            (adView.callToActionView as TextView).text = unifiedNativeAd.callToAction
            adView.callToActionView.visibility = View.VISIBLE
        }
        if (unifiedNativeAd.icon == null) {
            adView.iconView.visibility = View.GONE
        } else {
            (adView.iconView as ImageView).setImageDrawable(
                unifiedNativeAd.icon.drawable
            )
            adView.iconView.visibility = View.VISIBLE
        }
        adView.setNativeAd(unifiedNativeAd)
    }

    private fun removeHandlerCallbacks() {
        mHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        RtcEngine.destroy()
        mRtcEngine = null

        if (nativeAd != null)
            nativeAd!!.destroy()
        removeHandlerCallbacks()
    }

    override fun onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed()
            return
        }
        this.doubleBackToExitPressedOnce = true
        Toast.makeText(this, "Press again to exit. Any ongoing call will be disconnected.", Toast.LENGTH_LONG).show()
        Handler().postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
    }

}

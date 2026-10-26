package com.example.headphoneanim

import android.annotation.SuppressLint
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.*
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var cardContainer: LinearLayout
    private lateinit var animationView: ImageView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var testBtn: Button
    private var overlayBtn: Button? = null

    private val handler = Handler(Looper.getMainLooper())
    private val autoCloseRunnable = Runnable { animateOutAndFinish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            permissionsToRequest.add("android.permission.POST_NOTIFICATIONS")
        }
        if (Build.VERSION.SDK_INT >= 31) {
            permissionsToRequest.add("android.permission.BLUETOOTH_CONNECT")
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), 101)
        }

        val serviceIntent = Intent(this, HeadphoneService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setupUI()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleIntent(intent)
        }
    }

    private fun setupUI() {
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
            setOnClickListener { animateOutAndFinish() }
        }

        cardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(60, 60, 60, 60)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 72f
                setColor(Color.parseColor("#181824"))
                setStroke(3, Color.parseColor("#3300E5FF"))
            }
            isClickable = true
        }

        animationView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val animParams = LinearLayout.LayoutParams(480, 480)
        cardContainer.addView(animationView, animParams)

        titleText = TextView(this).apply {
            text = "Мониторинг наушников"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 8)
        }
        cardContainer.addView(titleText)

        subtitleText = TextView(this).apply {
            text = "Сервис готов к работе"
            textSize = 14f
            setTextColor(Color.parseColor("#9E9EBA"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 36)
        }
        cardContainer.addView(subtitleText)

        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 20 }

        testBtn = Button(this).apply {
            text = "Тест анимации"
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                playConnectAnimation("vivo TWS 5i", "Проверка всплывающего окна", true)
            }
        }
        cardContainer.addView(testBtn, btnParams)

        if (!Settings.canDrawOverlays(this)) {
            overlayBtn = Button(this).apply {
                text = "Разрешить показ поверх окон"
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = 40f
                    setColor(Color.parseColor("#2A2A3C"))
                }
                setOnClickListener {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
            cardContainer.addView(overlayBtn, btnParams)
        }

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply {
            setMargins(40, 40, 40, 60)
        }

        rootLayout.addView(cardContainer, cardParams)
        setContentView(rootLayout)
    }

    private fun handleIntent(intent: Intent) {
        val autoTriggered = intent.getBooleanExtra("auto_triggered", false)
        val deviceName = intent.getStringExtra("device_name") ?: "Наушники"
        val deviceType = intent.getStringExtra("device_type")
            ?: if (autoTriggered) "Аудиоустройство активно" else "Фоновый мониторинг активен"

        playConnectAnimation(deviceName, deviceType, autoTriggered)
    }

    private fun playConnectAnimation(name: String, type: String, autoClose: Boolean) {
        handler.removeCallbacks(autoCloseRunnable)
        titleText.text = name
        subtitleText.text = type

        if (autoClose) {
            testBtn.visibility = View.GONE
            overlayBtn?.visibility = View.GONE
        } else {
            testBtn.visibility = View.VISIBLE
            overlayBtn?.visibility = if (!Settings.canDrawOverlays(this)) View.VISIBLE else View.GONE
        }

        cardContainer.translationY = 900f
        cardContainer.alpha = 0f
        cardContainer.scaleX = 0.85f
        cardContainer.scaleY = 0.85f

        val slideUp = ObjectAnimator.ofFloat(cardContainer, View.TRANSLATION_Y, 900f, 0f)
        val fadeIn = ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 0f, 1f)
        val scaleX = ObjectAnimator.ofFloat(cardContainer, View.SCALE_X, 0.85f, 1f)
        val scaleY = ObjectAnimator.ofFloat(cardContainer, View.SCALE_Y, 0.85f, 1f)

        AnimatorSet().apply {
            playTogether(slideUp, fadeIn, scaleX, scaleY)
            duration = 650
            interpolator = OvershootInterpolator(1.15f)
            start()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(resources, R.drawable.vivo_popup)
                val drawable = ImageDecoder.decodeDrawable(source)
                animationView.setImageDrawable(drawable)
                if (drawable is AnimatedImageDrawable) {
                    drawable.repeatCount = 0
                    drawable.start()
                }
            } catch (e: Exception) {
                animationView.setImageResource(R.drawable.vivo_popup)
            }
        } else {
            animationView.setImageResource(R.drawable.vivo_popup)
        }

        if (autoClose) {
            handler.postDelayed(autoCloseRunnable, 4000)
        }
    }

    private fun animateOutAndFinish() {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(cardContainer, View.TRANSLATION_Y, 0f, 900f),
                ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 1f, 0f)
            )
            duration = 300
            interpolator = DecelerateInterpolator()
            start()
        }
        handler.postDelayed({ finish() }, 300)
    }
}

class HeadphoneService : Service() {

    private lateinit var audioManager: AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isReady = false
    private var lastTriggerTime = 0L
    private var lastConnectedBtName: String? = null

    private val btReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            if (device != null && hasBtPermission()) {
                val name = device.alias ?: device.name
                if (!name.isNullOrBlank() && !isSystemBoardName(name)) {
                    lastConnectedBtName = name
                }
            }
        }
    }

    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (!isReady || addedDevices == null) return

            for (device in addedDevices) {
                if (!device.isSink) continue

                val isWired = device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                val isUsb = device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        device.type == AudioDeviceInfo.TYPE

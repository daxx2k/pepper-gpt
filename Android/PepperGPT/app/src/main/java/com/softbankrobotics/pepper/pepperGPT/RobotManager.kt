package com.softbankrobotics.pepper.pepperGPT

import android.content.Context
import android.util.Log
import com.aldebaran.qi.AnyObject
import com.aldebaran.qi.Future
import com.aldebaran.qi.Session as QiSession
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.builder.*
import com.aldebaran.qi.sdk.`object`.actuation.Animate
import com.aldebaran.qi.sdk.`object`.actuation.LookAt
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.human.Human
import com.aldebaran.qi.sdk.`object`.humanawareness.HumanAwareness
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.`object`.holder.Holder
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session as JschSession
import java.io.OutputStream
import java.util.Locale
import java.util.Properties
import java.util.Random
import java.util.regex.Pattern
import kotlinx.coroutines.*

class RobotManager private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: RobotManager? = null
        fun getInstance(context: Context): RobotManager =
            instance ?: synchronized(this) {
                instance ?: RobotManager(context.applicationContext).also { instance = it }
            }
    }

    private val TAG = "RobotManager"
    private var qiContext: QiContext? = null
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Holders
    private var abilityHolder: Holder? = null
    
    // Animation references
    private var currentAnimate: Animate? = null
    private var currentAnimateFuture: Future<Void>? = null

    // SSH members
    private var sshSession: JschSession? = null
    private var shellChannel: ChannelShell? = null
    private var outputStream: OutputStream? = null
    private var isSshConnected = false
    private val connectionPrefs
        get() = context.getSharedPreferences("PepperGPT_Prefs", Context.MODE_PRIVATE)

    init {
        connectSsh()
        startBatteryPoller()
    }

    private var isConnecting = false
    private var lastSshAttempt = 0L
    private var sshBackoffMs = 5_000L

    private fun connectSsh() {
        if (isSshConnected || isConnecting) return
        val pepperIp = connectionPrefs.getString("ssh_host", "")?.trim() ?: ""
        val sshUser = connectionPrefs.getString("ssh_user", "")?.trim() ?: ""
        val sshPassword = connectionPrefs.getString("ssh_password", "") ?: ""
        val knownHosts = connectionPrefs.getString("ssh_known_hosts", "") ?: ""
        if (pepperIp.isBlank() || sshUser.isBlank() || sshPassword.isBlank() || knownHosts.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastSshAttempt < sshBackoffMs) return
        lastSshAttempt = now
        isConnecting = true
        
        managerScope.launch {
            try {
                Log.d(TAG, "🔗 Connecting SSH to $pepperIp...")
                val jsch = JSch()
                jsch.setKnownHosts(java.io.ByteArrayInputStream(knownHosts.toByteArray(Charsets.UTF_8)))
                sshSession = jsch.getSession(sshUser, pepperIp, 22)
                sshSession?.setPassword(sshPassword)
                
                val config = Properties()
                config["kex"] = "diffie-hellman-group14-sha256"
                config["server_host_key"] = "ecdsa-sha2-nistp256,ssh-rsa"
                config["StrictHostKeyChecking"] = "yes"
                
                sshSession?.setConfig(config)
                sshSession?.connect(5000)
                
                shellChannel = sshSession?.openChannel("shell") as? ChannelShell
                outputStream = shellChannel?.outputStream
                shellChannel?.connect()
                
                isSshConnected = true
                sshBackoffMs = 5_000L
                Log.d(TAG, "✅ SSH Connected to $pepperIp")
            } catch (e: Exception) {
                Log.e(TAG, "❌ SSH Failed: ${e.message}")
                sshBackoffMs = (sshBackoffMs * 2).coerceAtMost(60_000L)
            } finally {
                isConnecting = false
            }
        }
    }

    private fun sendSshCommand(command: String) {
        if (!isSshConnected) {
            connectSsh() // Try reconnecting
            return
        }
        managerScope.launch {
            try {
                outputStream?.write((command + "\n").toByteArray(Charsets.UTF_8))
                outputStream?.flush()
                Log.v(TAG, "📡 SSH Command sent: $command")
            } catch (e: Exception) {
                Log.e(TAG, "❌ SSH Send failed: $command", e)
                isSshConnected = false
            }
        }
    }

    private suspend fun execSshCommand(command: String): String = withContext(Dispatchers.IO) {
        if (!isSshConnected) return@withContext ""
        try {
            val channel = sshSession?.openChannel("exec") as? com.jcraft.jsch.ChannelExec ?: return@withContext ""
            channel.setCommand(command)
            val input = channel.inputStream
            channel.connect()
            
            val result = input.bufferedReader().use { it.readText() }
            channel.disconnect()
            result.trim()
        } catch (e: Exception) {
            Log.e(TAG, "❌ SSH Exec failed: $command", e)
            ""
        }
    }

    fun onFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext
        Log.d(TAG, "✅ Robot Focus Gained")
        // Force an immediate battery check if possible
        managerScope.launch {
            if (isSshConnected) {
                val result = execSshCommand("qicli call ALBattery.getBatteryCharge")
                val pattern = Pattern.compile("(\\d+)")
                val matcher = pattern.matcher(result)
                if (matcher.find()) {
                    lastSshBatteryLevel = matcher.group(1)?.toIntOrNull() ?: -1
                }
            }
        }
    }

    fun onFocusLost() {
        this.qiContext = null
        releaseAbilities()
        stopAnimations()
        Log.d(TAG, "❌ Robot Focus Lost")
    }

    fun stopAnimations() {
        try {
            @Suppress("DEPRECATION") currentAnimateFuture?.cancel()
            currentAnimate = null
        } catch (e: Exception) {
            Log.w(TAG, "Stop animations failed: ${e.message}")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun triggerAnimation(animationResId: Int, isAsync: Boolean = true): Future<Void>? {
        val qia = qiContext ?: return null
        return AnimationBuilder.with(qia)
            .withResources(animationResId)
            .buildAsync()
            .andThenCompose { animation ->
                val animate = AnimateBuilder.with(qia)
                    .withAnimation(animation)
                    .build()
                currentAnimate = animate
                currentAnimateFuture = animate.async().run()
                currentAnimateFuture
            }
    }

    /** Hold only this app's autonomous abilities; never change global stiffness or wake the robot. */
    fun holdAbilities() {
        val qia = qiContext ?: return
        managerScope.launch {
            try {
                val holder = HolderBuilder.with(qia).withAutonomousAbilities(
                    AutonomousAbilitiesType.BASIC_AWARENESS,
                    AutonomousAbilitiesType.BACKGROUND_MOVEMENT).build()
                if (qiContext !== qia) return@launch
                holder.hold()
                if (qiContext !== qia) holder.release()
                else {
                    val previous = abilityHolder
                    abilityHolder = holder
                    previous?.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not hold app abilities: ${e.javaClass.simpleName}")
            }
        }
    }

    fun releaseAbilities() {
        val holder = abilityHolder ?: return
        abilityHolder = null
        managerScope.launch {
            try { holder.release() }
            catch (e: Exception) { Log.w(TAG, "Could not release app abilities: ${e.javaClass.simpleName}") }
        }
    }

    fun ensureInteractiveState() { releaseAbilities() }

    fun setLedStatus(status: String) {
        // Define color components for ChestLeds based on status
        val (r, g, b) = when (status) {
            "thinking" -> Triple(0f, 1f, 1f)    // Cyan
            "speaking" -> Triple(0f, 0f, 1f)    // Blue
            "listening" -> Triple(0f, 1f, 0f)   // Green
            "drawing" -> Triple(1f, 0.5f, 0f)   // Orange/Yellow
            "error" -> Triple(1f, 0f, 0f)       // Red
            else -> Triple(1f, 1f, 1f)          // White/Idle
        }

        // Use SSH based control as per reference project
        sendSshCommand("qicli call ALLeds.fadeRGB \"ChestLeds\" $r $g $b 0.2")
        

    }
    
    fun lookAtUser(): Future<Void>? {
        val qia = qiContext ?: return null
        return try {
            val human = qia.humanAwareness.engagedHuman
            if (human != null) {
                LookAtBuilder.with(qia)
                    .withFrame(human.headFrame)
                    .buildAsync().andThenCompose { it.async().run() }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "LookAt failed: ${e.message}")
            null
        }
    }


    private fun getSession(qia: QiContext): QiSession? {
        Log.d(TAG, "🔍 Attempting to reflect QiSession from QiContext: ${qia.javaClass.name}")
        return try {
            // Strategy 1: Direct getSession() or session
            val sessionProp = qia.javaClass.methods.find { it.name == "getSession" || it.name == "session" || it.name == "getQiSession" }
            if (sessionProp != null) {
                val s = sessionProp.invoke(qia) as? QiSession
                if (s != null) return s
            }
            
            // Strategy 2: robotContext
            val robotContextProp = qia.javaClass.methods.find { it.name == "getRobotContext" || it.name == "robotContext" }
            val robotContext = robotContextProp?.invoke(qia)
            if (robotContext != null) {
                Log.d(TAG, "  -> Found RobotContext: ${robotContext.javaClass.name}")
                val sProp = robotContext.javaClass.methods.find { it.name == "getSession" || it.name == "session" }
                if (sProp != null) {
                    val s = sProp.invoke(robotContext) as? QiSession
                    if (s != null) return s
                }
            }
            
            // Strategy 3: PepperQiContext specifically if it's a wrapper
            val contextField = qia.javaClass.declaredFields.find { it.name == "qiContext" || it.name == "context" }
            if (contextField != null) {
                contextField.isAccessible = true
                val inner = contextField.get(qia) as? QiContext
                if (inner != null && inner !== qia) return getSession(inner)
            }

            Log.e(TAG, "❌ Reflection failed to find QiSession in QiContext (exhausted strategies)")
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Reflection failed to get session: ${e.message}")
            null
        }
    }

    private var lastSshBatteryLevel = -1

    private fun startBatteryPoller() {
        managerScope.launch {
            while (isActive) {
                if (isSshConnected) {
                    val result = execSshCommand("qicli call ALBattery.getBatteryCharge")
                    val pattern = Pattern.compile("(\\d+)")
                    val matcher = pattern.matcher(result)
                    if (matcher.find()) {
                        lastSshBatteryLevel = matcher.group(1)?.toIntOrNull() ?: -1
                        Log.d(TAG, "🔋 Battery level via SSH: $lastSshBatteryLevel% (Raw: '$result')")
                    }
                }
                delay(30000)
            }
        }
    }

    fun getBatteryLevel(): Int {
        if (lastSshBatteryLevel != -1) return lastSshBatteryLevel
        
        // Fallback to QiSDK if SSH result isn't ready
        val qia = qiContext ?: return -1
        return try {
            val session = getSession(qia) ?: return -1
            val battery: AnyObject = session.service("ALBattery").get(2, java.util.concurrent.TimeUnit.SECONDS)
            val level = battery.call<Any>("getBatteryCharge").get(2, java.util.concurrent.TimeUnit.SECONDS)
            (level as? Number)?.toInt() ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    private var radioLedJob: Job? = null
    private var speakingLedJob: Job? = null
    private var thinkingLedJob: Job? = null

    fun startSpeakingLedAnimation() {
        if (speakingLedJob != null) return
        Log.d(TAG, "🎙️ Starting speaking LED animation (Chest)")
        speakingLedJob = managerScope.launch {
            try {
                while (isActive) {
                    // Pulse Blue/Cyan
                    sendSshCommand("qicli call ALLeds.fadeRGB \"ChestLeds\" 0.0 0.0 1.0 0.4")
                    delay(400)
                    sendSshCommand("qicli call ALLeds.fadeRGB \"ChestLeds\" 0.0 1.0 1.0 0.4")
                    delay(400)
                }
            } finally {
                sendSshCommand("qicli call ALLeds.fadeRGB \"ChestLeds\" 1.0 1.0 1.0 0.2")
            }
        }
    }

    fun stopSpeakingLedAnimation() {
        Log.d(TAG, "🎙️ Stopping speaking LED animation")
        speakingLedJob?.cancel()
        speakingLedJob = null
    }

    fun startThinkingLedAnimation() {
        if (thinkingLedJob != null) return
        Log.d(TAG, "🧠 Starting thinking LED animation (Ears)")
        thinkingLedJob = managerScope.launch {
            try {
                while (isActive) {
                    // Ear LEDs are single-color (blue), use setIntensity per reference project
                    sendSshCommand("qicli call ALLeds.setIntensity \"LeftEarLeds\" 1.0")
                    sendSshCommand("qicli call ALLeds.setIntensity \"RightEarLeds\" 1.0")
                    delay(500)
                    sendSshCommand("qicli call ALLeds.setIntensity \"LeftEarLeds\" 0.2")
                    sendSshCommand("qicli call ALLeds.setIntensity \"RightEarLeds\" 0.2")
                    delay(500)
                }
            } finally {
                sendSshCommand("qicli call ALLeds.setIntensity \"LeftEarLeds\" 1.0")
                sendSshCommand("qicli call ALLeds.setIntensity \"RightEarLeds\" 1.0")
            }
        }
    }

    fun stopThinkingLedAnimation() {
        Log.d(TAG, "🧠 Stopping thinking LED animation")
        thinkingLedJob?.cancel()
        thinkingLedJob = null
    }

    fun startRadioLedAnimation() {
        if (radioLedJob != null) return
        Log.d(TAG, "📻 Starting radio random LED animation")
        radioLedJob = managerScope.launch {
            if (!ensureSshConnected()) {
                Log.w(TAG, "📻 Radio LEDs skipped — SSH not available")
                return@launch
            }
            val random = Random()
            val faceGroups = listOf(
                "FaceLeds", "LeftFaceLeds", "RightFaceLeds",
                "FaceLedsTop", "FaceLedsBottom", "FaceLedsLeftTop", "FaceLedsRightTop"
            )
            try {
                while (isActive) {
                    val duration = 0.2f + random.nextFloat() * 0.4f
                    // ChestLeds = shoulder RGB on Pepper
                    qicliFadeRgb(
                        "ChestLeds",
                        random.nextFloat(), random.nextFloat(), random.nextFloat(),
                        duration
                    )
                    val face = faceGroups[random.nextInt(faceGroups.size)]
                    qicliFadeRgb(
                        face,
                        random.nextFloat(), random.nextFloat(), random.nextFloat(),
                        duration
                    )
                    if (random.nextBoolean()) {
                        val face2 = faceGroups[random.nextInt(faceGroups.size)]
                        qicliFadeRgb(
                            face2,
                            random.nextFloat(), random.nextFloat(), random.nextFloat(),
                            duration
                        )
                    }
                    val earLevel = random.nextFloat()
                    qicliSetIntensity("LeftEarLeds", earLevel)
                    qicliSetIntensity("RightEarLeds", earLevel)
                    delay(250 + random.nextInt(350).toLong())
                }
            } finally {
                qicliFadeRgb("FaceLeds", 1f, 1f, 1f, 0.2f)
                qicliFadeRgb("ChestLeds", 1f, 1f, 1f, 0.2f)
                qicliSetIntensity("LeftEarLeds", 1f)
                qicliSetIntensity("RightEarLeds", 1f)
            }
        }
    }

    private suspend fun ensureSshConnected(): Boolean {
        if (!isSshConnected) connectSsh()
        repeat(10) {
            if (isSshConnected) return true
            delay(500)
        }
        return isSshConnected
    }

    private suspend fun qicliFadeRgb(group: String, r: Float, g: Float, b: Float, duration: Float) {
        execSshCommand(
            String.format(
                Locale.US,
                "qicli call ALLeds.fadeRGB \"%s\" %.3f %.3f %.3f %.3f",
                group, r, g, b, duration
            )
        )
    }

    private suspend fun qicliSetIntensity(group: String, intensity: Float) {
        execSshCommand(
            String.format(
                Locale.US,
                "qicli call ALLeds.setIntensity \"%s\" %.3f",
                group, intensity
            )
        )
    }

    fun stopRadioLedAnimation() {
        Log.d(TAG, "📻 Stopping radio LED animation")
        radioLedJob?.cancel()
        radioLedJob = null
    }
}

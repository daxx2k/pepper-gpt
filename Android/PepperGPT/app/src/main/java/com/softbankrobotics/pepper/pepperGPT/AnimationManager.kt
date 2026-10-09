package com.softbankrobotics.pepper.pepperGPT

import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Animate
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import kotlinx.coroutines.*

/**
 * Centralised animation controller for Pepper.
 *
 * Groups every .qianim resource by purpose so that any Activity
 * can trigger contextual gestures via a single call.
 */
class AnimationManager {

    companion object {
        private const val TAG = "AnimationManager"

        // ── Hello / Greeting ──
        val HELLO_ANIMS = listOf(
            R.raw.hello_a001,
            R.raw.salute_right_b001,
            R.raw.hello_02,
            R.raw.hello_05
        )

        // ── Thinking / Processing ──
        val THINKING_ANIMS = listOf(
            R.raw.looking_around,
            R.raw.enumeration_01
        )

        // ── Success / Reaction ──
        val SUCCESS_ANIMS = listOf(
            R.raw.nice_reaction_01,
            R.raw.nice_reaction_02,
            R.raw.show_tablet_01,
            R.raw.show_tablet_03
        )

        // ── Sad / Error ──
        val SAD_ANIMS = listOf(
            R.raw.sad_reaction_01
        )

        // ── Show Tablet (presenting results) ──
        val SHOW_TABLET_ANIMS = listOf(
            R.raw.show_tablet_01,
            R.raw.show_tablet_03
        )

        // ── Attract / Idle ──
        val ATTRACT_ANIMS = listOf(
            R.raw.attract_left_01,
            R.raw.attract_right_01,
            R.raw.attract_left_03,
            R.raw.attract_right_03
        )

        // ── Dance (radio) ──
        val DANCE_ANIMS = listOf(
            R.raw.funny_01,
            R.raw.funny_02,
            R.raw.play_hand_left,
            R.raw.play_hand_right,
            R.raw.attract_left_01,
            R.raw.attract_right_01,
            R.raw.attract_left_03,
            R.raw.attract_right_03,
            R.raw.looking_around,
            R.raw.nice_reaction_01,
            R.raw.nice_reaction_02
        )

        // ── Back to Stand (reset pose) ──
        val BACK_TO_STAND_ANIMS = listOf(
            R.raw.back_to_stand
        )

        // ── Listening (Head Nod) ──
        val HEAD_NOD_ANIMS = listOf(
            R.raw.head_nod
        )
    }

    @Volatile private var qiContext: QiContext? = null
    private var generation = 0
    private var thinkingPending = false

    // Track currently running animations so they can be cancelled
    private var currentAnimate: Animate? = null
    private var currentFuture: Future<Void>? = null
    private var thinkingFuture: Future<Void>? = null

    @Synchronized fun onFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext
    }

    @Synchronized fun onFocusLost() {
        stopAll()
        this.qiContext = null
    }

    // ───────── Public API ─────────

    /** Play a random greeting animation (non-blocking). */
    fun playHello() = playRandom(HELLO_ANIMS)

    /** Play a thinking/looking-around animation (non-blocking, single instance). */
    @Synchronized fun playThinking() {
        if (thinkingFuture != null || thinkingPending) return
        val qia = qiContext ?: return
        val epoch = generation
        thinkingPending = true
        AnimationBuilder.with(qia).withResources(THINKING_ANIMS.random()).buildAsync()
            .thenConsume { built ->
                synchronized(this@AnimationManager) {
                    if (epoch == generation && qiContext === qia) {
                        thinkingPending = false
                        if (!built.hasError() && !built.isCancelled) {
                            val animate = AnimateBuilder.with(qia).withAnimation(built.get()).build()
                            val running = animate.async().run()
                            thinkingFuture = running
                            running.thenConsume {
                                synchronized(this@AnimationManager) {
                                    if (thinkingFuture === running) thinkingFuture = null
                                }
                            }
                        }
                    }
                }
            }
    }

    /** Play a success/nice-reaction animation (non-blocking). */
    fun playSuccess() = playRandom(SUCCESS_ANIMS)

    /** Play a sad/error reaction animation (non-blocking). */
    fun playSad() = playRandom(SAD_ANIMS)

    /** Play a show-tablet gesture (non-blocking). */
    fun playShowTablet() = playRandom(SHOW_TABLET_ANIMS)

    /** Play an attract/idle animation (non-blocking). */
    fun playAttract() = playRandom(ATTRACT_ANIMS)

    /** Play a back-to-stand reset (non-blocking). */
    fun playBackToStand() = playRandom(BACK_TO_STAND_ANIMS)

    /** Play a listening animation (head nod) (non-blocking). */
    fun playListening() = playRandom(HEAD_NOD_ANIMS)

    /** Play a specific animation resource (non-blocking). Returns a cancellable Future. */
    fun play(animResId: Int): Future<Void>? {
        stopAll()
        val qia = qiContext ?: return null
        val epoch = generation
        return try {
            val animation = AnimationBuilder.with(qia).withResources(animResId).build()
            synchronized(this) {
                if (epoch != generation || qiContext !== qia) return null
                val animate = AnimateBuilder.with(qia).withAnimation(animation).build()
                currentAnimate = animate
                currentFuture = animate.async().run()
                currentFuture
            }
        } catch (e: Exception) {
            Log.w(TAG, "Animation play failed: ${e.message}")
            null
        }
    }

    /** Stop any currently running animation. */
    @Synchronized fun stopAll() {
        generation++
        thinkingPending = false
        try {
            @Suppress("DEPRECATION") currentFuture?.cancel()
            currentFuture = null
            currentAnimate = null
            @Suppress("DEPRECATION") thinkingFuture?.cancel()
            thinkingFuture = null
        } catch (e: Exception) {
            Log.w(TAG, "Stop animations failed: ${e.message}")
        }
    }

    // ───────── Internals ─────────

    @Synchronized private fun playRandom(anims: List<Int>) {
        stopAll()
        val qia = qiContext ?: return
        val epoch = generation
        AnimationBuilder.with(qia).withResources(anims.random()).buildAsync()
            .thenConsume { built ->
                synchronized(this@AnimationManager) {
                    if (epoch == generation && qiContext === qia && !built.hasError() && !built.isCancelled) {
                        val animate = AnimateBuilder.with(qia).withAnimation(built.get()).build()
                        currentAnimate = animate
                        currentFuture = animate.async().run()
                    }
                }
            }
    }
}

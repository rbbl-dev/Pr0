package com.pr0gramm.app.ui.views.viewer

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.SeekBar
import androidx.annotation.LayoutRes
import androidx.core.view.isVisible
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.util.dp
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 */
@Suppress("LeakingThis")
abstract class AbstractProgressMediaView(config: MediaView.Config, @LayoutRes layoutId: Int?) : MediaView(config, layoutId) {
    internal var progressTouched = false
    internal var lastUserInteraction: Long = -1

    private var progressEnabled = true
    private var firstTimeProgressValue = true

    private val seekBarView: SeekBar = LayoutInflater.from(context)
            .inflate(R.layout.player_video_seekbar, this, false) as SeekBar

    private val progressView: ProgressBar = LayoutInflater.from(context)
            .inflate(R.layout.player_video_progress, this, false) as ProgressBar

    // keep a reference to the update callback so we can re-use
    // the instance to schedule and de-schedule it later.
    private val updateCallback = Runnable { updateTimeline() }

    private var maxBufferedValue = 0.0f

    protected abstract fun currentVideoProgress(): ProgressInfo?

    init {
        publishControllerView(progressView)
        publishControllerView(seekBarView)

        updateTimeline()

        seekBarView.setOnSeekBarChangeListener(SeekbarChangeListener())
    }

    override fun onSingleTap(event: MotionEvent): Boolean {
        if (userSeekable()) {
            if (seekCurrentlyVisible()) {
                lastUserInteraction = -1
                showSeekbar(false)
            } else {
                lastUserInteraction = System.currentTimeMillis()
                showSeekbar(true)
            }

            return true
        }

        return super.onSingleTap(event)
    }

    protected open fun userSeekable(): Boolean {
        return false
    }

    private fun seekCurrentlyVisible(): Boolean {
        return seekBarView.visibility == View.VISIBLE
    }

    private fun showSeekbar(show: Boolean) {
        val deltaY = context.dp(12)

        val viewToShow = if (show) seekBarView else progressView
        val viewToHide = if (show) progressView else seekBarView

        if (viewToHide.visibility == View.VISIBLE) {
            viewToHide.translationY = 0f
            viewToHide.animate()
                    .alpha(0f)
                    .translationY(deltaY.toFloat())
                    .withEndAction { viewToHide.isVisible = false }
                    .setInterpolator(AccelerateInterpolator())
                    .start()

        }

        if (viewToShow.visibility != View.VISIBLE) {
            if (progressEnabled || progressView !== viewToShow) {
                viewToShow.alpha = 0f
                viewToShow.translationY = deltaY.toFloat()
                viewToShow.visibility = View.VISIBLE
                viewToShow.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setListener(null)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
            }

            onSeekbarVisibilityChanged(show)
        }

        if (show) {
            updateTimeline()
        }
    }

    protected open fun onSeekbarVisibilityChanged(show: Boolean) {
        // can be implemented in a subclass.
    }

    private fun updateInfo(view: ProgressBar, info: ProgressInfo) {
        // do not step back in buffering
        val buffered = info.buffered.coerceAtLeast(maxBufferedValue)
        this.maxBufferedValue = buffered

        val progressTarget = (1000 * info.progress).roundToInt()

        view.max = 1000
        view.secondaryProgress = (1000 * buffered).toInt()
        view.progress = progressTarget
    }

    protected fun updateTimeline() {
        if (!isPlaying)
            return

        val info = currentVideoProgress()
        if (!progressTouched) {
            if (info != null && shouldShowView(info)) {
                if (firstTimeProgressValue && progressEnabled) {
                    firstTimeProgressValue = false
                    progressView.isVisible = true
                    progressView.alpha = 1f
                    progressView.translationY = 0f
                }

                updateInfo(progressView, info)
                updateInfo(seekBarView, info)

                if (userSeekable() && seekHideTimeoutReached()) {
                    logger.debug { "Hiding seekbar after idle timeout" }
                    lastUserInteraction = -1
                    showSeekbar(false)
                }
            } else {
                lastUserInteraction = -1
                firstTimeProgressValue = true
                seekBarView.isVisible = false
                progressView.isVisible = false
            }
        }

        if (progressEnabled || seekCurrentlyVisible()) {
            val duration = info?.duration?.convertTo(TimeUnit.SECONDS)
            val delay = if (duration != null && duration <= 30) 50L else 100L

            removeCallbacks(updateCallback)
            postDelayed(updateCallback, delay)
        }
    }

    private fun seekHideTimeoutReached(): Boolean {
        return seekCurrentlyVisible()
                && lastUserInteraction > 0
                && System.currentTimeMillis() - lastUserInteraction > 3000
    }

    private fun shouldShowView(info: ProgressInfo): Boolean {
        return (progressEnabled || seekCurrentlyVisible()) && (info.progress in 0.0..1.0 || info.buffered in 0.0..1.0)
    }

    /**
     * Implement to seek after user input.
     */
    protected open fun userSeekTo(fraction: Float) {
    }

    /**
     * Disable the little progressbar. We still allow seeking, if the user
     * touches the screen.
     */
    fun hideVideoProgress() {
        progressEnabled = false

        lastUserInteraction = -1
        firstTimeProgressValue = true
        seekBarView.isVisible = false
        progressView.isVisible = false
    }

    class ProgressInfo(val progress: Float, val buffered: Float, val duration: Duration)

    private inner class SeekbarChangeListener : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                lastUserInteraction = System.currentTimeMillis()
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            progressTouched = true
            lastUserInteraction = System.currentTimeMillis()
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            val currentValue = seekBar.progress
            userSeekTo(currentValue / seekBar.max.toFloat())

            progressTouched = false
            lastUserInteraction = System.currentTimeMillis()
        }
    }

    companion object {
        private val logger = Logger("AbstractProgressMediaView")
    }
}

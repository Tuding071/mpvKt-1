package live.mehiz.mpvkt.ui.player.controls

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import live.mehiz.mpvkt.ui.player.PlayerViewModel

class GestureHandler(private val viewModel: PlayerViewModel) {

    private var startingX = 0f
    private var startingY = 0f
    private var startingPos = 0
    private var gestureType: GestureType? = null

    private val pixelsPerStep = 14f  // adjust sensitivity
    private val msPerStep = 111      // how many ms per step
    private val frameUpdateInterval = 333L // ~3 frames per second

    private val handler = Handler(Looper.getMainLooper())
    private var lastFrameUpdate = 0L

    private var isSeeking = false

    enum class GestureType {
        HORIZONTAL, VERTICAL_LEFT, VERTICAL_RIGHT
    }

    fun onTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startingX = event.x
                startingY = event.y
                startingPos = viewModel.currentPosition.value
                gestureType = null
                isSeeking = false
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - startingX
                val deltaY = event.y - startingY

                // detect gesture type if not yet detected
                if (gestureType == null) {
                    gestureType = if (abs(deltaX) > abs(deltaY)) {
                        GestureType.HORIZONTAL
                    } else if (startingX < event.device.widthPixels / 2) {
                        GestureType.VERTICAL_LEFT
                    } else {
                        GestureType.VERTICAL_RIGHT
                    }
                }

                when (gestureType) {
                    GestureType.HORIZONTAL -> handleSeek(event.x)
                    GestureType.VERTICAL_LEFT -> handleBrightness(event.y)
                    GestureType.VERTICAL_RIGHT -> handleVolume(event.y)
                    else -> {}
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSeeking) {
                    // finalize position when user releases
                    viewModel.confirmSeek()
                }
                gestureType = null
                isSeeking = false
            }
        }
        return true
    }

    private fun handleSeek(currentX: Float) {
        isSeeking = true
        val deltaPixels = currentX - startingX
        val steps = (deltaPixels / pixelsPerStep).toInt()
        val deltaMs = (steps * msPerStep)
        val newPosition = (startingPos + deltaMs).coerceAtLeast(0)

        val now = System.currentTimeMillis()
        if (now - lastFrameUpdate > frameUpdateInterval) {
            lastFrameUpdate = now
            viewModel.seekTo(newPosition)
        }
    }

    private fun handleBrightness(currentY: Float) {
        val newBrightness = calculateNewVerticalGestureValue(
            startingValue = viewModel.brightness.value,
            startingY = startingY,
            currentY = currentY,
            sensitivity = 0.01f
        )
        viewModel.setBrightness(newBrightness)
    }

    private fun handleVolume(currentY: Float) {
        val newVolume = calculateNewVerticalGestureValue(
            startingValue = viewModel.volume.value,
            startingY = startingY,
            currentY = currentY,
            sensitivity = 0.05f
        )
        viewModel.setVolume(newVolume)
    }

    // 🧮 Helper functions
    private fun calculateNewHorizontalGestureValue(
        startingPosition: Int,
        startingX: Float,
        currentX: Float,
        sensitivity: Float
    ): Int {
        val delta = (currentX - startingX) * sensitivity
        return (startingPosition + delta).toInt()
    }

    private fun calculateNewVerticalGestureValue(
        startingValue: Int,
        startingY: Float,
        currentY: Float,
        sensitivity: Float
    ): Int {
        val delta = (startingY - currentY) * sensitivity
        val value = (startingValue + delta).toInt()
        return max(0, min(100, value))
    }
}

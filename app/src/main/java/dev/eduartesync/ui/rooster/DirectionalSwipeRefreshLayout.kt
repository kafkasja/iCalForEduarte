package dev.eduartesync.ui.rooster

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs

/**
 * Pull-to-refresh around the day pager that only takes a gesture when it is clearly downward.
 * The plain one starts refreshing as soon as a finger moved a few pixels down, so a sideways
 * swipe to the next day that drifted a little downward turned into a half pull instead.
 * Here the first [touchSlop] of movement decides: mostly sideways belongs to the pager.
 */
class DirectionalSwipeRefreshLayout(context: Context, attrs: AttributeSet? = null) : SwipeRefreshLayout(context, attrs) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var sideways = false
    private var decided = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                sideways = false
                decided = false
            }
            MotionEvent.ACTION_MOVE -> if (!decided) {
                val dx = abs(event.x - startX)
                val dy = abs(event.y - startY)
                if (dx > touchSlop || dy > touchSlop) {
                    decided = true
                    // A pull needs to be clearly more down than sideways.
                    sideways = dy < dx * VERTICAL_RATIO
                }
            }
        }
        if (sideways) return false
        return super.onInterceptTouchEvent(event)
    }

    private companion object {
        const val VERTICAL_RATIO = 1.5f
    }
}

package dev.eduartesync.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.BaseProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.R as MaterialR

/**
 * Shows loading as a thin line along the top of a pull-to-refresh screen. The round
 * pull-to-refresh bubble stays only while the user is pulling: reading the portal can take half
 * a minute, and a bubble hanging over the content all that time looked broken.
 *
 * [attach] puts the line along the top of `over`, the [SwipeRefreshLayout] itself unless the
 * screen has something between its top bar and the list (the Rooster tab's week strip), by
 * wrapping it in a frame that takes over its place and layout params, so scrolling behaviors keep
 * working.
 */
class LoadingLine private constructor(
    private val refresh: SwipeRefreshLayout,
    private val line: LinearProgressIndicator,
) {
    var loading: Boolean = false
        set(value) {
            field = value
            refresh.isRefreshing = false
            if (value) line.show() else line.hide()
        }

    companion object {
        fun attach(refresh: SwipeRefreshLayout, over: View = refresh, onPull: () -> Unit): LoadingLine {
            val context = refresh.context
            refresh.setColorSchemeColors(MaterialColors.getColor(refresh, MaterialR.attr.colorPrimary))
            refresh.setProgressBackgroundColorSchemeColor(MaterialColors.getColor(refresh, MaterialR.attr.colorSurfaceContainerHigh))

            val line = LinearProgressIndicator(context).apply {
                isIndeterminate = true
                trackThickness = (3 * resources.displayMetrics.density).toInt()
                setIndicatorColor(MaterialColors.getColor(refresh, MaterialR.attr.colorPrimary))
                trackColor = android.graphics.Color.TRANSPARENT
                showAnimationBehavior = BaseProgressIndicator.SHOW_INWARD
                hideAnimationBehavior = BaseProgressIndicator.HIDE_INWARD
                visibility = View.INVISIBLE
            }
            val parent = over.parent as ViewGroup
            val index = parent.indexOfChild(over)
            val params = over.layoutParams
            parent.removeViewAt(index)
            val frame = FrameLayout(context).apply {
                id = View.generateViewId()
                addView(over, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(line, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            }
            parent.addView(frame, index, params)

            val loadingLine = LoadingLine(refresh, line)
            refresh.setOnRefreshListener {
                // The pull is done: hand over from the bubble to the line straight away.
                loadingLine.loading = true
                onPull()
            }
            return loadingLine
        }
    }
}

package io.hex128.uproxconcierge

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.use
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.SnackbarContentLayout


@SuppressLint("RestrictedApi")
class PersistentSnackbar private constructor(
    parent: ViewGroup,
    content: SnackbarContentLayout
) : BaseTransientBottomBar<PersistentSnackbar?>(parent, content, content) {
    private val accessibilityManager: AccessibilityManager? =
        parent.context.getSystemService("accessibility") as AccessibilityManager?
    private var hasAction = false

    @Suppress("unused")
    fun setText(message: CharSequence): PersistentSnackbar {
        this.messageView!!.text = message
        return this
    }

    @Suppress("unused")
    fun setText(@StringRes resId: Int): PersistentSnackbar {
        return this.setText(this.context.getText(resId))
    }

    @Suppress("unused")
    fun setAction(@StringRes resId: Int, listener: View.OnClickListener?): PersistentSnackbar {
        return this.setAction(this.context.getText(resId), listener)
    }

    @Suppress("unused")
    fun setAction(text: CharSequence?, listener: View.OnClickListener?): PersistentSnackbar {
        val tv: TextView = this.actionView
        if (!TextUtils.isEmpty(text) && listener != null) {
            this.hasAction = true
            tv.visibility = View.VISIBLE
            tv.text = text
            tv.setOnClickListener { view: View? ->
                listener.onClick(view)
            }
        } else {
            tv.visibility = View.GONE
            tv.setOnClickListener(null as View.OnClickListener?)
            this.hasAction = false
        }

        return this
    }

    override fun getDuration(): Int {
        val userSetDuration = super.getDuration()
        if (userSetDuration == -2) {
            return -2
        } else if (Build.VERSION.SDK_INT >= 29) {
            val controlsFlag = if (this.hasAction) AccessibilityManager.FLAG_CONTENT_CONTROLS else 0
            return this.accessibilityManager!!.getRecommendedTimeoutMillis(
                userSetDuration,
                controlsFlag or
                        AccessibilityManager.FLAG_CONTENT_ICONS or
                        AccessibilityManager.FLAG_CONTENT_TEXT
            )
        } else {
            return if (this.hasAction && this.accessibilityManager!!.isTouchExplorationEnabled) {
                -2
            } else {
                userSetDuration
            }
        }
    }

    @Suppress("unused")
    fun setTextColor(colors: ColorStateList?): PersistentSnackbar {
        this.messageView!!.setTextColor(colors)
        return this
    }

    @Suppress("unused")
    fun setTextColor(@ColorInt color: Int): PersistentSnackbar {
        this.messageView!!.setTextColor(color)
        return this
    }

    @Suppress("unused")
    fun setTextMaxLines(maxLines: Int): PersistentSnackbar {
        this.messageView!!.setMaxLines(maxLines)
        return this
    }

    @Suppress("unused")
    fun setActionTextColor(colors: ColorStateList?): PersistentSnackbar {
        this.actionView.setTextColor(colors)
        return this
    }

    @Suppress("unused")
    fun setMaxInlineActionWidth(@Dimension width: Int): PersistentSnackbar {
        this.contentLayout!!.setMaxInlineActionWidth(width)
        return this
    }

    @Suppress("unused")
    fun setActionTextColor(@ColorInt color: Int): PersistentSnackbar {
        this.actionView.setTextColor(color)
        return this
    }

    @Suppress("unused")
    fun setBackgroundTint(@ColorInt color: Int): PersistentSnackbar {
        return this.setBackgroundTintList(ColorStateList.valueOf(color))
    }

    fun setBackgroundTintList(colorStateList: ColorStateList?): PersistentSnackbar {
        this.view.backgroundTintList = colorStateList
        return this
    }

    @Suppress("unused")
    fun setBackgroundTintMode(mode: PorterDuff.Mode?): PersistentSnackbar {
        this.view.backgroundTintMode = mode
        return this
    }

    private val messageView: TextView?
        get() = this.contentLayout!!.messageView

    private val actionView: Button
        get() = this.contentLayout!!.actionView

    private val contentLayout: SnackbarContentLayout?
        get() = this.view.getChildAt(0) as SnackbarContentLayout?

    private class SnackbarLayout : SnackbarBaseLayout {
        constructor(context: Context) : super(context)

        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val childCount = this.childCount
            val availableWidth =
                this.measuredWidth - this.getPaddingLeft() - this.getPaddingRight()

            for (i in 0..<childCount) {
                val child = this.getChildAt(i)
                if (child.layoutParams.width == -1) {
                    child.measure(
                        MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(child.measuredHeight, MeasureSpec.EXACTLY)
                    )
                }
            }
        }
    }

    companion object {
        private val SNACKBAR_CONTENT_STYLE_ATTRS: IntArray = intArrayOf(
            com.google.android.material.R.attr.snackbarButtonStyle,
            com.google.android.material.R.attr.snackbarTextViewStyle
        )

        fun make(view: View, text: CharSequence, duration: Int): PersistentSnackbar {
            return makeInternal(null as Context?, view, text, duration)
        }

        @Suppress("unused")
        fun make(
            context: Context,
            view: View,
            text: CharSequence,
            duration: Int
        ): PersistentSnackbar {
            return makeInternal(context, view, text, duration)
        }

        private fun makeInternal(
            context: Context?,
            view: View,
            text: CharSequence,
            duration: Int
        ): PersistentSnackbar {
            var context = context
            val parent = findSuitableParent(view)
            requireNotNull(parent != null) {
                "No suitable parent found from the given view. Please provide a valid view."
            }
            if (context == null) {
                context = parent!!.context
            }

            val inflater = LayoutInflater.from(context)
            val content = inflater.inflate(
                if (hasSnackbarContentStyleAttrs(context)) {
                    com.google.android.material.R.layout.mtrl_layout_snackbar_include
                } else {
                    com.google.android.material.R.layout.design_layout_snackbar_include
                },
                parent,
                false
            ) as SnackbarContentLayout
            val snackbar = PersistentSnackbar(parent!!, content)
            snackbar.setText(text)
            snackbar.duration = duration
            return snackbar
        }

        @SuppressLint("ResourceType")
        private fun hasSnackbarContentStyleAttrs(context: Context): Boolean =
            context.obtainStyledAttributes(SNACKBAR_CONTENT_STYLE_ATTRS).use { a ->
                a.hasValue(0) && a.hasValue(1)
            }

        @Suppress("unused")
        fun make(view: View, @StringRes resId: Int, duration: Int): PersistentSnackbar {
            return make(view, view.resources.getText(resId), duration)
        }

        private fun findSuitableParent(view: View?): ViewGroup? {
            var view = view
            var fallback: ViewGroup? = null

            while (view !is CoordinatorLayout) {
                if (view is FrameLayout) {
                    if (view.id == android.R.id.content) {
                        return view as ViewGroup
                    }

                    fallback = view as ViewGroup
                }

                if (view != null) {
                    val parent = view.parent
                    view = if (parent is View) parent as View else null
                }

                if (view == null) {
                    return fallback
                }
            }

            return view as ViewGroup
        }
    }
}

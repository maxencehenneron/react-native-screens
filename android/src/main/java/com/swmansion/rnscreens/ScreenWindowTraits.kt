package com.swmansion.rnscreens

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import android.view.ViewParent
import android.view.WindowManager
import androidx.core.view.ViewCompat
import com.facebook.react.bridge.GuardedRunnable
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.UiThreadUtil
import com.swmansion.rnscreens.Screen.WindowTraits

object ScreenWindowTraits {
    // Methods concerning statusBar management were taken from `react-native`'s status bar module:
    // https://github.com/facebook/react-native/blob/master/ReactAndroid/src/main/java/com/facebook/react/modules/statusbar/StatusBarModule.java
    private var mDidSetOrientation = false
    private var mDidSetStatusBarAppearance = false
    private var mDefaultStatusBarColor: Int? = null
    internal fun applyDidSetOrientation() {
        mDidSetOrientation = true
    }

    internal fun applyDidSetStatusBarAppearance() {
        mDidSetStatusBarAppearance = true
    }

    private fun didSetStatusBarAppearance(): Boolean {
        return mDidSetStatusBarAppearance
    }

    internal fun setOrientation(screen: Screen, activity: Activity?) {
        if (activity == null) {
            return
        }
        val screenForOrientation = findScreenForTrait(screen, WindowTraits.ORIENTATION)
        val orientation: Int? = if (screenForOrientation?.screenOrientation != null) {
            screenForOrientation.screenOrientation
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        activity.requestedOrientation = orientation!!
    }

    internal fun setColor(screen: Screen, activity: Activity?, context: ReactContext?) {
        if (activity == null || context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }
        if (mDefaultStatusBarColor == null) {
            mDefaultStatusBarColor = activity.window.statusBarColor
        }
        val screenForColor = findScreenForTrait(screen, WindowTraits.COLOR)
        val screenForAnimated = findScreenForTrait(screen, WindowTraits.ANIMATED)
        val color: Int? = if (screenForColor?.statusBarColor != null) {
            screenForColor.statusBarColor
        } else {
            mDefaultStatusBarColor
        }
        val animated: Boolean = if (screenForAnimated?.isStatusBarAnimated != null) {
            screenForAnimated.isStatusBarAnimated!!
        } else {
            false
        }
        UiThreadUtil.runOnUiThread(
            object : GuardedRunnable(context) {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                override fun runGuarded() {
                    activity
                        .window
                        .addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    val curColor = activity.window.statusBarColor
                    val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), curColor, color)
                    colorAnimation.addUpdateListener { animator -> activity.window.statusBarColor = (animator.animatedValue as Int) }
                    if (animated) {
                        colorAnimation.setDuration(300).startDelay = 0
                    } else {
                        colorAnimation.setDuration(0).startDelay = 300
                    }
                    colorAnimation.start()
                }
            })
    }

    internal fun setStyle(screen: Screen, activity: Activity?, context: ReactContext?) {
        if (activity == null || context == null) {
            return
        }
        val screenForStyle = findScreenForTrait(screen, WindowTraits.STYLE)
        val style: String? = if (screenForStyle?.statusBarStyle != null) {
            screenForStyle.statusBarStyle
        } else {
            "light"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            UiThreadUtil.runOnUiThread {
                val decorView = activity.window.decorView
                var systemUiVisibilityFlags = decorView.systemUiVisibility
                systemUiVisibilityFlags = if ("dark" == style) {
                    systemUiVisibilityFlags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    systemUiVisibilityFlags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
                decorView.systemUiVisibility = systemUiVisibilityFlags
            }
        }
    }

    internal fun setTranslucent(
        screen: Screen,
        activity: Activity?,
        context: ReactContext?
    ) {
        if (activity == null || context == null) {
            return
        }
        val translucent: Boolean
        val screenForTranslucent = findScreenForTrait(screen, WindowTraits.TRANSLUCENT)
        translucent = if (screenForTranslucent?.isStatusBarTranslucent != null) {
            screenForTranslucent.isStatusBarTranslucent!!
        } else {
            false
        }
        UiThreadUtil.runOnUiThread(
            object : GuardedRunnable(context) {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                override fun runGuarded() {
                    // If the status bar is translucent hook into the window insets calculations
                    // and consume all the top insets so no padding will be added under the status bar.
                    val decorView = activity.window.decorView
                    if (translucent) {
                        decorView.setOnApplyWindowInsetsListener { v, insets ->
                            val defaultInsets = v.onApplyWindowInsets(insets)
                            defaultInsets.replaceSystemWindowInsets(
                                defaultInsets.systemWindowInsetLeft,
                                0,
                                defaultInsets.systemWindowInsetRight,
                                defaultInsets.systemWindowInsetBottom
                            )
                        }
                    } else {
                        decorView.setOnApplyWindowInsetsListener(null)
                    }
                    ViewCompat.requestApplyInsets(decorView)
                }
            })
    }

    internal fun setHidden(screen: Screen, activity: Activity?) {
        if (activity == null) {
            return
        }
        val screenForHidden = findScreenForTrait(screen, WindowTraits.HIDDEN)
        val hidden = if (screenForHidden?.isStatusBarHidden != null) {
            screenForHidden.isStatusBarHidden!!
        } else {
            false
        }
        UiThreadUtil.runOnUiThread {
            if (hidden) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
            } else {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
    }

    internal fun trySetWindowTraits(screen: Screen, activity: Activity?, context: ReactContext?) {
        if (mDidSetOrientation) {
            setOrientation(screen, activity)
        }
        if (didSetStatusBarAppearance()) {
            setColor(screen, activity, context)
            setStyle(screen, activity, context)
            setTranslucent(screen, activity, context)
            setHidden(screen, activity)
        }
    }

    private fun findScreenForTrait(screen: Screen, trait: WindowTraits): Screen? {
        val childWithTrait = childScreenWithTraitSet(screen, trait)
        if (childWithTrait != null) {
            return childWithTrait
        }
        return if (checkTraitForScreen(screen, trait)) {
            screen
        } else {
            // if there is no child with trait set and this screen has no trait set, we look for a parent
            // that has the trait set
            findParentWithTraitSet(screen, trait)
        }
    }

    private fun findParentWithTraitSet(screen: Screen, trait: WindowTraits): Screen? {
        var parent: ViewParent? = screen.container
        while (parent != null) {
            if (parent is Screen) {
                if (checkTraitForScreen(parent, trait)) {
                    return parent
                }
            }
            parent = parent.parent
        }
        return null
    }

    private fun childScreenWithTraitSet(
        screen: Screen?,
        trait: WindowTraits
    ): Screen? {
        if (screen?.fragment == null) {
            return null
        }
        for (sc in screen.fragment!!.childScreenContainers) {
            // we check only the top screen for the trait
            val topScreen = sc.topScreen
            val child = childScreenWithTraitSet(topScreen, trait)
            if (child != null) {
                return child
            }
            if (topScreen != null && checkTraitForScreen(topScreen, trait)) {
                return topScreen
            }
        }
        return null
    }

    private fun checkTraitForScreen(screen: Screen, trait: WindowTraits): Boolean {
        return when (trait) {
            WindowTraits.ORIENTATION -> screen.screenOrientation != null
            WindowTraits.COLOR -> screen.statusBarColor != null
            WindowTraits.STYLE -> screen.statusBarStyle != null
            WindowTraits.TRANSLUCENT -> screen.isStatusBarTranslucent != null
            WindowTraits.HIDDEN -> screen.isStatusBarHidden != null
            WindowTraits.ANIMATED -> screen.isStatusBarAnimated != null
        }
    }
}

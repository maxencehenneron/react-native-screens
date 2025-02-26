package com.swmansion.rnscreens

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.uimanager.UIManagerModule
import com.facebook.react.uimanager.events.Event
import com.swmansion.rnscreens.events.ScreenAppearEvent
import com.swmansion.rnscreens.events.ScreenDisappearEvent
import com.swmansion.rnscreens.events.ScreenDismissedEvent
import com.swmansion.rnscreens.events.ScreenWillAppearEvent
import com.swmansion.rnscreens.events.ScreenWillDisappearEvent

open class ScreenFragment : Fragment {
    enum class ScreenLifecycleEvent {
        Appear, WillAppear, Disappear, WillDisappear
    }

    var screen: Screen? = null
        protected set
    private val mChildScreenContainers: MutableList<ScreenContainer<*>> = ArrayList()
    private var shouldUpdateOnResume = false

    constructor() {
        throw IllegalStateException(
            "Screen fragments should never be restored. Follow instructions from https://github.com/software-mansion/react-native-screens/issues/17#issuecomment-424704067 to properly configure your main activity."
        )
    }

    @SuppressLint("ValidFragment")
    constructor(screenView: Screen?) : super() {
        screen = screenView
    }

    override fun onResume() {
        super.onResume()
        if (shouldUpdateOnResume) {
            shouldUpdateOnResume = false
            screen?.let { ScreenWindowTraits.trySetWindowTraits(it, tryGetActivity(), tryGetContext()) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val wrapper = FrameLayout(context!!)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        screen!!.layoutParams = params
        wrapper.addView(recycleView(screen))
        return wrapper
    }

    open fun onContainerUpdate() {
        updateWindowTraits()
    }

    private fun updateWindowTraits() {
        val activity: Activity? = activity
        if (activity == null) {
            shouldUpdateOnResume = true
            return
        }
        screen?.let { ScreenWindowTraits.trySetWindowTraits(it, activity, tryGetContext()) }
    }

    fun tryGetActivity(): Activity? {
        if (activity != null) {
            return activity
        }
        val context = screen!!.context
        if (context is ReactContext && context.currentActivity != null) {
            return context.currentActivity
        }
        var parent: ViewParent? = screen!!.container
        while (parent != null) {
            if (parent is Screen) {
                val fragment = parent.fragment
                if (fragment != null && fragment.activity != null) {
                    return fragment.activity
                }
            }
            parent = parent.parent
        }
        return null
    }

    fun tryGetContext(): ReactContext? {
        if (context is ReactContext) {
            return context as ReactContext?
        }
        if (screen!!.context is ReactContext) {
            return screen!!.context as ReactContext
        }
        var parent: ViewParent? = screen!!.container
        while (parent != null) {
            if (parent is Screen) {
                if (parent.context is ReactContext) {
                    return parent.context as ReactContext
                }
            }
            parent = parent.parent
        }
        return null
    }

    val childScreenContainers: List<ScreenContainer<*>>
        get() = mChildScreenContainers

    fun dispatchOnWillAppear() {
        dispatchEvent(ScreenLifecycleEvent.WillAppear, this)
    }

    fun dispatchOnAppear() {
        dispatchEvent(ScreenLifecycleEvent.Appear, this)
    }

    protected fun dispatchOnWillDisappear() {
        dispatchEvent(ScreenLifecycleEvent.WillDisappear, this)
    }

    protected fun dispatchOnDisappear() {
        dispatchEvent(ScreenLifecycleEvent.Disappear, this)
    }

    private fun dispatchEvent(event: ScreenLifecycleEvent, fragment: ScreenFragment?) {
        val screen = fragment!!.screen
        val lifecycleEvent: Event<*> = when (event) {
            ScreenLifecycleEvent.WillAppear -> ScreenWillAppearEvent(screen!!.id)
            ScreenLifecycleEvent.Appear -> ScreenAppearEvent(screen!!.id)
            ScreenLifecycleEvent.WillDisappear -> ScreenWillDisappearEvent(screen!!.id)
            ScreenLifecycleEvent.Disappear -> ScreenDisappearEvent(screen!!.id)
        }
        (screen.context as ReactContext)
            .getNativeModule(UIManagerModule::class.java)
            ?.eventDispatcher
            ?.dispatchEvent(lifecycleEvent)
        fragment.dispatchEventInChildContainers(event)
    }

    private fun dispatchEventInChildContainers(event: ScreenLifecycleEvent) {
        for (sc in mChildScreenContainers) {
            if (sc.screenCount > 0) {
                val topScreen = sc.topScreen
                if (topScreen?.fragment != null && (topScreen.stackAnimation !== Screen.StackAnimation.NONE || isRemoving)) {
                    // we do not dispatch events in child when it has `none` animation
                    // and we are going forward since then they will be dispatched in child via
                    // `onCreateAnimation` of ScreenStackFragment
                    dispatchEvent(event, topScreen.fragment)
                }
            }
        }
    }

    fun registerChildScreenContainer(screenContainer: ScreenContainer<*>) {
        mChildScreenContainers.add(screenContainer)
    }

    fun unregisterChildScreenContainer(screenContainer: ScreenContainer<*>) {
        mChildScreenContainers.remove(screenContainer)
    }

    fun onViewAnimationStart() {
        // onViewAnimationStart is triggered from View#onAnimationStart method of the fragment's root
        // view. We override Screen#onAnimationStart and an appropriate method of the StackFragment's
        // root view in order to achieve this.
        if (isResumed) {
            // Android dispatches the animation start event for the fragment that is being added first
            // however we want the one being dismissed first to match iOS. It also makes more sense from
            // a navigation point of view to have the disappear event first.
            // Since there are no explicit relationships between the fragment being added / removed the
            // practical way to fix this is delaying dispatching the appear events at the end of the
            // frame.
            UiThreadUtil.runOnUiThread { dispatchOnWillAppear() }
        } else {
            dispatchOnWillDisappear()
        }
    }

    open fun onViewAnimationEnd() {
        // onViewAnimationEnd is triggered from View#onAnimationEnd method of the fragment's root view.
        // We override Screen#onAnimationEnd and an appropriate method of the StackFragment's root view
        // in order to achieve this.
        if (isResumed) {
            // See the comment in onViewAnimationStart for why this event is delayed.
            UiThreadUtil.runOnUiThread { dispatchOnAppear() }
        } else {
            dispatchOnDisappear()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val container = screen!!.container
        if (container == null || !container.hasScreen(this)) {
            // we only send dismissed even when the screen has been removed from its container
            (screen!!.context as ReactContext)
                .getNativeModule(UIManagerModule::class.java)
                ?.eventDispatcher
                ?.dispatchEvent(ScreenDismissedEvent(screen!!.id))
        }
        mChildScreenContainers.clear()
    }

    companion object {
        @JvmStatic
        protected fun recycleView(view: View?): View {
            // screen fragments reuse view instances instead of creating new ones. In order to reuse a given
            // view it needs to be detached from the view hierarchy to allow the fragment to attach it back.
            val parent = view!!.parent
            if (parent != null) {
                (parent as ViewGroup).endViewTransition(view)
                parent.removeView(view)
            }

            // view detached from fragment manager get their visibility changed to GONE after their state is
            // dumped. Since we don't restore the state but want to reuse the view we need to change
            // visibility back to VISIBLE in order for the fragment manager to animate in the view.
            view.visibility = View.VISIBLE
            return view
        }
    }
}

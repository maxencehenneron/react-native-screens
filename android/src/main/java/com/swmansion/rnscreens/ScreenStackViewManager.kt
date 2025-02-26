package com.swmansion.rnscreens

import android.view.View
import android.view.ViewGroup
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager

@ReactModule(name = ScreenStackViewManager.REACT_CLASS)
class ScreenStackViewManager : ViewGroupManager<ScreenStack>() {
    override fun getName(): String {
        return REACT_CLASS
    }

    override fun createViewInstance(reactContext: ThemedReactContext): ScreenStack {
        return ScreenStack(reactContext)
    }

    override fun addView(parent: ScreenStack, child: View, index: Int) {
        require(child is Screen) { "Attempt attach child that is not of type RNScreen" }
        parent.addScreen(child, index)
    }

    override fun removeViewAt(parent: ScreenStack, index: Int) {
        prepareOutTransition(parent.getScreenAt(index))
        parent.removeScreenAt(index)
    }

    private fun prepareOutTransition(screen: Screen?) {
        startTransitionRecursive(screen)
    }

    private fun startTransitionRecursive(parent: ViewGroup?) {
        var i = 0
        val size = parent!!.childCount
        while (i < size) {
            val child = parent.getChildAt(i)
            parent.startViewTransition(child)
            if (child is ScreenStackHeaderConfig) {
                // we want to start transition on children of the toolbar too,
                // which is not a child of ScreenStackHeaderConfig
                startTransitionRecursive(child.toolbar)
            }
            if (child is ViewGroup) {
                startTransitionRecursive(child)
            }
            i++
        }
    }

    override fun getChildCount(parent: ScreenStack): Int {
        return parent.screenCount
    }

    override fun getChildAt(parent: ScreenStack, index: Int): View {
        return parent.getScreenAt(index)!!
    }

    override fun needsCustomLayoutForChildren(): Boolean {
        return true
    }

    companion object {
        const val REACT_CLASS = "RNSScreenStack"
    }
}

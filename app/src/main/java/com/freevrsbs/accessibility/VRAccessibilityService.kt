package com.freevrsbs.accessibility
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
class VRAccessibilityService: AccessibilityService() {
 override fun onServiceConnected(){ serviceInfo=serviceInfo.apply { flags=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS } }
 override fun onAccessibilityEvent(event:AccessibilityEvent?) {}
 override fun onInterrupt() {}
 fun clickFocused():Boolean { val node=rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY); return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true }
 fun global(action:Int)=performGlobalAction(action)
}

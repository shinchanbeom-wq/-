package com.freevrsbs.input
import android.view.KeyEvent
import android.view.MotionEvent
class InputEventMonitor(private val report:(String)->Unit) { fun onKey(e:KeyEvent):Boolean { report("${e.device?.name ?: ""}: ${KeyEvent.keyCodeToString(e.keyCode)}"); return false }; fun onMotion(e:MotionEvent):Boolean { report("${e.device?.name ?: ""}: ${MotionEvent.actionToString(e.actionMasked)}"); return false } }

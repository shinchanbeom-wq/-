package com.freevrsbs.bluetooth
import android.content.Context
import android.view.InputDevice
import android.util.Log
data class BluetoothInputDevice(val id:Int,val name:String,val type:String,val connected:Boolean)
class BluetoothInputManager(private val context:Context) {
 fun devices():List<BluetoothInputDevice> = InputDevice.getDeviceIds().mapNotNull { id -> InputDevice.getDevice(id)?.let { d -> if((d.sources and InputDevice.SOURCE_KEYBOARD)!=0 || (d.sources and InputDevice.SOURCE_MOUSE)!=0 || (d.sources and InputDevice.SOURCE_GAMEPAD)!=0) BluetoothInputDevice(id,d.name,kind(d),true) else null } }
 private fun kind(d:InputDevice):String = when { d.sources and InputDevice.SOURCE_MOUSE != 0 -> context.getString(com.freevrsbs.R.string.hid_mouse); d.sources and InputDevice.SOURCE_KEYBOARD != 0 -> context.getString(com.freevrsbs.R.string.hid_keyboard); else -> context.getString(com.freevrsbs.R.string.hid_device) }
}
class BluetoothDeviceAdapter { fun labels(devices:List<BluetoothInputDevice>)=devices.map { "${it.name}\n${it.type}" } }

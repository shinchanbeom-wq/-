package com.freevrsbs.bluetooth

import android.content.Context
import android.view.InputDevice
import com.freevrsbs.R

data class BluetoothInputDevice(
    val id: Int,
    val name: String,
    val type: String,
    val connected: Boolean
)

/** Lists HID devices that Android has exposed through the public input-device API. */
class BluetoothInputManager(private val context: Context) {
    fun devices(): List<BluetoothInputDevice> = buildList {
        // getDeviceIds() returns an IntArray. Iterate it directly because primitive arrays
        // do not provide mapNotNull in all Kotlin Android toolchains.
        for (deviceId in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(deviceId) ?: continue
            if (!isSupportedHid(device)) continue
            add(
                BluetoothInputDevice(
                    id = deviceId,
                    name = device.name ?: context.getString(R.string.unknown_device),
                    type = kind(device),
                    connected = true
                )
            )
        }
    }

    private fun isSupportedHid(device: InputDevice): Boolean {
        val sources = device.sources
        return (sources and InputDevice.SOURCE_KEYBOARD) != 0 ||
            (sources and InputDevice.SOURCE_MOUSE) != 0 ||
            (sources and InputDevice.SOURCE_GAMEPAD) != 0 ||
            (sources and InputDevice.SOURCE_DPAD) != 0
    }

    private fun kind(device: InputDevice): String = when {
        (device.sources and InputDevice.SOURCE_MOUSE) != 0 -> context.getString(R.string.hid_mouse)
        (device.sources and InputDevice.SOURCE_KEYBOARD) != 0 -> context.getString(R.string.hid_keyboard)
        else -> context.getString(R.string.hid_device)
    }
}

class BluetoothDeviceAdapter {
    fun labels(devices: List<BluetoothInputDevice>): List<String> =
        devices.map { device -> "${device.name}\n${device.type}" }
}

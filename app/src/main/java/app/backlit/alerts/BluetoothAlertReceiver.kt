package app.backlit.alerts

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper

/** A chosen Bluetooth device connected → play its animation (~3 s). */
class BluetoothAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val pending = goAsync()
        AlertsRuntime.get(context).onDeviceConnected(device.address)
        Handler(Looper.getMainLooper()).postDelayed({ pending.finish() }, 3_500)
    }
}

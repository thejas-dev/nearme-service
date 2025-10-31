package com.xenikii.nearby_service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.os.Build
import io.flutter.plugin.common.EventChannel

/** Receiver of [WifiP2pManager] changes. */
@Suppress("DEPRECATION")
class NearbyServiceBroadcastReceiver(
        private val wifiManager: WifiP2pManager,
        private val wifiChannel: Channel,
        private val permissionsHandler: NearbyServicePermissionsHandler,
) : BroadcastReceiver() {
    var peers: MutableList<String> = mutableListOf()
    var connectedDevice: WifiP2pDevice? = null
    var currentDevice: WifiP2pDevice? = null
    var wifiInfo: WifiP2pInfo? = null
    var networkInfo: NetworkInfo? = null
    var currentConnectionState: String? = null
    private var popupNotificationSink: EventChannel.EventSink? = null

    override fun onReceive(context: Context, intent: Intent) {
        Logger.i("Received action ${intent.action?.replace("android.net.wifi.p2p.", "")}")

        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                logState(intent)
                writeConnectionInfo()
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                writeDevices { intent.getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST) }
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                writeConnectionStateChange(intent)
                writeConnectionInfo()
            }
            WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                writeConnectionInfo()
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                writeCurrentDevice(intent)
            }
        }
    }

    fun init() {
        writeDevices()
        writeConnectionInfo()
    }

    private fun logState(intent: Intent) {
        when (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)) {
            WifiP2pManager.WIFI_P2P_STATE_ENABLED -> {
                Logger.d("P2P state enabled")
            }
            else -> {
                Logger.d("P2P state disabled")
            }
        }
    }

    private fun writeConnectionStateChange(intent: Intent) {
        // NetworkInfo is deprecated in API level 29 and above
        // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
        Logger.d("NetworkInfo: $networkInfo")
        this.currentConnectionState = networkInfo?.detailedState.toString()

        if (networkInfo?.detailedState == NetworkInfo.DetailedState.CONNECTING) {
            Logger.d("⚠️ CONNECTION DIALOG IS LIKELY SHOWING")
            notifyPopupShown()
        }
    }

    private fun notifyPopupShown() {
        val popupData = mapOf(
            "type" to "connection_dialog",
            "message" to "Connection dialog is showing",
            "timestamp" to System.currentTimeMillis(),
            "connectionState" to currentConnectionState
        )
        
        popupNotificationSink?.success(popupData)
        Logger.d("Popup notification sent to Flutter: $popupData")
    }

    fun setPopupNotificationSink(sink: EventChannel.EventSink?) {
        popupNotificationSink = sink
    }

    private fun writeCurrentDevice(intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            currentDevice = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
        }
    }

    private fun writeDevices(deviceListSupplier: (() -> WifiP2pDeviceList?)? = null) {
        try {
            wifiManager.requestPeers(wifiChannel) { data: WifiP2pDeviceList ->
                val list: MutableList<String> = mutableListOf()

                val newPeers =
                        if (data.deviceList.isEmpty() && deviceListSupplier != null) {
                            deviceListSupplier() ?: data
                        } else {
                            data
                        }
                if (newPeers.deviceList.isEmpty() && connectedDevice != null) {
                    connectedDevice = null
                }
                for (device: WifiP2pDevice in newPeers.deviceList) {
                    list.add(device.toJsonString())
                    if (device.status == WifiP2pDevice.CONNECTED) {
                        connectedDevice = device
                    } else if (device.deviceAddress == connectedDevice?.deviceAddress &&
                                    device.status != WifiP2pDevice.CONNECTED
                    ) {
                        connectedDevice = null
                    }
                }
                peers = list
            }
        } catch (e: SecurityException) {
            if (!permissionsHandler.checkPermissions()) {
                Logger.e("No permission to call 'writeDevices'")
                permissionsHandler.requestPermissions()
            }
        }
    }

    private fun writeConnectionInfo() {
        wifiManager.requestConnectionInfo(wifiChannel) { info: WifiP2pInfo ->
            Logger.d("ConnectionInfo: $info")
            if (!info.groupFormed && wifiInfo?.groupFormed == true) {
                writeDevices()
            }
            wifiInfo = info
            if (info.isGroupOwner && peers.isEmpty())
                    try {
                        wifiManager.requestGroupInfo(wifiChannel) { group: WifiP2pGroup ->
                            peers =
                                    group.clientList
                                            .map { t: WifiP2pDevice -> t.toJsonString() }
                                            .toMutableList()
                        }
                    } catch (e: SecurityException) {
                        e.message?.let { Logger.e(it) }
                    }
        }
    }
}

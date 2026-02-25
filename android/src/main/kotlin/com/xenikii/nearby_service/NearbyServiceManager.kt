package com.xenikii.nearby_service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiAvailableChannel
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.future.await

/** General Manager of Wi-fi Direct local network operations. */
class NearbyServiceManager(private var context: Context) {
    private lateinit var wifiManager: WifiP2pManager
    private lateinit var wifiChannel: WifiP2pManager.Channel
    private lateinit var receiver: NearbyServiceBroadcastReceiver

    private val intentFilter = IntentFilter()
    private var permissionsHandler = NearbyServicePermissionsHandler(context)
    private var activityPluginBinding: ActivityPluginBinding? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var resetInProgress = false

    /**
     * Sets [binding] to [activityPluginBinding] and [permissionsHandler]. Adds permissions result
     * listener to [binding].
     */
    fun setBinding(binding: ActivityPluginBinding) {
        activityPluginBinding = binding
        activityPluginBinding?.addRequestPermissionsResultListener(permissionsHandler)
        permissionsHandler.activity = binding.activity
    }

    /**
     * Removes permissions result listener to [activityPluginBinding]. Sets [activityPluginBinding]
     * to null.
     */
    fun removeBinding() {
        activityPluginBinding?.removeRequestPermissionsResultListener(permissionsHandler)
        activityPluginBinding = null
    }

    /** Initializes everything for [WifiManager] to work. */
    fun initialize(result: Result, logLevel: String) {
        Logger.level = LogLevel.valueOf(logLevel.uppercase())

        addWifiActions()
        initWifiManager()
        initReceiver()
        result.success(true)
    }

    /** Requesting permissions with [permissionsHandler]. */
    suspend fun requestPermissions(): Boolean {
        return permissionsHandler.requestPermissionsAsync().await()
    }

    /** Checking if Wi-fi is enabled now. */
    fun checkWifiService(result: Result) {
        result.success(
                (context.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
        )
    }

    /**
     * Returns info about a current device in format WifiP2pDevice.toJsonString().
     *
     * Note! The field **deviceAddress** will always be 02:00:00:00:00:00 for privacy issues.
     *
     * Note! If the SDK version is less than 29 (Q), tries to return a current device from
     * [receiver]. It also may be null.
     */
    fun getCurrentDevice(result: Result) {
        if (!checkInitialization(result)) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wifiManager.requestDeviceInfo(wifiChannel) { device ->
                    result.success(device?.toJsonString())
                }
            } else {
                result.success(receiver.currentDevice?.toJsonString())
            }
        } catch (e: SecurityException) {
            if (!permissionsHandler.checkPermissions()) {
                Logger.e("No permission to call 'getCurrentDevice'")
                permissionsHandler.requestPermissions()
                result.success(null)
            }
        }
    }

    /** Opens the phone settings under Wi-fi. */
    fun openWifiSettings(result: Result) {
        activityPluginBinding?.activity?.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        result.success(true)
    }

    /**
     * Renames the current Wi-Fi Direct device.
     *
     * @param result MethodChannel.Result to send the operation result.
     * @param newName New device name to be set.
     */
    fun renameDevice(result: Result, newName: String) {
        if (!checkInitialization(result)) return

        try {

            val method =
                    wifiManager.javaClass.getMethod(
                            "setDeviceName",
                            wifiChannel.javaClass,
                            String::class.java,
                            getActionListener(
                                            result,
                                            "Device renamed method retrieved successfully for $newName",
                                            "Failed to retrieve setDeviceName method"
                                    )::class
                                    .java
                    )

            method.invoke(
                    wifiManager,
                    wifiChannel,
                    newName,
                    getActionListener(
                            result,
                            "Device renamed successfully to $newName",
                            "Failed to rename device"
                    )
            )
        } catch (e: NoSuchMethodException) {
            result.error(
                    "NOT_SUPPORTED",
                    "setDeviceName not available on this Android version",
                    null
            )
        } catch (e: Exception) {
            result.error("ERROR", "Unexpected error while renaming: ${e.message}", null)
        }
    }

    /**
     * Adds a local service for Wi-fi Direct service discovery.
     *
     * @param result MethodChannel.Result to send the operation result.
     * @param serviceName Name of the service.
     * @param serviceType Type of the service (e.g., "_presence._tcp").
     * @param txtRecord Map of TXT record attributes.
     */
    fun addLocalService(
            result: Result,
            serviceName: String,
            serviceType: String,
            txtRecord: Map<String, String>
    ) {
        if (!checkInitialization(result)) return

        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(serviceName, serviceType, txtRecord)
        try {
            wifiManager.clearLocalServices(wifiChannel, null)
            wifiManager.addLocalService(
                    wifiChannel,
                    serviceInfo,
                    getActionListener(
                            result,
                            "Local service added successfully",
                            "Failed to add local service"
                    )
            )
        } catch (e: SecurityException) {
            if (!permissionsHandler.checkPermissions()) {
                Logger.e("No permission to call 'addLocalService'")
                permissionsHandler.requestPermissions()
            }
        }
    }

    /** Removes all local services added by [addLocalService]. */
    fun removeLocalServices(result: Result) {
        if (!checkInitialization(result)) return

        try {
            wifiManager.clearLocalServices(
                    wifiChannel,
                    getActionListener(
                            result,
                            "All local services removed successfully",
                            "Failed to remove local services"
                    )
            )
        } catch (e: SecurityException) {
            if (!permissionsHandler.checkPermissions()) {
                Logger.e("No permission to call 'removeLocalServices'")
                permissionsHandler.requestPermissions()
            }
        }
    }

    /**
     * Start discovery for peers in Wi-fi Direct scope.
     *
     * Note! All permissions from [NearbyServicePermissionsHandler] are required.
     */
    fun discover(result: Result) {
        if (!checkInitialization(result)) return

        try {
            wifiManager.discoverPeers(
                    wifiChannel,
                    getActionListener(
                            result,
                            "Discovery has started successfully!",
                            "Discovery starting failed"
                    )
            )
        } catch (e: SecurityException) {
            if (!permissionsHandler.checkPermissions()) {
                Logger.e("No permission to call 'discover'")
                permissionsHandler.requestPermissions()
            }
        }
    }

    /**
     * Start fast peer discovery on a specific frequency channel. Requires API level 33+ and
     * channel-constrained discovery support.
     *
     * @param result MethodChannel.Result to send the operation result.
     * @param frequencyMhz The frequency in MHz to scan (e.g., 5200, 5220, 5240).
     */
    fun discoverPeersOnFrequency(result: Result, frequencyMhz: Int) {
        if (!checkInitialization(result)) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Logger.d("Frequency-based discovery requires API 33+, falling back to normal discovery")
            discover(result)
            return
        }

        try {
            // Check if channel-constrained discovery is supported
            val isSupported = wifiManager.isChannelConstrainedDiscoverySupported()

            if (!isSupported) {
                Logger.i(
                        "Channel-constrained discovery not supported, falling back to normal discovery"
                )
                // Fallback to regular discovery
                discover(result)
                return
            }

            wifiManager.discoverPeersOnSpecificFrequency(
                    wifiChannel,
                    frequencyMhz,
                    getActionListener(
                            result,
                            "Discovery started on $frequencyMhz MHz",
                            "Discovery failed on $frequencyMhz MHz"
                    )
            )
        } catch (e: SecurityException) {
            if (!permissionsHandler.checkPermissions()) {
                Logger.e("No permission for frequency-based discovery")
                permissionsHandler.requestPermissions()
            }
        } catch (e: UnsupportedOperationException) {
            Logger.d("Frequency-based discovery not supported, falling back")
            discover(result)
        } catch (e: Exception) {
            Logger.e("Error in frequency-based discovery: ${e.message}")
            discover(result)
        }
    }

    /**
     * Checks if channel-constrained discovery is supported on this device. This feature is required
     * for [discoverPeersOnFrequency] to work.
     *
     * Requires API level 33+.
     */
    fun isChannelConstrainedDiscoverySupported(result: Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Logger.d("Channel-constrained discovery not supported: requires API 33+")
            result.success(false)
            return
        }

        if (!checkInitialization(result)) return

        try {
            val isSupported = wifiManager.isChannelConstrainedDiscoverySupported()
            Logger.d("Channel-constrained discovery supported: $isSupported")
            result.success(isSupported)
        } catch (e: SecurityException) {
            Logger.e("SecurityException checking channel-constrained discovery: ${e.message}")
            if (!permissionsHandler.checkPermissions()) {
                permissionsHandler.requestPermissions()
            }
            result.success(false)
        } catch (e: Exception) {
            Logger.e("Error checking channel-constrained discovery: ${e.message}")
            result.success(false)
        }
    }

    /** Stop discovery for peers in Wi-fi Direct scope. */
    fun stopDiscovery(result: Result) {
        if (!checkInitialization(result)) return

        wifiManager.stopPeerDiscovery(
                wifiChannel,
                getActionListener(
                        result,
                        "Discovery has successfully stopped",
                        "Discovery stopping failed"
                )
        )
    }

    /** Returns peers from [NearbyServiceBroadcastReceiver]. */
    fun getPeers(result: Result) {
        if (!checkInitialization(result)) return

        result.success(receiver.peers)
    }

    /** Returns connection info from [NearbyServiceBroadcastReceiver] in json string. */
    fun getConnectionInfo(result: Result) {
        if (!checkInitialization(result)) return

        val info = receiver.wifiInfo?.toJsonString()
        result.success(info)
    }

    /**
     * Gets the operating frequency of the WiFi Direct group in MHz.
     *
     * @param result MethodChannel.Result to send the operation result.
     * Returns the frequency in MHz if a group is formed, null otherwise.
     */
    fun getGroupOperatingFrequency(result: Result) {
        if (!checkInitialization(result)) return

        try {
            wifiManager.requestGroupInfo(wifiChannel) { group ->
                if (group != null) {
                    val frequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        group.getFrequency()
                    } else {
                        // For API < 29, frequency information is not available
                        null
                    }
                    Logger.i("Group operating frequency: ${frequency ?: "Not available (API < 29)"}")
                    result.success(frequency)
                } else {
                    Logger.w("No WiFi Direct group is currently formed")
                    result.success(null)
                }
            }
        } catch (e: SecurityException) {
            if (!permissionsHandler.checkPermissions()) {
                Logger.e("No permission to call 'getGroupOperatingFrequency'")
                permissionsHandler.requestPermissions()
                result.success(null)
            }
        } catch (e: Exception) {
            Logger.e("Error in getGroupOperatingFrequency: ${e.message}")
            result.error("ERROR", "Failed to get group operating frequency: ${e.message}", null)
        }
    }

    /**
     * Creates a WiFi Direct group with optional operating frequency.
     *
     * @param result MethodChannel.Result to send the operation result.
     * @param frequencyMhz Optional operating frequency in MHz (requires API 29+).
     */
    fun createGroup(result: Result, frequencyMhz: Int? = null) {
        if (!checkInitialization(result)) return

        val config =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && frequencyMhz != null) {
                    try {
                        WifiP2pConfig.Builder()
                                .setNetworkName("DIRECT-mira-${System.currentTimeMillis() % 10000}")
                                .setPassphrase("mira1234")
                                .setGroupOperatingFrequency(frequencyMhz)
                                .build()
                    } catch (e: IllegalArgumentException) {
                        Logger.w("Invalid frequency $frequencyMhz MHz, using default")
                        WifiP2pConfig()
                    }
                } else {
                    WifiP2pConfig()
                }

        val actionListener =
                getActionListener(
                        result,
                        "Group created ${if (frequencyMhz != null) "on $frequencyMhz MHz" else ""}",
                        "Group creation failed"
                )

        wifiManager.createGroup(wifiChannel, config, actionListener)
    }

    /**
     * Builds and returns the SSID (network name) from device ID.
     *
     * @param deviceId Device ID to include in the network name.
     * @return SSID string in the format: "DIRECT-mira-<deviceId>DdDxaK21BA"
     */
    fun buildSSIDFromDeviceId(deviceId: String): String {
        return "DIRECT-mira-$deviceId" + "DdDxaK21BA"
    }

    /**
     * Creates a WiFi Direct group from device ID with specified passphrase.
     *
     * @param result MethodChannel.Result to send the operation result.
     * @param deviceId Device ID to include in the network name.
     * @param passphrase Passphrase for the network. Defaults to "KhJ10287SbGa" if not provided.
     * @param frequency Operating frequency in MHz. Defaults to 5765 (5GHz) if not provided. (Ignored - use use5GHz instead)
     * @param use5GHz If true, sets 5GHz operating band, otherwise 2.4GHz. Defaults to true.
     */
    fun createGroupFromDeviceId(
            result: Result,
            deviceId: String,
            passphrase: String = "KhJ10287SbGa",
            frequency: Int? = null,
            use5GHz: Boolean = true
    ) {
        if (!checkInitialization(result)) return

        try {
            val networkName = buildSSIDFromDeviceId(deviceId)
            val operatingBand = if (use5GHz) {
                WifiP2pConfig.GROUP_OWNER_BAND_5GHZ
            } else {
                WifiP2pConfig.GROUP_OWNER_BAND_2GHZ
            }

            val config =
                    WifiP2pConfig.Builder()
                            .setNetworkName(networkName)
                            .setPassphrase(passphrase)
                            .setGroupOperatingBand(operatingBand)
                            .enablePersistentMode(false)
                            .build()

            val actionListener =
                    getActionListener(
                            result,
                            "Group created from device ID: $deviceId",
                            "Group creation from device ID failed"
                    )

            wifiManager.createGroup(wifiChannel, config, actionListener)
        } catch (e: SecurityException) {
            if (!permissionsHandler.checkPermissions()) {
                Logger.e("No permission to call 'createGroupFromDeviceId'")
                permissionsHandler.requestPermissions()
            }
        } catch (e: Exception) {
            Logger.e("Error in createGroupFromDeviceId: ${e.message}")
            result.error("ERROR", "Failed to create group from device ID: ${e.message}", null)
        }
    }

    fun removeGroup(result: Result) {
        if (!checkInitialization(result)) return

        val actionListener =
                getActionListener(result, "Group removed successfully", "Failed to remove group")

        wifiManager.removeGroup(wifiChannel, actionListener)
    }

    /** Connects to provided [deviceAddress] in Wi-fi Direct scope. */
    fun connect(result: Result, deviceAddress: String, isGroupOwner: Boolean) {
        if (!checkInitialization(result)) return

        if (receiver.connectedDevice?.deviceAddress == deviceAddress) {
            Logger.i("Already connected to the device $deviceAddress")
            result.success(true)
            return
        }

        val config =
                WifiP2pConfig().apply {
                    this.deviceAddress = deviceAddress
                    wps.setup = WpsInfo.PBC
                    groupOwnerIntent = if (isGroupOwner) 15 else 0
                }

        val actionListener =
                getActionListener(
                        result,
                        "Connection request sent to $deviceAddress",
                        "Connection to $deviceAddress failed"
                )

        try {
            wifiChannel.also { wifiChannel: WifiP2pManager.Channel ->
                wifiManager.connect(wifiChannel, config, actionListener)
            }
        } catch (e: SecurityException) {
            if (!permissionsHandler.checkPermissions()) {
                Logger.e("No permission to call 'connect'")
                permissionsHandler.requestPermissions()
            }
        }
    }

    /**
     * Connects to a device using device ID and passphrase.
     *
     * @param result MethodChannel.Result to send the operation result.
     * @param deviceId Device ID to build SSID from.
     * @param passphrase Passphrase for the network. Defaults to "KhJ10287SbGa" if not provided.
     * @param frequency Operating frequency in MHz. Defaults to 5765 (5GHz) if not provided. (Ignored - use use5GHz instead)
     * @param use5GHz If true, sets 5GHz operating band, otherwise 2.4GHz. Defaults to true.
     */
    fun connectWithDeviceId(result: Result, deviceId: String, passphrase: String = "KhJ10287SbGa", frequency: Int? = null, use5GHz: Boolean = true) {
        if (!checkInitialization(result)) return

        try {
            val ssid = buildSSIDFromDeviceId(deviceId)
            val operatingBand = if (use5GHz) {
                WifiP2pConfig.GROUP_OWNER_BAND_5GHZ
            } else {
                WifiP2pConfig.GROUP_OWNER_BAND_2GHZ
            }
            val config =
                    WifiP2pConfig.Builder()
                            .setNetworkName(ssid)
                            .setPassphrase(passphrase)
                            .setGroupOperatingBand(operatingBand)
                            .enablePersistentMode(false)
                            .build()

            val actionListener =
                    getActionListener(result, null, "Connection to device ID: $deviceId failed")

            wifiManager.connect(wifiChannel, config, actionListener)
        } catch (e: SecurityException) {
            if (!permissionsHandler.checkPermissions()) {
                Logger.e("No permission to call 'connectWithDeviceId'")
                permissionsHandler.requestPermissions()
            }
        } catch (e: Exception) {
            Logger.e("Error in connectWithDeviceId: ${e.message}")
            result.error("ERROR", "Failed to connect with device ID: ${e.message}", null)
        }
    }

    /** Disconnect from a previous device in Wi-fi Direct scope. */
    fun disconnect(result: Result? = null) {
        if (!checkInitialization(result)) return

        val actionListener = getActionListener(result, "Disconnected", "Failed to disconnect")

        wifiManager.cancelConnect(wifiChannel, null)
        wifiManager.removeGroup(wifiChannel, actionListener)
    }

    fun cancelConnect(result: Result? = null) {
        if (!checkInitialization(result)) return

        val actionListener =
                getActionListener(
                        result,
                        "Last connection request was cancelled",
                        "Failed to cancel the last connection process"
                )
        wifiManager.cancelConnect(wifiChannel, actionListener)
    }

    fun resetWifiDirect(result: Result? = null) {
        if (!checkInitialization(result)) return
        if (resetInProgress) {
            Logger.w("resetWifiDirect() ignored - already in progress")
            result?.success(true)
            return
        }

        resetInProgress = true
        Logger.w("Starting Wi-Fi Direct reset sequence...")

        // Step A: stop discovery (best effort)
        try {
            wifiManager.stopPeerDiscovery(wifiChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Logger.i("stopPeerDiscovery: success")
                    stepCancelConnectThenRemoveGroup(result)
                }
                override fun onFailure(reason: Int) {
                    Logger.w("stopPeerDiscovery: failure reason=$reason (continuing)")
                    stepCancelConnectThenRemoveGroup(result)
                }
            })
        } catch (t: Throwable) {
            Logger.w("stopPeerDiscovery threw: ${t.message} (continuing)")
            stepCancelConnectThenRemoveGroup(result)
        }
    }

    private fun stepCancelConnectThenRemoveGroup(result: Result?) {
        // Step B: cancelConnect (best effort)
        try {
            wifiManager.cancelConnect(wifiChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Logger.i("cancelConnect: success")
                    stepRemoveGroupWithRetry(result, attempt = 1)
                }
                override fun onFailure(reason: Int) {
                    Logger.w("cancelConnect: failure reason=$reason (continuing)")
                    stepRemoveGroupWithRetry(result, attempt = 1)
                }
            })
        } catch (t: Throwable) {
            Logger.w("cancelConnect threw: ${t.message} (continuing)")
            stepRemoveGroupWithRetry(result, attempt = 1)
        }
    }

    private fun stepRemoveGroupWithRetry(result: Result?, attempt: Int) {
        // Step C: removeGroup (this is the key); BUSY is common, so retry.
        val maxAttempts = 5
        val backoffMs = (attempt * 400L).coerceAtMost(2000L)

        try {
            wifiManager.removeGroup(wifiChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Logger.i("removeGroup: success")
                    stepReinitializeChannel(result)
                }

                override fun onFailure(reason: Int) {
                    Logger.w("removeGroup: failure reason=$reason attempt=$attempt")

                    if (attempt < maxAttempts) {
                        mainHandler.postDelayed(
                            { stepRemoveGroupWithRetry(result, attempt + 1) },
                            backoffMs
                        )
                    } else {
                        Logger.e("removeGroup failed after $maxAttempts attempts; still reinit channel")
                        stepReinitializeChannel(result)
                    }
                }
            })
        } catch (t: Throwable) {
            Logger.w("removeGroup threw: ${t.message}")
            stepReinitializeChannel(result)
        }
    }

    private fun stepReinitializeChannel(result: Result?) {
        try {
            // Unregister + re-register receiver (best effort)
            try { context.unregisterReceiver(receiver) } catch (_: Throwable) {}
            wifiChannel = wifiManager.initialize(context, Looper.getMainLooper(), null)
            initReceiver()
            Logger.i("Reinitialized channel + receiver")
        } catch (t: Throwable) {
            Logger.w("Reinit channel+receiver threw: ${t.message}")
        } finally {
            resetInProgress = false
            Logger.w("Wi-Fi Direct reset sequence completed")
            result?.success(true)
        }
    }

    private fun addWifiActions() {
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
    }

    private fun initWifiManager() {
        wifiManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        wifiChannel = wifiManager.initialize(context, Looper.getMainLooper(), null)
    }

    private fun initReceiver() {
        receiver =
                NearbyServiceBroadcastReceiver(
                        wifiManager,
                        wifiChannel,
                        permissionsHandler,
                )
        context.registerReceiver(receiver, intentFilter)
        try {
            receiver.init()
        } catch (error: Throwable) {
            Logger.e("Failed to write initial info, error=${error.message}")
        }
    }

    private fun checkInitialization(result: Result?, shouldLog: Boolean = true): Boolean {
        try {
            if (!::wifiManager.isInitialized) {
                Logger.e("WifiManager is not initialized. Please call 'initialize()' first")
                result?.success(ErrorCodes.NO_INITIALIZATION)
                return false
            }
            if (!::wifiChannel.isInitialized) {
                Logger.e("WifiChannel is not initialized. Please call 'initialize()' first")
                result?.success(ErrorCodes.NO_INITIALIZATION)
                return false
            }
            if (!::receiver.isInitialized) {
                Logger.e("Broadcast Receiver is not initialized. Please call 'initialize()' first")
                result?.success(ErrorCodes.NO_INITIALIZATION)
                return false
            }
        } catch (e: Exception) {
            Logger.e("Failed to check initialization, please call 'initialize()' first")
            result?.success(ErrorCodes.NO_INITIALIZATION)
            return false
        }
        return true
    }

    private fun getActionListener(
            result: Result?,
            successMessage: String? = null,
            errorMessage: String
    ): WifiP2pManager.ActionListener {
        return object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                if (successMessage != null) {
                    Logger.i(successMessage)
                }
                result?.success(true)
            }

            override fun onFailure(reasonCode: Int) {
                val reason =
                        when (reasonCode) {
                            WifiP2pManager.P2P_UNSUPPORTED ->
                                    "Wi-Fi P2P is not supported on this device. Please ensure your device supports Wi-Fi P2P."
                            WifiP2pManager.ERROR ->
                                    "A generic error occurred. This could be due to various reasons such as hardware issues, Wi-Fi being turned off, or temporary issues with the Wi-Fi P2P framework."
                            WifiP2pManager.BUSY ->
                                    "The Wi-Fi P2P framework is currently busy. Please wait for the current operation to complete before initiating another. Usually this means that you have sent a request to some device and now one of the peers is CONNECTING."
                            WifiP2pManager.NO_SERVICE_REQUESTS ->
                                    "No service discovery requests have been made. Ensure that you have initiated a service discovery request before attempting to connect."
                            else ->
                                    "An unknown error occurred. Please check the device's Wi-Fi P2P settings and ensure the device supports Wi-Fi P2P."
                        }
                val stringifyReasonCode =
                        when (reasonCode) {
                            WifiP2pManager.P2P_UNSUPPORTED -> ErrorCodes.P2P_UNSUPPORTED
                            WifiP2pManager.ERROR -> ErrorCodes.ERROR
                            WifiP2pManager.BUSY -> ErrorCodes.BUSY
                            WifiP2pManager.NO_SERVICE_REQUESTS -> ErrorCodes.NO_SERVICE_REQUESTS
                            else -> ErrorCodes.UNKNOWN
                        }
                Logger.e("$errorMessage, Reason code: $reasonCode, Reason: $reason")
                result?.success(stringifyReasonCode)
            }
        }
    }

    var p2pServiceHandler =
            object : EventChannel.StreamHandler {
                private var eventSink: EventChannel.EventSink? = null
                private var discoveryHandler: Handler? = null
                private var discoveryRunnable: Runnable? = null

                override fun onListen(arguments: Any?, sink: EventChannel.EventSink?) {
                    Logger.d("Start listening service discovery")
                    eventSink = sink

                    if (!checkInitialization(null, false)) return

                    // TXT record listener
                    val txtRecordListener =
                            WifiP2pManager.DnsSdTxtRecordListener {
                                    fullDomainName,
                                    txtRecordMap,
                                    srcDevice ->
                                Logger.d(
                                        "TXT record from ${srcDevice.deviceName} (${srcDevice.deviceAddress}): $txtRecordMap"
                                )

                                val deviceInfo =
                                        mapOf(
                                                "deviceName" to srcDevice.deviceName,
                                                "deviceAddress" to srcDevice.deviceAddress,
                                                "serviceName" to fullDomainName,
                                                "txtRecord" to txtRecordMap
                                        )

                                // Send discovered device info to Flutter
                                eventSink?.success(deviceInfo)
                            }

                    // Basic service response listener
                    val serviceResponseListener =
                            WifiP2pManager.DnsSdServiceResponseListener {
                                    instanceName,
                                    registrationType,
                                    srcDevice ->
                                Logger.d(
                                        "Service discovered: $instanceName from ${srcDevice.deviceName}"
                                )
                            }

                    // Attach listeners
                    wifiManager.setDnsSdResponseListeners(
                            wifiChannel,
                            serviceResponseListener,
                            txtRecordListener
                    )

                    // Start periodic discovery
                    val request = WifiP2pDnsSdServiceRequest.newInstance()
                    wifiManager.addServiceRequest(wifiChannel, request, null)

                    discoveryHandler = Handler(Looper.getMainLooper())
                    discoveryRunnable =
                            object : Runnable {
                                override fun run() {
                                    wifiManager.discoverServices(
                                            wifiChannel,
                                            object : WifiP2pManager.ActionListener {
                                                override fun onSuccess() {
                                                    Logger.d("Service discovery started")
                                                }

                                                override fun onFailure(reason: Int) {
                                                    Logger.e("Service discovery failed: $reason")
                                                }
                                            }
                                    )
                                    // Repeat every 25 seconds to keep discovery alive
                                    discoveryHandler?.postDelayed(this, 25000)
                                }
                            }
                    discoveryHandler?.post(discoveryRunnable!!)
                }

                override fun onCancel(arguments: Any?) {
                    Logger.d("Stop listening service discovery")
                    eventSink = null
                    discoveryHandler?.removeCallbacks(discoveryRunnable!!)
                    discoveryRunnable = null
                    discoveryHandler = null
                    wifiManager.clearServiceRequests(wifiChannel, null)
                }
            }

    var peersHandler =
            object : EventChannel.StreamHandler {
                private var handler: Handler = Handler(Looper.getMainLooper())
                private var eventSink: EventChannel.EventSink? = null

                val postCallback =
                        object : Runnable {
                            override fun run() {
                                if (!checkInitialization(null, false)) return

                                handler.post { eventSink?.success("${receiver.peers}") }
                                handler.postDelayed(this, 1000)
                            }
                        }

                override fun onListen(arguments: Any?, sink: EventChannel.EventSink?) {
                    onCancel(null)
                    Logger.d("Start listening peers")
                    eventSink = sink
                    handler.postDelayed(postCallback, 1000)
                }

                override fun onCancel(p0: Any?) {
                    Logger.d("Kill last process listening peers")
                    eventSink = null
                    handler.removeCallbacks(postCallback)
                }
            }

    var connectedDeviceInfoHandler =
            object : EventChannel.StreamHandler {
                private var handler: Handler = Handler(Looper.getMainLooper())
                private var eventSink: EventChannel.EventSink? = null

                val postCallback =
                        object : Runnable {
                            override fun run() {
                                if (!checkInitialization(null, false)) return

                                handler.post {
                                    eventSink?.success(receiver.connectedDevice?.toJsonString())
                                }
                                handler.postDelayed(this, 1000)
                            }
                        }

                override fun onListen(arguments: Any?, sink: EventChannel.EventSink?) {
                    onCancel(null)
                    eventSink = sink
                    Logger.d("Listen connected device")
                    handler.postDelayed(postCallback, 1000)
                }

                override fun onCancel(p0: Any?) {
                    Logger.d("Kill last process connected device")
                    eventSink = null
                    handler.removeCallbacks(postCallback)
                }
            }
    var connectionInfoHandler =
            object : EventChannel.StreamHandler {
                private var handler: Handler = Handler(Looper.getMainLooper())
                private var eventSink: EventChannel.EventSink? = null

                val postCallback =
                        object : Runnable {
                            override fun run() {
                                if (!checkInitialization(null, false)) return

                                handler.post {
                                    eventSink?.success(receiver.wifiInfo?.toJsonString())
                                }
                                handler.postDelayed(this, 1000)
                            }
                        }

                override fun onListen(arguments: Any?, sink: EventChannel.EventSink?) {
                    onCancel(null)
                    eventSink = sink
                    Logger.d("Listen connection info")
                    handler.postDelayed(postCallback, 1000)
                }

                override fun onCancel(p0: Any?) {
                    Logger.d("Kill last process connection info")
                    eventSink = null
                    handler.removeCallbacks(postCallback)
                }
            }

    var popupNotificationHandler =
            object : EventChannel.StreamHandler {
                private var eventSink: EventChannel.EventSink? = null

                override fun onListen(arguments: Any?, sink: EventChannel.EventSink?) {
                    Logger.d("Start listening popup notifications")
                    eventSink = sink
                    receiver.setPopupNotificationSink(eventSink)
                }

                override fun onCancel(arguments: Any?) {
                    Logger.d("Stop listening popup notifications")
                    eventSink = null
                    receiver.setPopupNotificationSink(null)
                }
            }
}

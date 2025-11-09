import 'package:nearby_service/nearby_service.dart';
import 'package:nearby_service/src/platforms/android/nearby_service_android_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

abstract class NearbyServiceAndroidPlatform extends PlatformInterface {
  NearbyServiceAndroidPlatform() : super(token: _token);

  static final Object _token = Object();

  static NearbyServiceAndroidPlatform _instance =
      MethodChannelAndroidNearbyService();

  /// The default instance of [NearbyServiceAndroidPlatform] to use.
  ///
  /// Defaults to [NearbyServiceAndroidPlatform].
  static NearbyServiceAndroidPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [NearbyServiceAndroidPlatform] when
  /// they register themselves.
  static set instance(NearbyServiceAndroidPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<bool> initialize() {
    throw UnimplementedError('initialize() has not been implemented.');
  }

  Future<bool> requestPermissions() {
    throw UnimplementedError('requestPermissions() has not been implemented.');
  }

  Future<bool> checkWifiService() {
    throw UnimplementedError('checkWifiService() has not been implemented.');
  }

  Future<NearbyConnectionAndroidInfo?> getConnectionInfo() {
    throw UnimplementedError('getConnectionInfo() has not been implemented.');
  }

  Future<bool> discover() {
    throw UnimplementedError('discover() has not been implemented.');
  }

  Future<bool> stopDiscovery() {
    throw UnimplementedError('stopDiscovery() has not been implemented.');
  }

  Future<bool> connect(String deviceAddress, bool isGroupOwner) {
    throw UnimplementedError('connect() has not been implemented.');
  }

  /// Connects to a device using SSID and passphrase.
  ///
  /// [ssid] Network name (SSID) to connect to.
  /// [passphrase] Passphrase for the network. Defaults to "KhJ10287SbGa" if not provided.
  ///
  /// Returns true if connection request was sent successfully, false otherwise.
  Future<bool> connectWithSSID(String ssid,
      {String passphrase = "KhJ10287SbGa"}) {
    throw UnimplementedError('connectWithSSID() has not been implemented.');
  }

  /// Creates a WiFi Direct group with optional operating frequency.
  ///
  /// [frequencyMhz] Operating frequency in MHz (e.g., 5200, 5220, 5240).
  /// Requires Android 10+ (API 29+) to set specific frequency.
  /// If null or unsupported, system chooses frequency automatically.
  ///
  /// Returns true if group creation started successfully, false otherwise.
  Future<bool> createGroup({int? frequencyMhz}) {
    throw UnimplementedError('createGroup() has not been implemented.');
  }

  /// Creates a WiFi Direct group from device ID with specified passphrase.
  ///
  /// [deviceId] Device ID to include in the network name.
  /// [passphrase] Passphrase for the network. Defaults to "KhJ10287SbGa" if not provided.
  ///
  /// The network name will be in the format: "Direct-mira-<deviceId>DDD<6 random characters>"
  ///
  /// Returns true if group creation started successfully, false otherwise.
  Future<bool> createGroupFromDeviceId(String deviceId,
      {String passphrase = "KhJ10287SbGa"}) {
    throw UnimplementedError(
        'createGroupFromDeviceId() has not been implemented.');
  }

  /// Builds and returns the SSID (network name) from device ID.
  ///
  /// [deviceId] Device ID to include in the network name.
  ///
  /// Returns the SSID string in the format: "DIRECT-mira-<deviceId>DdDxaK21BA"
  Future<String> buildSSIDFromDeviceId(String deviceId) {
    throw UnimplementedError(
        'buildSSIDFromDeviceId() has not been implemented.');
  }

  Future<bool> removeGroup() {
    throw UnimplementedError('removeGroup() has not been implemented.');
  }

  Future<bool> disconnect() {
    throw UnimplementedError('disconnect() has not been implemented.');
  }

  Future<bool> cancelConnect() {
    throw UnimplementedError('cancelConnect() has not been implemented.');
  }

  Stream<NearbyConnectionAndroidInfo?> getConnectionInfoStream() {
    throw UnimplementedError(
      'getConnectionInfoStream() has not been implemented.',
    );
  }

  Stream<String> getConnectionState() {
    throw UnimplementedError('getConnectionState() has not been implemented.');
  }

  Future<dynamic> addLocalService(
      String serviceName, String serviceType, Map<String, String> txtRecord) {
    throw UnimplementedError('addLocalService() has not been implemented.');
  }

  Future<dynamic> removeLocalServices() {
    throw UnimplementedError('removeLocalServices() has not been implemented.');
  }

  Future<dynamic> renameDevice(String newName) {
    throw UnimplementedError('renameDevice() has not been implemented.');
  }

  /// Starts fast peer discovery on a specific frequency channel.
  /// This is significantly faster than full-band discovery as it only scans one channel.
  ///
  /// Requires API level 33+ and channel-constrained discovery support.
  /// Use [isChannelConstrainedDiscoverySupported] to check support before calling.
  ///
  /// Recommended frequencies for 5GHz:
  /// - 5200 MHz (Channel 40)
  /// - 5220 MHz (Channel 44)
  /// - 5240 MHz (Channel 48)
  ///
  /// [frequencyMhz] The frequency in MHz to scan (e.g., 5200 for Channel 40).
  ///
  /// Returns true if discovery started successfully, false otherwise.
  Future<bool> discoverPeersOnFrequency(int frequencyMhz) {
    throw UnimplementedError(
      'discoverPeersOnFrequency() has not been implemented.',
    );
  }

  /// Checks if channel-constrained discovery is supported on this device.
  /// This feature is required for [discoverPeersOnFrequency] to work.
  ///
  /// Requires API level 33+.
  ///
  /// Returns true if supported, false otherwise.
  Future<bool> isChannelConstrainedDiscoverySupported() {
    throw UnimplementedError(
      'isChannelConstrainedDiscoverySupported() has not been implemented.',
    );
  }
}

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

  /// Adds a service request to discover specific services.
  /// This allows you to filter discovered devices to only those advertising your service.
  ///
  /// [serviceType] Type of the service to discover (e.g., "_presence._tcp").
  ///               If null or empty, discovers all DNS-SD services.
  Future<dynamic> addServiceRequest(String? serviceType) {
    throw UnimplementedError('addServiceRequest() has not been implemented.');
  }

  /// Removes all service requests added by [addServiceRequest].
  Future<dynamic> removeServiceRequests() {
    throw UnimplementedError(
        'removeServiceRequests() has not been implemented.');
  }

  /// Start discovery for services in Wi-fi Direct scope.
  /// This discovers only devices that match the service requests added via [addServiceRequest].
  ///
  /// Note: You must call [addServiceRequest] before calling this method.
  Future<bool> discoverServices() {
    throw UnimplementedError('discoverServices() has not been implemented.');
  }

  /// Stop service discovery.
  Future<bool> stopServiceDiscovery() {
    throw UnimplementedError(
        'stopServiceDiscovery() has not been implemented.');
  }

  /// Sets up DNS-SD response listeners to receive discovered services.
  Future<bool> setServiceResponseListeners() {
    throw UnimplementedError(
        'setServiceResponseListeners() has not been implemented.');
  }
}

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

  Future<bool> createGroup() {
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
}

/// Quick Start Example: WiFi Direct Service Discovery
///
/// This file demonstrates the simplest way to implement service discovery
/// in your app to filter discovered devices to only those running your app.

import 'package:nearby_service/nearby_service.dart';

/// Use this constant across your app to ensure consistency
const String MY_APP_SERVICE_TYPE = "_myapp._tcp";

/// DEVICE A: Advertiser (wants to be discovered)
/// Call this on the device that should be visible to others
Future<void> setupAsAdvertiser() async {
  // Step 1: Initialize
  await NearbyServiceAndroidPlatform.instance.initialize();
  await NearbyServiceAndroidPlatform.instance.requestPermissions();

  // Step 2: Advertise your service with metadata
  await NearbyServiceAndroidPlatform.instance.addLocalService(
    "MyApp", // Service name (displayed to users)
    MY_APP_SERVICE_TYPE, // Service type (must match across devices)
    {
      "deviceType": "tablet", // Custom metadata
      "version": "1.0.0",
      "userName": "Alice",
    },
  );

  print("✅ Now advertising service - other devices can discover me!");
}

/// DEVICE B: Discoverer (wants to find other devices)
/// Call this on the device that should discover others
Future<void> setupAsDiscoverer() async {
  // Step 1: Initialize
  await NearbyServiceAndroidPlatform.instance.initialize();
  await NearbyServiceAndroidPlatform.instance.requestPermissions();

  // Step 2: Add service request (filter for your app's service type)
  await NearbyServiceAndroidPlatform.instance.addServiceRequest(
    MY_APP_SERVICE_TYPE, // Only discover devices with this service type
  );

  // Step 3: Listen for discovered services
  NearbyServicePlatform.instance.getServiceDiscoveryStream().listen((services) {
    print("📱 Discovered ${services.length} device(s):");

    for (var service in services) {
      final deviceName = service['deviceName'];
      final deviceAddress = service['deviceAddress'];
      final txtRecord = service['txtRecord'] as Map?;

      print("  - Device: $deviceName");
      print("    Address: $deviceAddress");

      if (txtRecord != null) {
        print("    Type: ${txtRecord['deviceType']}");
        print("    Version: ${txtRecord['version']}");
        print("    User: ${txtRecord['userName']}");
      }

      // You can now connect to this device
      // connectToDevice(deviceAddress);
    }
  });

  // Step 4: Start discovery
  final success = await NearbyServiceAndroidPlatform.instance
      .discoverServices();

  if (success) {
    print("✅ Service discovery started - searching for devices...");
  } else {
    print("❌ Failed to start service discovery");
  }
}

/// Connect to a discovered device
Future<void> connectToDevice(String deviceAddress) async {
  print("🔗 Connecting to device: $deviceAddress");

  final success = await NearbyServiceAndroidPlatform.instance.connect(
    deviceAddress,
    false, // isGroupOwner: set to true if this device should be group owner
  );

  if (success) {
    print("✅ Connected successfully!");
  } else {
    print("❌ Connection failed");
  }
}

/// Cleanup when done
Future<void> cleanup() async {
  await NearbyServiceAndroidPlatform.instance.stopServiceDiscovery();
  await NearbyServiceAndroidPlatform.instance.removeServiceRequests();
  await NearbyServiceAndroidPlatform.instance.removeLocalServices();

  print("🧹 Cleanup complete");
}

/// BOTH DEVICES: Setup as both advertiser AND discoverer
/// This is the most common use case - each device advertises itself
/// and also discovers other devices
Future<void> setupAsAdvertiserAndDiscoverer() async {
  // Initialize
  await NearbyServiceAndroidPlatform.instance.initialize();
  await NearbyServiceAndroidPlatform.instance.requestPermissions();

  // Advertise this device
  await NearbyServiceAndroidPlatform.instance.addLocalService(
    "MyApp",
    MY_APP_SERVICE_TYPE,
    {"deviceType": "phone", "version": "1.0.0"},
  );

  // Also discover other devices
  await NearbyServiceAndroidPlatform.instance.addServiceRequest(
    MY_APP_SERVICE_TYPE,
  );

  // Listen for discoveries
  NearbyServicePlatform.instance.getServiceDiscoveryStream().listen((services) {
    print("Found ${services.length} other device(s) running my app!");

    for (var service in services) {
      print("  - ${service['deviceName']} (${service['deviceAddress']})");
    }
  });

  // Start discovery
  await NearbyServiceAndroidPlatform.instance.discoverServices();

  print("✅ Setup complete - advertising and discovering!");
}

/// Example: UI Integration
/// 
/// In your Flutter widget:
/// 
/// ```dart
/// class MyServiceDiscoveryPage extends StatefulWidget {
///   @override
///   State<MyServiceDiscoveryPage> createState() => _MyServiceDiscoveryPageState();
/// }
/// 
/// class _MyServiceDiscoveryPageState extends State<MyServiceDiscoveryPage> {
///   List<Map<dynamic, dynamic>> discoveredDevices = [];
///   
///   @override
///   void initState() {
///     super.initState();
///     _initializeDiscovery();
///   }
///   
///   Future<void> _initializeDiscovery() async {
///     // Initialize and request permissions
///     await NearbyServiceAndroidPlatform.instance.initialize();
///     await NearbyServiceAndroidPlatform.instance.requestPermissions();
///     
///     // Advertise this device
///     await NearbyServiceAndroidPlatform.instance.addLocalService(
///       "MyApp",
///       MY_APP_SERVICE_TYPE,
///       {"deviceType": "phone"},
///     );
///     
///     // Request to discover other devices
///     await NearbyServiceAndroidPlatform.instance.addServiceRequest(
///       MY_APP_SERVICE_TYPE,
///     );
///     
///     // Listen for discoveries and update UI
///     NearbyServicePlatform.instance.getServiceDiscoveryStream().listen((services) {
///       setState(() {
///         discoveredDevices = services;
///       });
///     });
///     
///     // Start discovery
///     await NearbyServiceAndroidPlatform.instance.discoverServices();
///   }
///   
///   @override
///   Widget build(BuildContext context) {
///     return Scaffold(
///       appBar: AppBar(title: Text('Nearby Devices')),
///       body: ListView.builder(
///         itemCount: discoveredDevices.length,
///         itemBuilder: (context, index) {
///           final device = discoveredDevices[index];
///           return ListTile(
///             title: Text(device['deviceName'] ?? 'Unknown'),
///             subtitle: Text(device['deviceAddress'] ?? ''),
///             trailing: ElevatedButton(
///               child: Text('Connect'),
///               onPressed: () {
///                 connectToDevice(device['deviceAddress']);
///               },
///             ),
///           );
///         },
///       ),
///     );
///   }
///   
///   @override
///   void dispose() {
///     cleanup();
///     super.dispose();
///   }
/// }
/// ```

/// WHY USE SERVICE DISCOVERY?
/// 
/// Traditional Peer Discovery (discover()):
///   ❌ Discovers ALL WiFi Direct devices (your app, other apps, TVs, fridges, etc.)
///   ❌ No way to filter results
///   ❌ Users see irrelevant devices
/// 
/// Service Discovery (discoverServices()):
///   ✅ ONLY discovers devices running YOUR app
///   ✅ Filter by service type
///   ✅ Include metadata (device type, version, etc.)
///   ✅ Better user experience
/// 
/// Example:
///   Without service discovery: User sees 15 devices (5 phones, 3 TVs, 2 fridges, 5 other apps)
///   With service discovery: User sees 5 devices (only devices running YOUR app)


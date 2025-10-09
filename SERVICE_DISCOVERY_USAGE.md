# WiFi Direct Service Discovery Usage Guide

This guide explains how to use the Pre-Association Service Discovery (P2P SD) APIs to ensure that only devices running your app appear in discovery results.

## Overview

The service discovery implementation uses three key methods:

1. **`addLocalService()`** - Advertise a unique service from your app
2. **`addServiceRequest()`** - Request to discover that same service
3. **`discoverServices()`** - Find only peers advertising that service

This approach provides **logical segregation** - only devices running your app with the same service type will appear in results, filtering out TVs, phones, fridges, and other WiFi Direct devices.

## Implementation Flow

### 1. Setup and Initialization

First, initialize the service and request permissions:

```dart
import 'package:nearby_service/nearby_service.dart';

// Initialize
await NearbyServiceAndroidPlatform.instance.initialize();

// Request permissions
await NearbyServiceAndroidPlatform.instance.requestPermissions();
```

### 2. Advertise Your Service (Device A)

Device A advertises a local service with a unique service type and optional metadata:

```dart
final serviceName = "MyApp";
final serviceType = "_myapp._tcp";  // Unique service type for your app
final txtRecord = {
  "deviceType": "tablet",
  "version": "1.0.0",
  "userId": "user123",
};

await NearbyServiceAndroidPlatform.instance.addLocalService(
  serviceName,
  serviceType,
  txtRecord,
);
```

**Important Notes:**
- The `serviceType` should be unique to your app (e.g., `_yourappname._tcp`)
- Use the `txtRecord` to include metadata like device type, user info, etc.
- Only devices with the same service type will be discoverable

### 3. Request Service Discovery (Device B)

Device B adds a service request to discover only devices advertising your service:

```dart
final serviceType = "_myapp._tcp";  // Same service type as advertised

await NearbyServiceAndroidPlatform.instance.addServiceRequest(serviceType);
```

**To discover all DNS-SD services** (not recommended):
```dart
await NearbyServiceAndroidPlatform.instance.addServiceRequest(null);
```

### 4. Set Up Service Response Listeners (Optional)

If you want manual control instead of using the EventChannel stream:

```dart
await NearbyServiceAndroidPlatform.instance.setServiceResponseListeners();
```

This sets up listeners to log discovered services. For production use, you'll likely use the EventChannel stream (see below).

### 5. Start Service Discovery

```dart
final result = await NearbyServiceAndroidPlatform.instance.discoverServices();

if (result) {
  print("Service discovery started successfully");
} else {
  print("Failed to start service discovery");
}
```

**Common Error:** If you get a `NO_SERVICE_REQUESTS` error, make sure you called `addServiceRequest()` first.

### 6. Listen for Discovered Services

Use the EventChannel stream to receive discovered services in real-time:

```dart
NearbyServicePlatform.instance.getServiceDiscoveryStream().listen((services) {
  for (var service in services) {
    print("Discovered service:");
    print("  Device Name: ${service['deviceName']}");
    print("  Device Address: ${service['deviceAddress']}");
    print("  Service Name: ${service['serviceName']}");
    print("  TXT Record: ${service['txtRecord']}");
    
    // Access custom metadata from txtRecord
    if (service['txtRecord'] != null) {
      final txtRecord = service['txtRecord'] as Map;
      print("  Device Type: ${txtRecord['deviceType']}");
      print("  Version: ${txtRecord['version']}");
    }
  }
});
```

### 7. Connect to Discovered Device

Once you've discovered a device, connect to it using its device address:

```dart
final deviceAddress = discoveredService['deviceAddress'];
final isGroupOwner = true;  // Set based on your needs

await NearbyServiceAndroidPlatform.instance.connect(
  deviceAddress,
  isGroupOwner,
);
```

### 8. Cleanup

When you're done with service discovery:

```dart
// Stop service discovery
await NearbyServiceAndroidPlatform.instance.stopServiceDiscovery();

// Remove service requests
await NearbyServiceAndroidPlatform.instance.removeServiceRequests();

// Remove local services
await NearbyServiceAndroidPlatform.instance.removeLocalServices();
```

## Complete Example

Here's a complete example showing both advertiser and discoverer:

```dart
import 'package:nearby_service/nearby_service.dart';

class ServiceDiscoveryExample {
  static const String APP_SERVICE_TYPE = "_myapp._tcp";
  
  // Call this on the device that wants to be discovered
  Future<void> advertiseService() async {
    await NearbyServiceAndroidPlatform.instance.initialize();
    await NearbyServiceAndroidPlatform.instance.requestPermissions();
    
    await NearbyServiceAndroidPlatform.instance.addLocalService(
      "MyApp",
      APP_SERVICE_TYPE,
      {
        "deviceType": "phone",
        "version": "1.0.0",
      },
    );
    
    print("Now advertising service: $APP_SERVICE_TYPE");
  }
  
  // Call this on the device that wants to discover others
  Future<void> discoverServices() async {
    await NearbyServiceAndroidPlatform.instance.initialize();
    await NearbyServiceAndroidPlatform.instance.requestPermissions();
    
    // Add service request for your app's service type
    await NearbyServiceAndroidPlatform.instance.addServiceRequest(APP_SERVICE_TYPE);
    
    // Listen for discovered services
    NearbyServicePlatform.instance.getServiceDiscoveryStream().listen((services) {
      print("Discovered ${services.length} services");
      
      for (var service in services) {
        print("Found device: ${service['deviceName']}");
        print("Address: ${service['deviceAddress']}");
        print("TXT Record: ${service['txtRecord']}");
      }
    });
    
    // Start discovery
    final success = await NearbyServiceAndroidPlatform.instance.discoverServices();
    
    if (success) {
      print("Service discovery started");
    } else {
      print("Failed to start service discovery");
    }
  }
  
  Future<void> cleanup() async {
    await NearbyServiceAndroidPlatform.instance.stopServiceDiscovery();
    await NearbyServiceAndroidPlatform.instance.removeServiceRequests();
    await NearbyServiceAndroidPlatform.instance.removeLocalServices();
  }
}
```

## Comparison: Service Discovery vs Peer Discovery

### Traditional Peer Discovery
```dart
// Discovers ALL WiFi Direct devices (phones, TVs, fridges, etc.)
await NearbyServiceAndroidPlatform.instance.discover();
```

**Problems:**
- Discovers all WiFi Direct devices nearby
- No way to filter by app
- Users see irrelevant devices

### Service Discovery (Recommended)
```dart
// Discovers ONLY devices running your app with matching service type
await NearbyServiceAndroidPlatform.instance.addServiceRequest("_myapp._tcp");
await NearbyServiceAndroidPlatform.instance.discoverServices();
```

**Benefits:**
- Only discovers devices running your app
- Can include metadata in TXT records
- Logical segregation, not just frequency-based scanning
- Better user experience

## Key Benefits

1. **App-Specific Discovery**: Only devices running your app appear in results
2. **Metadata Support**: Include device type, version, user info in TXT records
3. **No False Positives**: TVs, fridges, and other devices won't appear
4. **Better UX**: Users only see relevant connection options

## Error Handling

```dart
try {
  final result = await NearbyServiceAndroidPlatform.instance.discoverServices();
  
  if (result == false) {
    // Check if service request was added
    print("Make sure you called addServiceRequest() first");
  }
} catch (e) {
  print("Error during service discovery: $e");
}
```

## Common Issues

### NO_SERVICE_REQUESTS Error
**Cause**: Called `discoverServices()` without calling `addServiceRequest()` first.

**Solution**:
```dart
await NearbyServiceAndroidPlatform.instance.addServiceRequest("_myapp._tcp");
await NearbyServiceAndroidPlatform.instance.discoverServices();
```

### No Devices Discovered
**Cause**: Service types don't match between advertiser and discoverer.

**Solution**: Ensure both devices use the exact same service type string (e.g., `_myapp._tcp`).

### Permission Errors
**Cause**: Missing required permissions.

**Solution**:
```dart
await NearbyServiceAndroidPlatform.instance.requestPermissions();
```

## Best Practices

1. **Use a unique service type** for your app (e.g., `_yourappname._tcp`)
2. **Include useful metadata** in TXT records (device type, version, etc.)
3. **Always call cleanup methods** when done with discovery
4. **Handle errors gracefully** and provide user feedback
5. **Request permissions** before starting any discovery operations
6. **Use the EventChannel stream** for real-time updates instead of polling

## Android Manifest Requirements

Ensure your `AndroidManifest.xml` includes:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.INTERNET" />
```

## API Reference

### Methods

#### `addLocalService(String serviceName, String serviceType, Map<String, String> txtRecord)`
Advertises a local service for WiFi Direct service discovery.

#### `addServiceRequest(String? serviceType)`
Adds a service request to discover specific services. Pass `null` to discover all services.

#### `discoverServices()`
Starts discovery for services matching the added service requests.

#### `stopServiceDiscovery()`
Stops service discovery.

#### `removeServiceRequests()`
Removes all service requests.

#### `removeLocalServices()`
Removes all advertised local services.

#### `setServiceResponseListeners()`
Sets up DNS-SD response listeners (for manual control instead of using EventChannel).

### Streams

#### `getServiceDiscoveryStream()`
Stream of discovered WiFi Direct services. Each event contains a list of discovered services with their metadata.

## Additional Resources

- [Android WiFi Direct Documentation](https://developer.android.com/guide/topics/connectivity/wifip2p)
- [WiFi Direct Service Discovery](https://developer.android.com/training/connect-devices-wirelessly/nsd-wifi-direct)


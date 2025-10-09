# Fast Discovery Quick Reference

## 🚀 Quick Start (Copy-Paste Ready)

### Minimal Working Example

```dart
import 'package:nearby_service/nearby_service.dart';

Future<void> quickStart() async {
  // 1. Initialize
  await NearbyServiceAndroidPlatform.instance.initialize();
  await NearbyServiceAndroidPlatform.instance.requestPermissions();
  
  // 2. Advertise this device
  await NearbyServiceAndroidPlatform.instance.addLocalService(
    "MyApp",
    "_myapp._tcp",
    {"deviceType": "tablet"},
  );
  
  // 3. Add service filter
  await NearbyServiceAndroidPlatform.instance.addServiceRequest("_myapp._tcp");
  
  // 4. Start FAST discovery
  final isSupported = await NearbyServiceAndroidPlatform.instance
      .isChannelConstrainedDiscoverySupported();
  
  if (isSupported) {
    await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(5200);
  } else {
    await NearbyServiceAndroidPlatform.instance.discover();
  }
  
  // 5. Listen for devices
  NearbyServicePlatform.instance.getServiceDiscoveryStream().listen((devices) {
    print("Found ${devices.length} devices");
    for (var device in devices) {
      print("  - ${device['deviceName']}");
    }
  });
}
```

## 📋 Method Reference

### New Methods (Fast Discovery)

```dart
// Check if fast discovery is supported (API 33+)
Future<bool> isChannelConstrainedDiscoverySupported()

// Start fast discovery on specific frequency
Future<bool> discoverPeersOnFrequency(int frequencyMhz)
```

### Existing Methods (Service Discovery)

```dart
// Advertise your service
Future<void> addLocalService(
  String serviceName,
  String serviceType,
  Map<String, String> txtRecord,
)

// Filter discovery by service type
Future<void> addServiceRequest(String? serviceType)

// Start discovery (slower, full scan)
Future<bool> discoverServices()

// Regular peer discovery (no filtering)
Future<bool> discover()

// Stop any discovery
Future<bool> stopDiscovery()

// Cleanup
Future<void> removeServiceRequests()
Future<void> removeLocalServices()
```

## 🎯 Common Frequencies

| Frequency | Channel | Band | Notes |
|-----------|---------|------|-------|
| 5200 MHz | 40 | 5 GHz | ✅ Recommended - Less crowded |
| 5220 MHz | 44 | 5 GHz | ✅ Good alternative |
| 5240 MHz | 48 | 5 GHz | ✅ Good alternative |
| 5180 MHz | 36 | 5 GHz | Common, may be congested |
| 2412 MHz | 1 | 2.4 GHz | More congested |
| 2437 MHz | 6 | 2.4 GHz | More congested |
| 2462 MHz | 11 | 2.4 GHz | More congested |

## ⚡ Performance Comparison

| Method | Time | Filtering |
|--------|------|-----------|
| `discoverPeersOnFrequency(5200)` | 1-3s | ❌ (needs service request) |
| `discover()` | 3-5s | ❌ |
| `discoverServices()` | 15-30s | ✅ |
| **Fast + Service Filter** | 1-3s | ✅ |

## 🔧 Before vs After

### ❌ Old Slow Way (15-30 seconds)

```dart
await NearbyServiceAndroidPlatform.instance.addServiceRequest("_myapp._tcp");
await NearbyServiceAndroidPlatform.instance.discoverServices();
```

### ✅ New Fast Way (1-3 seconds)

```dart
await NearbyServiceAndroidPlatform.instance.addServiceRequest("_myapp._tcp");

final isSupported = await NearbyServiceAndroidPlatform.instance
    .isChannelConstrainedDiscoverySupported();

if (isSupported) {
  await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(5200);
} else {
  await NearbyServiceAndroidPlatform.instance.discover();
}
```

## 🎬 Complete Flow

```dart
// Device A (Advertiser)
┌─────────────────────────────────────────┐
│ 1. initialize()                         │
│ 2. requestPermissions()                 │
│ 3. addLocalService("MyApp", "_myapp._tcp", {...}) │
│ 4. [Wait to be discovered]              │
└─────────────────────────────────────────┘

// Device B (Discoverer)
┌─────────────────────────────────────────┐
│ 1. initialize()                         │
│ 2. requestPermissions()                 │
│ 3. addLocalService("MyApp", "_myapp._tcp", {...}) │  // Also advertise
│ 4. addServiceRequest("_myapp._tcp")     │
│ 5. isChannelConstrainedDiscoverySupported() │
│ 6. discoverPeersOnFrequency(5200)       │  // Fast!
│ 7. Listen to getServiceDiscoveryStream() │
│ 8. connect(deviceAddress, false)        │
└─────────────────────────────────────────┘
```

## 🐛 Troubleshooting

### No Devices Found

```dart
// Try different frequencies
for (int freq in [5200, 5220, 5240, 5180]) {
  await NearbyServiceAndroidPlatform.instance.stopDiscovery();
  await Future.delayed(Duration(milliseconds: 500));
  await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(freq);
  await Future.delayed(Duration(seconds: 3));
}
```

### Check If Everything Is Set Up

```dart
// Debug checklist
print("1. Initialized: ${await checkInitialized()}");
print("2. Permissions: ${await requestPermissions()}");
print("3. WiFi enabled: ${await checkWifiService()}");
print("4. Fast discovery supported: ${await isChannelConstrainedDiscoverySupported()}");
```

### Still Slow?

```dart
// Verify you're using the right method
final stopwatch = Stopwatch()..start();

await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(5200);

await Future.delayed(Duration(seconds: 5));
stopwatch.stop();

print("Discovery took: ${stopwatch.elapsedMilliseconds}ms");
// Expected: < 3000ms (3 seconds)
// If > 5000ms, check you're not calling discoverServices()
```

## 📦 Required Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.INTERNET" />
```

## 💡 Pro Tips

1. **Always advertise**: Even discoverers should advertise so they can be discovered by others
2. **Use unique service types**: `_yourappname._tcp` not `_generic._tcp`
3. **Check support first**: Not all devices support fast discovery
4. **Provide fallback**: Use regular `discover()` if fast discovery not supported
5. **Periodic scanning**: Re-run discovery every 10-15s to find new devices
6. **Handle errors**: Wrap in try-catch and provide user feedback

## 📚 Full Documentation

- **Implementation Guide**: `IMPLEMENTATION_GUIDE.md` - Complete Flutter integration
- **Fast Discovery Guide**: `FAST_DISCOVERY_GUIDE.md` - Deep dive into speed optimization
- **Service Discovery Usage**: `SERVICE_DISCOVERY_USAGE.md` - Original service discovery docs

## 🎯 Key Takeaway

**Fast discovery (`discoverPeersOnFrequency`) speeds up scanning from 15-30s to 1-3s, but you MUST still use service requests (`addServiceRequest`) to filter by app. The combination gives you both speed AND filtering.**

```dart
// The winning combination:
await addServiceRequest("_myapp._tcp");         // ← Filtering
await discoverPeersOnFrequency(5200);           // ← Speed
// Result: Fast (1-3s) + App-specific filtering ✅
```


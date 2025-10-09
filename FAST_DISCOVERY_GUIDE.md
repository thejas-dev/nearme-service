# Fast WiFi Direct Discovery Guide

## Understanding Discovery Speed

WiFi Direct discovery can be slow because it scans multiple channels. Here's how different approaches compare:

| Method | Speed | Filtering | Use Case |
|--------|-------|-----------|----------|
| `discover()` | ⚡ Fast (3-5s) | ❌ None - finds ALL devices | Quick scan, no filtering needed |
| `discoverServices()` | 🐌 Slow (15-30s) | ✅ App-specific | Filtered discovery |
| `discoverPeersOnFrequency()` | ⚡⚡ Very Fast (1-3s) | ❌ None - finds ALL on that channel | Optimized scanning |

## The Speed Problem

When using service discovery, the process is slow because:

1. **Scans all WiFi channels** (2.4GHz: 1-13, 5GHz: 36-165 = 20+ channels)
2. **Does DNS-SD query/response** on each channel
3. **Waits for responses** from all channels
4. **Total time**: 15-30 seconds

## ✅ Optimized Hybrid Strategy

The best approach combines **fast frequency-specific discovery** with **service verification**:

### Strategy Overview

```
Step 1: Quick Scan (1-3 seconds)
└─> discoverPeersOnFrequency(5200 MHz) → Find peers on known channel

Step 2: Filter Results  
└─> Check discovered peers for your service type

Step 3: Connect
└─> Only connect to peers with matching service
```

## Implementation

### 1. Check Device Support

First, verify that the device supports channel-constrained discovery:

```dart
// Check if frequency-specific discovery is supported
final isSupported = await NearbyServiceAndroidPlatform.instance
    .isChannelConstrainedDiscoverySupported();

if (!isSupported) {
  print("Device doesn't support fast discovery, falling back to normal mode");
  // Use regular discover() or discoverServices() instead
}
```

### 2. Advertise Your Service (All Devices)

Every device should advertise a unique service type:

```dart
const APP_SERVICE_TYPE = "_mytabletapp._tcp";

await NearbyServiceAndroidPlatform.instance.addLocalService(
  "MyTabletApp",
  APP_SERVICE_TYPE,
  {
    "deviceType": "tablet",
    "version": "1.0.0",
    "deviceId": "unique_id_123",
  },
);
```

### 3. Fast Discovery with Service Filtering

Use frequency-specific discovery for speed, then filter by service:

```dart
import 'package:nearby_service/nearby_service.dart';

class FastDiscoveryManager {
  static const int PREFERRED_FREQUENCY = 5200; // 5200 MHz = Channel 40
  static const String APP_SERVICE_TYPE = "_mytabletapp._tcp";
  
  // List to track discovered devices with our service
  final List<Map<String, dynamic>> appDevices = [];
  
  Future<void> startFastDiscovery() async {
    // Step 1: Add service request for filtering
    await NearbyServiceAndroidPlatform.instance.addServiceRequest(
      APP_SERVICE_TYPE,
    );
    
    // Step 2: Start fast frequency-specific peer discovery
    final started = await NearbyServiceAndroidPlatform.instance
        .discoverPeersOnFrequency(PREFERRED_FREQUENCY);
    
    if (started) {
      print("Fast discovery started on $PREFERRED_FREQUENCY MHz");
    } else {
      print("Fast discovery failed, falling back to normal discovery");
      await NearbyServiceAndroidPlatform.instance.discover();
    }
    
    // Step 3: Listen for discovered services
    NearbyServicePlatform.instance.getServiceDiscoveryStream().listen((services) {
      for (var service in services) {
        // Only process devices with our service type
        if (service['serviceType'] == APP_SERVICE_TYPE) {
          print("Found compatible device: ${service['deviceName']}");
          appDevices.add(service);
        }
      }
    });
  }
  
  Future<void> stopDiscovery() async {
    await NearbyServiceAndroidPlatform.instance.stopDiscovery();
    await NearbyServiceAndroidPlatform.instance.removeServiceRequests();
  }
}
```

### 4. Periodic Discovery for New Devices

Continuously scan for new tablets joining the network:

```dart
import 'dart:async';

class ContinuousDiscoveryManager {
  Timer? _discoveryTimer;
  static const SCAN_INTERVAL = Duration(seconds: 10);
  
  void startPeriodicDiscovery() {
    // Initial discovery
    _runDiscovery();
    
    // Periodic discovery every 10 seconds
    _discoveryTimer = Timer.periodic(SCAN_INTERVAL, (_) {
      _runDiscovery();
    });
  }
  
  Future<void> _runDiscovery() async {
    // Stop previous discovery
    await NearbyServiceAndroidPlatform.instance.stopDiscovery();
    
    // Start new fast discovery
    await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(5200);
    
    print("Scanning for new devices...");
  }
  
  void stopPeriodicDiscovery() {
    _discoveryTimer?.cancel();
    _discoveryTimer = null;
  }
}
```

## Frequency Selection

### Recommended 5GHz Frequencies

| Frequency | Channel | Congestion | Notes |
|-----------|---------|------------|-------|
| 5200 MHz | 40 | Low | ✅ Recommended - Less crowded |
| 5220 MHz | 44 | Low | ✅ Good alternative |
| 5240 MHz | 48 | Low | ✅ Good alternative |
| 5180 MHz | 36 | Medium | Common, may be congested |
| 5745 MHz | 149 | Low | Upper band, good for DFS-capable devices |

### Why 5GHz is Better

- **Faster discovery**: Less interference than 2.4GHz
- **Better performance**: Higher throughput for data transfer
- **Less congestion**: Fewer devices compete for channels

## Complete Example: Tablet Sync Network

Here's a complete implementation for your tablet network:

```dart
import 'package:nearby_service/nearby_service.dart';
import 'dart:async';

class TabletNetworkManager {
  static const String SERVICE_NAME = "TabletSync";
  static const String SERVICE_TYPE = "_tabletsync._tcp";
  static const int PREFERRED_FREQUENCY = 5200; // MHz
  static const Duration SCAN_INTERVAL = Duration(seconds: 12);
  
  Timer? _periodicDiscoveryTimer;
  final List<Map<String, dynamic>> _discoveredTablets = [];
  
  /// Initialize and start advertising this tablet
  Future<void> initialize() async {
    await NearbyServiceAndroidPlatform.instance.initialize();
    await NearbyServiceAndroidPlatform.instance.requestPermissions();
    
    // Advertise this tablet
    await _advertiseTablet();
    
    print("✅ Tablet network initialized");
  }
  
  /// Advertise this tablet's service
  Future<void> _advertiseTablet() async {
    final deviceInfo = await _getDeviceInfo();
    
    await NearbyServiceAndroidPlatform.instance.addLocalService(
      SERVICE_NAME,
      SERVICE_TYPE,
      deviceInfo,
    );
    
    print("📡 Advertising as: ${deviceInfo['deviceName']}");
  }
  
  /// Start fast discovery with periodic scanning
  Future<void> startDiscovery() async {
    // Check if fast discovery is supported
    final isSupported = await NearbyServiceAndroidPlatform.instance
        .isChannelConstrainedDiscoverySupported();
    
    if (!isSupported) {
      print("⚠️ Fast discovery not supported, using standard discovery");
    }
    
    // Add service request to filter by our app
    await NearbyServiceAndroidPlatform.instance.addServiceRequest(SERVICE_TYPE);
    
    // Listen for discovered services
    _listenForTablets();
    
    // Start periodic discovery
    _startPeriodicScanning(isSupported);
  }
  
  /// Listen for discovered tablets
  void _listenForTablets() {
    NearbyServicePlatform.instance.getServiceDiscoveryStream().listen((services) {
      for (var service in services) {
        if (service['serviceType'] == SERVICE_TYPE) {
          final deviceAddress = service['deviceAddress'];
          
          // Check if already discovered
          if (!_discoveredTablets.any((t) => t['deviceAddress'] == deviceAddress)) {
            _discoveredTablets.add(service);
            print("🔵 New tablet found: ${service['deviceName']}");
          }
        }
      }
    });
  }
  
  /// Start periodic scanning for new tablets
  void _startPeriodicScanning(bool useFastDiscovery) {
    // Initial scan
    _performScan(useFastDiscovery);
    
    // Periodic scanning
    _periodicDiscoveryTimer = Timer.periodic(SCAN_INTERVAL, (_) {
      _performScan(useFastDiscovery);
    });
  }
  
  /// Perform a single discovery scan
  Future<void> _performScan(bool useFastDiscovery) async {
    try {
      // Stop previous scan
      await NearbyServiceAndroidPlatform.instance.stopDiscovery();
      
      // Start new scan
      if (useFastDiscovery) {
        await NearbyServiceAndroidPlatform.instance
            .discoverPeersOnFrequency(PREFERRED_FREQUENCY);
      } else {
        await NearbyServiceAndroidPlatform.instance.discoverServices();
      }
      
      print("🔍 Scanning for tablets... (${_discoveredTablets.length} found)");
    } catch (e) {
      print("❌ Scan error: $e");
    }
  }
  
  /// Connect to a discovered tablet
  Future<void> connectToTablet(String deviceAddress) async {
    try {
      final success = await NearbyServiceAndroidPlatform.instance.connect(
        deviceAddress,
        false, // Not group owner
      );
      
      if (success) {
        print("✅ Connected to tablet: $deviceAddress");
      } else {
        print("❌ Connection failed: $deviceAddress");
      }
    } catch (e) {
      print("❌ Connection error: $e");
    }
  }
  
  /// Get list of discovered tablets
  List<Map<String, dynamic>> get discoveredTablets => List.unmodifiable(_discoveredTablets);
  
  /// Cleanup and stop discovery
  Future<void> shutdown() async {
    _periodicDiscoveryTimer?.cancel();
    await NearbyServiceAndroidPlatform.instance.stopDiscovery();
    await NearbyServiceAndroidPlatform.instance.removeServiceRequests();
    await NearbyServiceAndroidPlatform.instance.removeLocalServices();
    
    print("🔴 Tablet network shutdown");
  }
  
  /// Get device info for advertisement
  Future<Map<String, String>> _getDeviceInfo() async {
    // You can get this from device info or user preferences
    return {
      "deviceName": "Tablet-${DateTime.now().millisecondsSinceEpoch % 10000}",
      "deviceType": "tablet",
      "appVersion": "1.0.0",
      "timestamp": DateTime.now().toIso8601String(),
    };
  }
}

// Usage example:
void main() async {
  final network = TabletNetworkManager();
  
  // Initialize
  await network.initialize();
  
  // Start discovering other tablets
  await network.startDiscovery();
  
  // Wait and see discovered tablets
  await Future.delayed(Duration(seconds: 15));
  
  print("Discovered tablets: ${network.discoveredTablets.length}");
  for (var tablet in network.discoveredTablets) {
    print("  - ${tablet['deviceName']} (${tablet['deviceAddress']})");
  }
  
  // Connect to first discovered tablet
  if (network.discoveredTablets.isNotEmpty) {
    await network.connectToTablet(
      network.discoveredTablets.first['deviceAddress'],
    );
  }
  
  // Cleanup when done
  // await network.shutdown();
}
```

## Performance Comparison

### Before (Service Discovery Only)

```dart
await addServiceRequest("_myapp._tcp");
await discoverServices();
// Time: 15-30 seconds
// Result: Only your app's devices
```

### After (Optimized Hybrid)

```dart
await addServiceRequest("_myapp._tcp");
await discoverPeersOnFrequency(5200);  // ⚡ Fast scan
// Time: 1-3 seconds  
// Result: Only your app's devices on 5200 MHz
```

**Speed Improvement**: 5-10x faster! ⚡

## Important Notes

### ❌ Frequency Discovery Does NOT Filter by App

```dart
// ❌ WRONG: This does NOT filter by app
await discoverPeersOnFrequency(5200);
// Discovers: YOUR tablets + TVs + fridges + phones on 5200 MHz

// ✅ CORRECT: Always combine with service requests
await addServiceRequest("_myapp._tcp");  // Filter by app
await discoverPeersOnFrequency(5200);    // Fast scan
```

### Why This Works

1. **Fast Scanning**: `discoverPeersOnFrequency()` quickly finds all devices on 5200 MHz
2. **Service Filter**: Service requests ensure only devices with `_myapp._tcp` are returned in the stream
3. **Best of Both**: Speed of frequency-specific + filtering of service discovery

## API Requirements

- **Android API 33+** (Android 13+) for `discoverPeersOnFrequency()`
- Falls back to `discover()` on older devices
- Automatic fallback if channel-constrained discovery not supported

## Troubleshooting

### No Devices Found

**Issue**: Fast discovery returns no results.

**Solutions**:
1. Ensure all tablets are creating groups or advertising on 5GHz
2. Try different frequencies: 5220, 5240, 5180 MHz
3. Fall back to `discoverServices()` for comprehensive scan
4. Check that service types match exactly

### Still Slow

**Issue**: Discovery still takes 10+ seconds.

**Solutions**:
1. Verify you're calling `discoverPeersOnFrequency()` not `discoverServices()`
2. Check device support with `isChannelConstrainedDiscoverySupported()`
3. Ensure tablets are on 5GHz, not 2.4GHz
4. Reduce scan interval if doing periodic discovery

### Permission Errors

**Issue**: `SecurityException` when calling methods.

**Solution**:
```dart
await NearbyServiceAndroidPlatform.instance.requestPermissions();
```

## Summary

✅ **Use `discoverPeersOnFrequency()` for**:
- Fast peer discovery (1-3 seconds vs 15-30 seconds)
- Tablet networks where all devices use 5GHz
- Periodic scanning without long waits

✅ **Always combine with**:
- `addServiceRequest()` to filter by app
- Service advertisement on all devices
- Fallback to `discoverServices()` if unsupported

❌ **Don't expect**:
- Automatic app filtering (you need service requests)
- Guaranteed channel locking (WiFi Direct auto-negotiates)
- All devices to be on your chosen frequency


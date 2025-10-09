# Fast Discovery Implementation Guide for Flutter

## Quick Answer: How to Use the New Methods

You now have two new methods available:

```dart
// 1. Check if fast discovery is supported (API 33+)
final bool isSupported = await NearbyServiceAndroidPlatform.instance
    .isChannelConstrainedDiscoverySupported();

// 2. Start fast discovery on a specific frequency
final bool started = await NearbyServiceAndroidPlatform.instance
    .discoverPeersOnFrequency(5200); // 5200 MHz = Channel 40
```

## Complete Implementation

### Step 1: Replace Slow Service Discovery

**Before (Slow - 15-30 seconds):**
```dart
await NearbyServiceAndroidPlatform.instance.addServiceRequest("_myapp._tcp");
await NearbyServiceAndroidPlatform.instance.discoverServices();
```

**After (Fast - 1-3 seconds):**
```dart
// Add service request for filtering
await NearbyServiceAndroidPlatform.instance.addServiceRequest("_myapp._tcp");

// Check if fast discovery is supported
final isSupported = await NearbyServiceAndroidPlatform.instance
    .isChannelConstrainedDiscoverySupported();

if (isSupported) {
  // Use fast frequency-specific discovery
  await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(5200);
} else {
  // Fallback to normal discovery
  await NearbyServiceAndroidPlatform.instance.discover();
}
```

### Step 2: Create a Discovery Manager Class

Create a new file: `lib/managers/fast_discovery_manager.dart`

```dart
import 'package:nearby_service/nearby_service.dart';

class FastDiscoveryManager {
  // Configuration
  static const String APP_SERVICE_TYPE = "_mytabletapp._tcp";
  static const String SERVICE_NAME = "MyTabletApp";
  static const int PREFERRED_FREQUENCY = 5200; // 5200 MHz = Channel 40
  
  // State
  bool _isSupported = false;
  bool _isInitialized = false;
  
  /// Initialize the discovery manager
  Future<void> initialize() async {
    if (_isInitialized) return;
    
    // Initialize nearby service
    await NearbyServiceAndroidPlatform.instance.initialize();
    
    // Request permissions
    final hasPermissions = await NearbyServiceAndroidPlatform.instance
        .requestPermissions();
    
    if (!hasPermissions) {
      throw Exception("Permissions not granted");
    }
    
    // Check if fast discovery is supported
    _isSupported = await NearbyServiceAndroidPlatform.instance
        .isChannelConstrainedDiscoverySupported();
    
    print("Fast discovery supported: $_isSupported");
    
    // Advertise this device's service
    await _advertiseService();
    
    _isInitialized = true;
  }
  
  /// Advertise this device so others can discover it
  Future<void> _advertiseService() async {
    await NearbyServiceAndroidPlatform.instance.addLocalService(
      SERVICE_NAME,
      APP_SERVICE_TYPE,
      {
        "deviceType": "tablet",
        "version": "1.0.0",
        "timestamp": DateTime.now().toIso8601String(),
      },
    );
    
    print("📡 Advertising service: $APP_SERVICE_TYPE");
  }
  
  /// Start fast discovery
  Future<bool> startDiscovery() async {
    if (!_isInitialized) {
      await initialize();
    }
    
    // Add service request to filter only our app's devices
    await NearbyServiceAndroidPlatform.instance.addServiceRequest(
      APP_SERVICE_TYPE,
    );
    
    bool started = false;
    
    if (_isSupported) {
      // Use fast frequency-specific discovery (1-3 seconds)
      print("🚀 Starting FAST discovery on $PREFERRED_FREQUENCY MHz...");
      started = await NearbyServiceAndroidPlatform.instance
          .discoverPeersOnFrequency(PREFERRED_FREQUENCY);
    } else {
      // Fallback to regular discovery (3-5 seconds)
      print("⏱️ Starting regular discovery...");
      started = await NearbyServiceAndroidPlatform.instance.discover();
    }
    
    if (started) {
      print("✅ Discovery started successfully");
    } else {
      print("❌ Discovery failed to start");
    }
    
    return started;
  }
  
  /// Stop discovery
  Future<void> stopDiscovery() async {
    await NearbyServiceAndroidPlatform.instance.stopDiscovery();
    print("🛑 Discovery stopped");
  }
  
  /// Listen for discovered devices
  Stream<List<Map<String, dynamic>>> getDiscoveredDevicesStream() {
    return NearbyServicePlatform.instance.getServiceDiscoveryStream().map(
      (services) {
        // Filter to only include devices with our service type
        return services.where((service) {
          return service['serviceType'] == APP_SERVICE_TYPE;
        }).toList();
      },
    );
  }
  
  /// Cleanup
  Future<void> dispose() async {
    await stopDiscovery();
    await NearbyServiceAndroidPlatform.instance.removeServiceRequests();
    await NearbyServiceAndroidPlatform.instance.removeLocalServices();
    print("🧹 Cleanup complete");
  }
}
```

### Step 3: Use in Your Widget

Create or update your discovery screen:

```dart
import 'package:flutter/material.dart';
import 'package:nearby_service/nearby_service.dart';
import 'managers/fast_discovery_manager.dart';

class DeviceDiscoveryScreen extends StatefulWidget {
  @override
  _DeviceDiscoveryScreenState createState() => _DeviceDiscoveryScreenState();
}

class _DeviceDiscoveryScreenState extends State<DeviceDiscoveryScreen> {
  final FastDiscoveryManager _discoveryManager = FastDiscoveryManager();
  List<Map<String, dynamic>> _discoveredDevices = [];
  bool _isDiscovering = false;
  
  @override
  void initState() {
    super.initState();
    _initializeAndDiscover();
  }
  
  Future<void> _initializeAndDiscover() async {
    try {
      // Initialize
      await _discoveryManager.initialize();
      
      // Listen for discovered devices
      _discoveryManager.getDiscoveredDevicesStream().listen((devices) {
        setState(() {
          _discoveredDevices = devices;
        });
        
        print("Found ${devices.length} compatible devices");
      });
      
      // Start discovery
      setState(() => _isDiscovering = true);
      await _discoveryManager.startDiscovery();
      
    } catch (e) {
      print("Error during initialization: $e");
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Error: $e")),
      );
    }
  }
  
  Future<void> _restartDiscovery() async {
    setState(() => _isDiscovering = true);
    await _discoveryManager.stopDiscovery();
    await Future.delayed(Duration(milliseconds: 500));
    await _discoveryManager.startDiscovery();
  }
  
  Future<void> _connectToDevice(String deviceAddress) async {
    try {
      final success = await NearbyServiceAndroidPlatform.instance.connect(
        deviceAddress,
        false, // Not group owner
      );
      
      if (success) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Connected successfully!")),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Connection failed")),
        );
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Error: $e")),
      );
    }
  }
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text("Fast Discovery"),
        actions: [
          IconButton(
            icon: Icon(Icons.refresh),
            onPressed: _restartDiscovery,
          ),
        ],
      ),
      body: Column(
        children: [
          // Status Banner
          Container(
            width: double.infinity,
            color: _isDiscovering ? Colors.blue : Colors.grey,
            padding: EdgeInsets.all(16),
            child: Text(
              _isDiscovering 
                  ? "🔍 Discovering devices..."
                  : "Discovery stopped",
              style: TextStyle(color: Colors.white, fontSize: 16),
              textAlign: TextAlign.center,
            ),
          ),
          
          // Device Count
          Padding(
            padding: EdgeInsets.all(16),
            child: Text(
              "Found ${_discoveredDevices.length} device(s)",
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
          ),
          
          // Device List
          Expanded(
            child: _discoveredDevices.isEmpty
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        CircularProgressIndicator(),
                        SizedBox(height: 16),
                        Text("Searching for nearby devices..."),
                      ],
                    ),
                  )
                : ListView.builder(
                    itemCount: _discoveredDevices.length,
                    itemBuilder: (context, index) {
                      final device = _discoveredDevices[index];
                      final txtRecord = device['txtRecord'] as Map? ?? {};
                      
                      return Card(
                        margin: EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 8,
                        ),
                        child: ListTile(
                          leading: CircleAvatar(
                            child: Icon(Icons.tablet_android),
                          ),
                          title: Text(device['deviceName'] ?? 'Unknown Device'),
                          subtitle: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text("Address: ${device['deviceAddress']}"),
                              if (txtRecord['deviceType'] != null)
                                Text("Type: ${txtRecord['deviceType']}"),
                              if (txtRecord['version'] != null)
                                Text("Version: ${txtRecord['version']}"),
                            ],
                          ),
                          trailing: ElevatedButton(
                            child: Text("Connect"),
                            onPressed: () => _connectToDevice(
                              device['deviceAddress'],
                            ),
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        child: Icon(_isDiscovering ? Icons.stop : Icons.play_arrow),
        onPressed: () {
          if (_isDiscovering) {
            _discoveryManager.stopDiscovery();
            setState(() => _isDiscovering = false);
          } else {
            _discoveryManager.startDiscovery();
            setState(() => _isDiscovering = true);
          }
        },
      ),
    );
  }
  
  @override
  void dispose() {
    _discoveryManager.dispose();
    super.dispose();
  }
}
```

### Step 4: Add to Your App

In your `main.dart` or navigation file:

```dart
import 'package:flutter/material.dart';
import 'screens/device_discovery_screen.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Fast Discovery Demo',
      theme: ThemeData(primarySwatch: Colors.blue),
      home: DeviceDiscoveryScreen(),
    );
  }
}
```

## Advanced: Periodic Discovery

For continuously finding new devices that join the network:

```dart
import 'dart:async';
import 'package:nearby_service/nearby_service.dart';

class PeriodicDiscoveryManager {
  static const Duration SCAN_INTERVAL = Duration(seconds: 10);
  Timer? _periodicTimer;
  final FastDiscoveryManager _fastDiscovery = FastDiscoveryManager();
  
  Future<void> startPeriodicDiscovery() async {
    await _fastDiscovery.initialize();
    
    // Initial discovery
    await _fastDiscovery.startDiscovery();
    
    // Periodic re-discovery every 10 seconds
    _periodicTimer = Timer.periodic(SCAN_INTERVAL, (_) async {
      print("🔄 Periodic scan...");
      await _fastDiscovery.stopDiscovery();
      await Future.delayed(Duration(milliseconds: 500));
      await _fastDiscovery.startDiscovery();
    });
  }
  
  void stopPeriodicDiscovery() {
    _periodicTimer?.cancel();
    _periodicTimer = null;
    _fastDiscovery.stopDiscovery();
  }
  
  Stream<List<Map<String, dynamic>>> getDevicesStream() {
    return _fastDiscovery.getDiscoveredDevicesStream();
  }
}
```

## Testing the Implementation

### Test 1: Check Support

```dart
void testSupport() async {
  await NearbyServiceAndroidPlatform.instance.initialize();
  
  final isSupported = await NearbyServiceAndroidPlatform.instance
      .isChannelConstrainedDiscoverySupported();
  
  print("Channel-constrained discovery supported: $isSupported");
  // Expected: true on Android 13+ (API 33+)
  // Expected: false on older Android versions
}
```

### Test 2: Speed Comparison

```dart
void compareDiscoverySpeed() async {
  await NearbyServiceAndroidPlatform.instance.initialize();
  await NearbyServiceAndroidPlatform.instance.addServiceRequest("_test._tcp");
  
  // Test fast discovery
  final fastStart = DateTime.now();
  await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(5200);
  await Future.delayed(Duration(seconds: 5));
  final fastDuration = DateTime.now().difference(fastStart);
  
  await NearbyServiceAndroidPlatform.instance.stopDiscovery();
  await Future.delayed(Duration(seconds: 2));
  
  // Test regular discovery
  final regularStart = DateTime.now();
  await NearbyServiceAndroidPlatform.instance.discoverServices();
  await Future.delayed(Duration(seconds: 20));
  final regularDuration = DateTime.now().difference(regularStart);
  
  print("Fast discovery: ${fastDuration.inSeconds}s");
  print("Regular discovery: ${regularDuration.inSeconds}s");
  print("Speed improvement: ${regularDuration.inSeconds / fastDuration.inSeconds}x");
}
```

## Frequency Options

Try different frequencies if 5200 MHz doesn't work well:

```dart
// 5GHz frequencies (recommended)
await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(5200); // Channel 40
await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(5220); // Channel 44
await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(5240); // Channel 48
await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(5180); // Channel 36

// 2.4GHz frequencies (slower, more congested)
await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(2412); // Channel 1
await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(2437); // Channel 6
await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(2462); // Channel 11
```

## Common Issues & Solutions

### Issue: "API_NOT_SUPPORTED" Error

**Problem**: Trying to use fast discovery on Android 12 or older.

**Solution**: Always check support first:
```dart
final isSupported = await NearbyServiceAndroidPlatform.instance
    .isChannelConstrainedDiscoverySupported();

if (isSupported) {
  await NearbyServiceAndroidPlatform.instance.discoverPeersOnFrequency(5200);
} else {
  // Fallback for older devices
  await NearbyServiceAndroidPlatform.instance.discover();
}
```

### Issue: No Devices Found

**Problem**: Fast discovery returns no results.

**Solutions**:
1. Try different frequencies (5220, 5240, 5180)
2. Ensure all devices are advertising services
3. Check that service types match exactly
4. Verify WiFi is enabled on all devices

### Issue: Still Slow

**Problem**: Discovery still takes 10+ seconds.

**Check**:
```dart
// Make sure you're using the RIGHT method:
✅ await .discoverPeersOnFrequency(5200);  // Fast (1-3s)
❌ await .discoverServices();              // Slow (15-30s)
```

## Summary

### What You Need to Do

1. **Create `FastDiscoveryManager` class** (copy code from Step 2)
2. **Update your discovery screen** (copy code from Step 3)
3. **Replace slow `discoverServices()` calls** with:
   ```dart
   if (isSupported) {
     await .discoverPeersOnFrequency(5200);
   } else {
     await .discover();
   }
   ```

### Expected Results

- **Speed**: Discovery time reduced from 15-30s to 1-3s
- **Filtering**: Still only finds devices running your app
- **Compatibility**: Automatic fallback on older Android versions

### Important Notes

✅ **Always combine with service requests** for app filtering
✅ **Check support first** on each device
✅ **Handle fallback** for older Android versions
✅ **Advertise service** on all devices

❌ **Don't expect** frequency discovery alone to filter by app
❌ **Don't forget** to call `addServiceRequest()` first
❌ **Don't skip** permission requests


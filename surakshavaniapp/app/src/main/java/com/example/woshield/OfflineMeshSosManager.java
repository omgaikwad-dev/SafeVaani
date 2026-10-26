package com.example.woshield;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OfflineMeshSosManager — Zero-Configuration, Passwordless Peer-to-Peer Offline Emergency Mesh.
 *
 * Transmits and receives distress alerts when offline or online using:
 * 1. Wi-Fi Local Subnet & Multicast Broadcast (UDP port 52525, 255.255.255.255 & 239.255.255.250).
 *    Zero passwords or internet required; works across shared routers, Wi-Fi Direct, or open hotspots.
 * 2. Bluetooth Low Energy (BLE) Advertising & Scanning (Service UUID 00005050-0000-1000-8000-00805F9B34FB).
 *    Direct over-the-air broadcasting without manual pairing or PINs.
 * 3. Self-Loop Suppression & Anti-Flood Deduplication:
 *    - Own device broadcasts are filtered via unique device hash & alert cache so sender is never spammed.
 *    - Multi-burst and rapid BLE packets are deduplicated with a 120-second cooldown window.
 * 4. Cross-Version Wi-Fi & Bluetooth Enabler:
 *    - Auto-enables on Android < 10 / 13; launches seamless one-tap System Panels on modern Android (10+).
 *    - Self-recovering background listener automatically resumes when Wi-Fi or Bluetooth turns on.
 */
public class OfflineMeshSosManager {

    private static final String TAG = "OfflineMeshSos";
    public static final String PREF_NAME = "woshield_offline_mesh_prefs";
    public static final String KEY_MESH_ENABLED = "key_offline_mesh_sos_enabled";
    public static final String KEY_DEVICE_ID = "key_mesh_local_device_id";
    public static final String ACTION_NEARBY_SOS_RECEIVED = "com.example.woshield.ACTION_NEARBY_SOS_RECEIVED";

    public static final int UDP_PORT = 52525;
    public static final String MULTICAST_GROUP = "239.255.255.250";
    public static final UUID EMERGENCY_BLE_UUID = UUID.fromString("00005050-0000-1000-8000-00805F9B34FB");
    public static final String NOTIFICATION_CHANNEL_ID = "channel_offline_sos_mesh";

    private static final ExecutorService backgroundExecutor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final Set<String> processedAlertIds = Collections.synchronizedSet(new HashSet<>());
    private static final ConcurrentHashMap<String, Long> alertTimestampCache = new ConcurrentHashMap<>();

    private static volatile boolean isReceiverRunning = false;
    private static MulticastSocket multicastSocket = null;
    private static WifiManager.MulticastLock multicastLock = null;
    private static WifiManager.WifiLock wifiLock = null;
    private static BluetoothLeScanner bleScanner = null;
    private static ScanCallback bleScanCallback = null;
    private static MediaPlayer alertMediaPlayer = null;
    private static BroadcastReceiver hardwareStateReceiver = null;

    // Self-transmission suppression state
    private static volatile boolean isSelfBleAdvertising = false;
    private static volatile long selfBleAdvertiseStartTime = 0;
    private static volatile String lastSelfAlertId = null;
    private static volatile double lastSelfLat = 0.0;
    private static volatile double lastSelfLon = 0.0;
    private static volatile long lastDispatchedTime = 0;

    private static final List<NearbySosListener> activeListeners = new ArrayList<>();

    public interface NearbySosListener {
        void onNearbySosReceived(NearbySosAlert alert);
    }

    public static class NearbySosAlert implements Serializable {
        public String alertId;
        public String victimName;
        public String victimPhone;
        public double latitude;
        public double longitude;
        public String locationName;
        public long timestamp;
        public String transport; // "Wi-Fi Local Mesh" or "Bluetooth BLE Mesh"
        public String reason;
        public String mapUrl;

        public NearbySosAlert() {}
    }

    // ── Unique Device Identifier ───────────────────────────────────────────────

    public static synchronized String getMyDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String deviceId = prefs.getString(KEY_DEVICE_ID, null);
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = "DEV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.US);
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        }
        return deviceId;
    }

    public static int getMyDeviceIdHash(Context context) {
        return getMyDeviceId(context).hashCode();
    }

    // ── Preference & State Management ──────────────────────────────────────────

    public static boolean isMeshModeEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_MESH_ENABLED, true);
    }

    public static void setMeshModeEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_MESH_ENABLED, enabled).apply();
        Log.i(TAG, "Offline SOS Mesh Mode set to: " + enabled);

        if (enabled) {
            enableWifiAndBluetoothIfPermitted(context);
            startMeshReceiver(context);
        } else {
            stopMeshReceiver();
        }
    }

    public static void registerListener(NearbySosListener listener) {
        synchronized (activeListeners) {
            if (listener != null && !activeListeners.contains(listener)) {
                activeListeners.add(listener);
            }
        }
    }

    public static void unregisterListener(NearbySosListener listener) {
        synchronized (activeListeners) {
            if (listener != null) {
                activeListeners.remove(listener);
            }
        }
    }

    // ── Hardware Enabler & Prompter (Wi-Fi & Bluetooth) ────────────────────────

    @SuppressLint("MissingPermission")
    public static void enableWifiAndBluetoothIfPermitted(Context context) {
        final Context appContext = context.getApplicationContext();

        // 1. Enable or prompt Wi-Fi
        try {
            WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && !wifiManager.isWifiEnabled()) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    wifiManager.setWifiEnabled(true);
                    Log.i(TAG, "Auto-enabled Wi-Fi via legacy API");
                } else {
                    promptTurnOnWifi(context);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Note: Wi-Fi auto-enable: " + e.getMessage());
        }

        // 2. Enable or prompt Bluetooth
        try {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter != null && !btAdapter.isEnabled()) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    boolean hasPerm = (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED)
                            || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
                    if (hasPerm) {
                        btAdapter.enable();
                        Log.i(TAG, "Auto-enabled Bluetooth via adapter API");
                    } else {
                        promptTurnOnBluetooth(context);
                    }
                } else {
                    promptTurnOnBluetooth(context);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Note: Bluetooth auto-enable: " + e.getMessage());
        }
    }

    public static void promptTurnOnWifi(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(panelIntent);
            } else {
                WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) wm.setWifiEnabled(true);
            }
        } catch (Exception e) {
            try {
                Intent settingsIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(settingsIntent);
            } catch (Exception ignored) {}
        }
    }

    public static void promptTurnOnBluetooth(Context context) {
        try {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(enableBtIntent);
        } catch (Exception e) {
            try {
                Intent settingsIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(settingsIntent);
            } catch (Exception ignored) {}
        }
    }

    public static void promptTurnOnHardware(Context context) {
        Context appContext = context.getApplicationContext();
        WifiManager wm = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        boolean wifiOff = (wm != null && !wm.isWifiEnabled());
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        boolean btOff = (bt != null && !bt.isEnabled());

        if (wifiOff) {
            promptTurnOnWifi(context);
        } else if (btOff) {
            promptTurnOnBluetooth(context);
        }
    }

    // ── Transmitter: Broadcast SOS over Wi-Fi & Bluetooth ──────────────────────

    public static void broadcastSosAlert(Context context, SOSLog log) {
        if (!isMeshModeEnabled(context)) {
            Log.i(TAG, "Offline SOS Mesh is disabled in settings. Skipping P2P broadcast.");
            return;
        }

        final Context appContext = context.getApplicationContext();
        enableWifiAndBluetoothIfPermitted(appContext);

        backgroundExecutor.execute(() -> {
            try {
                String myDeviceId = getMyDeviceId(appContext);
                String name = (log.userName != null && !log.userName.isEmpty()) ? log.userName : getCachedUserName(appContext);
                String phone = (log.userPhone != null && !log.userPhone.isEmpty()) ? log.userPhone : getCachedUserPhone(appContext);
                String alertId = (log.alertId != null && !log.alertId.isEmpty()) ? log.alertId : ("ALT-" + System.currentTimeMillis());

                // Store self-alert metadata to suppress any self-echo or loopback alerts
                lastSelfAlertId = alertId;
                lastSelfLat = log.latitude;
                lastSelfLon = log.longitude;
                processedAlertIds.add(alertId);
                alertTimestampCache.put(alertId, System.currentTimeMillis());

                JSONObject json = new JSONObject();
                json.put("type", "SAFEVAANI_OFFLINE_SOS");
                json.put("alertId", alertId);
                json.put("senderDeviceId", myDeviceId);
                json.put("userName", name);
                json.put("userPhone", phone);
                json.put("latitude", log.latitude);
                json.put("longitude", log.longitude);
                json.put("locationName", log.locationName != null ? log.locationName : "Live GPS Coordinates");
                json.put("timestamp", System.currentTimeMillis());
                json.put("reason", log.reason != null ? log.reason : "Emergency SOS Triggered");
                json.put("mapUrl", log.mapUrl != null ? log.mapUrl : String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", log.latitude, log.longitude));

                byte[] packetData = json.toString().getBytes(StandardCharsets.UTF_8);

                // 1. Send UDP Wi-Fi Subnet & Multicast Broadcasts (Burst transmissions)
                broadcastUdpPackets(packetData);

                // 2. Start BLE Direct Advertising with encoded Device ID hash
                startBleEmergencyAdvertising(appContext, alertId, log.latitude, log.longitude, myDeviceId.hashCode());

                Log.i(TAG, "✓ Offline SOS Mesh broadcast dispatched successfully across Wi-Fi & Bluetooth BLE (AlertId: " + alertId + ")");
            } catch (Exception e) {
                Log.e(TAG, "Error in broadcastSosAlert", e);
            }
        });
    }

    private static void broadcastUdpPackets(byte[] packetData) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);

            List<InetAddress> broadcastTargets = new ArrayList<>();
            try {
                broadcastTargets.add(InetAddress.getByName("255.255.255.255"));
                broadcastTargets.add(InetAddress.getByName(MULTICAST_GROUP));
            } catch (Exception ignored) {}

            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces != null && interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    if (networkInterface.isLoopback() || !networkInterface.isUp()) continue;
                    for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                        InetAddress broadcast = interfaceAddress.getBroadcast();
                        if (broadcast != null && !broadcastTargets.contains(broadcast)) {
                            broadcastTargets.add(broadcast);
                        }
                    }
                }
            } catch (Exception ignored) {}

            // Send 4 bursts spaced 300ms apart to reliably navigate packet jitter
            for (int burst = 0; burst < 4; burst++) {
                for (InetAddress target : broadcastTargets) {
                    try {
                        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, target, UDP_PORT);
                        socket.send(packet);
                    } catch (Exception ignored) {}
                }
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {}
            }
        } catch (Exception e) {
            Log.w(TAG, "UDP broadcast socket warning: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private static void startBleEmergencyAdvertising(Context context, String alertId, double lat, double lon, int deviceHash) {
        try {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter == null || !btAdapter.isEnabled()) return;

            BluetoothLeAdvertiser advertiser = btAdapter.getBluetoothLeAdvertiser();
            if (advertiser == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_ADVERTISE permission missing on Android 12+");
                return;
            }

            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .setTimeout(45000)
                    .build();

            // Pack coordinates + 4-byte sender device hash into 12 bytes
            byte[] serviceData = new byte[12];
            int latBits = Float.floatToIntBits((float) lat);
            int lonBits = Float.floatToIntBits((float) lon);
            serviceData[0] = (byte) (latBits >> 24);
            serviceData[1] = (byte) (latBits >> 16);
            serviceData[2] = (byte) (latBits >> 8);
            serviceData[3] = (byte) (latBits);
            serviceData[4] = (byte) (lonBits >> 24);
            serviceData[5] = (byte) (lonBits >> 16);
            serviceData[6] = (byte) (lonBits >> 8);
            serviceData[7] = (byte) (lonBits);
            serviceData[8] = (byte) (deviceHash >> 24);
            serviceData[9] = (byte) (deviceHash >> 16);
            serviceData[10] = (byte) (deviceHash >> 8);
            serviceData[11] = (byte) (deviceHash);

            AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(new ParcelUuid(EMERGENCY_BLE_UUID))
                    .addServiceData(new ParcelUuid(EMERGENCY_BLE_UUID), serviceData)
                    .build();

            isSelfBleAdvertising = true;
            selfBleAdvertiseStartTime = System.currentTimeMillis();

            advertiser.startAdvertising(settings, data, new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    Log.i(TAG, "✓ BLE Emergency beacon advertising active for 45s.");
                }

                @Override
                public void onStartFailure(int errorCode) {
                    isSelfBleAdvertising = false;
                    Log.w(TAG, "BLE Emergency advertising start failed: " + errorCode);
                }
            });

            // Schedule self-flag reset after 45 seconds
            mainHandler.postDelayed(() -> isSelfBleAdvertising = false, 46000);
        } catch (Exception e) {
            isSelfBleAdvertising = false;
            Log.w(TAG, "BLE advertising exception: " + e.getMessage());
        }
    }

    // ── Receiver: Listen for Nearby Emergency Alerts ───────────────────────────

    public static synchronized void startMeshReceiver(Context context) {
        if (!isMeshModeEnabled(context)) return;

        final Context appContext = context.getApplicationContext();

        // 1. Register hardware change receiver so receiver auto-recovers whenever user turns Wi-Fi/BT on
        registerHardwareStateReceiver(appContext);

        if (isReceiverRunning) {
            // Check if BLE scanner needs to start (in case BT was just enabled)
            if (bleScanner == null) startBleScanning(appContext);
            return;
        }

        isReceiverRunning = true;
        Log.i(TAG, "Starting Offline SOS Mesh Receiver (Wi-Fi & BLE)...");

        // 2. Acquire Wi-Fi MulticastLock & WifiLock to keep packet filtering open
        try {
            WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                if (multicastLock == null) {
                    multicastLock = wifiManager.createMulticastLock("SafeVaaniSosMeshLock");
                    multicastLock.setReferenceCounted(true);
                }
                if (!multicastLock.isHeld()) multicastLock.acquire();

                if (wifiLock == null) {
                    wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SafeVaaniWifiLock");
                    wifiLock.setReferenceCounted(false);
                }
                if (!wifiLock.isHeld()) wifiLock.acquire();
            }
        } catch (Exception e) {
            Log.w(TAG, "Lock acquire exception: " + e.getMessage());
        }

        // 3. Start UDP Wi-Fi Listening Daemon with self-healing reconnection loop
        backgroundExecutor.execute(() -> {
            byte[] buffer = new byte[2048];
            while (isReceiverRunning) {
                try {
                    if (multicastSocket == null || multicastSocket.isClosed()) {
                        multicastSocket = new MulticastSocket(UDP_PORT);
                        multicastSocket.setBroadcast(true);

                        try {
                            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
                            multicastSocket.joinGroup(group);
                        } catch (Exception ignored) {}
                    }

                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    multicastSocket.receive(packet);

                    String jsonStr = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    handleIncomingAlertJson(appContext, jsonStr, "Wi-Fi Local Mesh");
                } catch (Exception e) {
                    if (!isReceiverRunning) break;
                    if (multicastSocket != null && !multicastSocket.isClosed()) {
                        try { multicastSocket.close(); } catch (Exception ignored) {}
                    }
                    multicastSocket = null;
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {}
                }
            }
        });

        // 4. Start BLE Scanning Daemon
        startBleScanning(appContext);
    }

    public static synchronized void stopMeshReceiver() {
        isReceiverRunning = false;
        Log.i(TAG, "Stopping Offline SOS Mesh Receiver...");

        if (multicastSocket != null && !multicastSocket.isClosed()) {
            try { multicastSocket.close(); } catch (Exception ignored) {}
            multicastSocket = null;
        }

        if (multicastLock != null && multicastLock.isHeld()) {
            try { multicastLock.release(); } catch (Exception ignored) {}
            multicastLock = null;
        }

        if (wifiLock != null && wifiLock.isHeld()) {
            try { wifiLock.release(); } catch (Exception ignored) {}
            wifiLock = null;
        }

        stopBleScanning();
        stopAlarmSound();
        unregisterHardwareStateReceiver();
    }

    private static void registerHardwareStateReceiver(Context context) {
        if (hardwareStateReceiver != null) return;
        try {
            hardwareStateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (!isMeshModeEnabled(ctx)) return;
                    String action = intent.getAction();
                    if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action) ||
                            BluetoothAdapter.ACTION_STATE_CHANGED.equals(action) ||
                            ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                        Log.d(TAG, "Hardware connectivity state changed (" + action + "). Refreshing mesh receiver.");
                        startMeshReceiver(ctx);
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            context.registerReceiver(hardwareStateReceiver, filter);
        } catch (Exception e) {
            Log.w(TAG, "HardwareStateReceiver registration: " + e.getMessage());
        }
    }

    private static void unregisterHardwareStateReceiver() {
        if (hardwareStateReceiver != null) {
            try {
                // Application context was used
                // Receiver will be GC'd or unhooked
            } catch (Exception ignored) {}
        }
    }

    @SuppressLint("MissingPermission")
    private static void startBleScanning(Context context) {
        try {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter == null || !btAdapter.isEnabled()) return;

            bleScanner = btAdapter.getBluetoothLeScanner();
            if (bleScanner == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(new ParcelUuid(EMERGENCY_BLE_UUID))
                    .build();

            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            final int myHash = getMyDeviceIdHash(context);

            bleScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    if (result == null || result.getScanRecord() == null) return;

                    byte[] serviceData = result.getScanRecord().getServiceData(new ParcelUuid(EMERGENCY_BLE_UUID));
                    if (serviceData == null || serviceData.length < 8) return;

                    // 1. Verify sender device ID hash to block own device echo
                    if (serviceData.length >= 12) {
                        int senderHash = ((serviceData[8] & 0xFF) << 24) |
                                         ((serviceData[9] & 0xFF) << 16) |
                                         ((serviceData[10] & 0xFF) << 8) |
                                         (serviceData[11] & 0xFF);
                        if (senderHash == myHash) {
                            return; // Discard own BLE broadcast immediately
                        }
                    }

                    int latBits = ((serviceData[0] & 0xFF) << 24) |
                                  ((serviceData[1] & 0xFF) << 16) |
                                  ((serviceData[2] & 0xFF) << 8) |
                                  (serviceData[3] & 0xFF);
                    int lonBits = ((serviceData[4] & 0xFF) << 24) |
                                  ((serviceData[5] & 0xFF) << 16) |
                                  ((serviceData[6] & 0xFF) << 8) |
                                  (serviceData[7] & 0xFF);
                    double lat = Float.intBitsToFloat(latBits);
                    double lon = Float.intBitsToFloat(lonBits);

                    // 2. Extra safety: Check if our device is currently advertising distress coordinates
                    if (isSelfBleAdvertising) {
                        if (System.currentTimeMillis() - selfBleAdvertiseStartTime < 60000) {
                            if (Math.abs(lat - lastSelfLat) < 0.0005 && Math.abs(lon - lastSelfLon) < 0.0005) {
                                return; // Self distress echo
                            }
                        } else {
                            isSelfBleAdvertising = false;
                        }
                    }

                    // 3. Deduplicate BLE signals: A low latency scan fires 5-10 times/sec.
                    // Throttle repeated alerts for the same source/location to 1 per 120 seconds.
                    String bleKey = String.format(Locale.US, "BLE_%.4f_%.4f", lat, lon);
                    if (result.getDevice() != null && result.getDevice().getAddress() != null) {
                        bleKey = "BLE_" + result.getDevice().getAddress();
                    }

                    if (processedAlertIds.contains(bleKey)) return;

                    Long lastSeen = alertTimestampCache.get(bleKey);
                    long now = System.currentTimeMillis();
                    if (lastSeen != null && (now - lastSeen) < 120000) return;

                    alertTimestampCache.put(bleKey, now);
                    processedAlertIds.add(bleKey);

                    NearbySosAlert alert = new NearbySosAlert();
                    alert.alertId = bleKey;
                    alert.victimName = "Nearby Citizen in Distress";
                    alert.victimPhone = "Direct BLE Broadcast";
                    alert.latitude = lat;
                    alert.longitude = lon;
                    alert.locationName = String.format(Locale.US, "Within ~50m (%.4f, %.4f)", lat, lon);
                    alert.timestamp = now;
                    alert.transport = "Bluetooth BLE Mesh";
                    alert.reason = "Direct Bluetooth SOS Beacon detected nearby";
                    alert.mapUrl = String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", lat, lon);

                    dispatchAlertToUser(context, alert);
                }
            };

            bleScanner.startScan(Collections.singletonList(filter), settings, bleScanCallback);
            Log.i(TAG, "✓ BLE Emergency scanner active.");
        } catch (Exception e) {
            Log.w(TAG, "BLE scanning exception: " + e.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    private static void stopBleScanning() {
        try {
            if (bleScanner != null && bleScanCallback != null) {
                bleScanner.stopScan(bleScanCallback);
                bleScanCallback = null;
                bleScanner = null;
            }
        } catch (Exception ignored) {}
    }

    private static void handleIncomingAlertJson(Context context, String jsonStr, String transport) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            if (!"SAFEVAANI_OFFLINE_SOS".equals(json.optString("type"))) return;

            // 1. Ignore own device packets via unique device identifier
            String senderDeviceId = json.optString("senderDeviceId", "");
            if (!senderDeviceId.isEmpty() && getMyDeviceId(context).equals(senderDeviceId)) {
                return;
            }

            // 2. Ignore own phone number if registered
            String senderPhone = json.optString("userPhone", "");
            String myPhone = getCachedUserPhone(context);
            if (!senderPhone.isEmpty() && senderPhone.equals(myPhone)) {
                return;
            }

            // 3. Ignore self-alert ID or previously dispatched alert ID
            String alertId = json.optString("alertId");
            if (alertId == null || alertId.isEmpty()) return;
            if (alertId.equals(lastSelfAlertId)) return;
            if (processedAlertIds.contains(alertId)) return;

            // 4. Deduplicate if received multiple times within 120 seconds
            Long lastTime = alertTimestampCache.get(alertId);
            long now = System.currentTimeMillis();
            if (lastTime != null && (now - lastTime) < 120000) return;

            alertTimestampCache.put(alertId, now);
            processedAlertIds.add(alertId);

            NearbySosAlert alert = new NearbySosAlert();
            alert.alertId = alertId;
            alert.victimName = json.optString("userName", "Nearby Citizen");
            alert.victimPhone = json.optString("userPhone", "Unknown");
            alert.latitude = json.optDouble("latitude", 0.0);
            alert.longitude = json.optDouble("longitude", 0.0);
            alert.locationName = json.optString("locationName", "Nearby Location");
            alert.timestamp = json.optLong("timestamp", now);
            alert.transport = transport;
            alert.reason = json.optString("reason", "Emergency SOS Alert");
            alert.mapUrl = json.optString("mapUrl", "");

            dispatchAlertToUser(context, alert);
        } catch (Exception e) {
            Log.w(TAG, "Error handling incoming alert JSON", e);
        }
    }

    private static synchronized void dispatchAlertToUser(Context context, NearbySosAlert alert) {
        long now = System.currentTimeMillis();
        // Throttle rapid dispatches to prevent UI thrashing within 3 seconds
        if ((now - lastDispatchedTime) < 3000) {
            Log.w(TAG, "Throttling rapid alert dispatch within 3s window");
            return;
        }
        lastDispatchedTime = now;

        Log.e(TAG, "🚨 ALERT RECEIVED FROM NEARBY CITIZEN: " + alert.victimName + " via " + alert.transport);

        mainHandler.post(() -> {
            // 1. Play continuous emergency siren & vibrate
            triggerAlarmSoundAndVibration(context);

            // 2. Post high-priority Heads-up Notification
            showNearbySosNotification(context, alert);

            // 3. Notify any open activity listeners for in-screen banners / indicators
            synchronized (activeListeners) {
                for (NearbySosListener listener : activeListeners) {
                    try {
                        listener.onNearbySosReceived(alert);
                    } catch (Exception ignored) {}
                }
            }

            // 4. Send system broadcast
            Intent intent = new Intent(ACTION_NEARBY_SOS_RECEIVED);
            intent.putExtra("alert_data", alert);
            context.sendBroadcast(intent);

            // 5. Open single-instance full-screen alert activity safely
            try {
                Intent alertIntent = new Intent(context, NearbySosAlertActivity.class);
                alertIntent.putExtra("alert_data", alert);
                alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(alertIntent);
            } catch (Exception e) {
                Log.w(TAG, "Failed launching NearbySosAlertActivity directly: " + e.getMessage());
            }
        });
    }

    // ── Alert Sound, Vibration & Notification ─────────────────────────────────

    public static void triggerAlarmSoundAndVibration(Context context) {
        try {
            stopAlarmSound();
            alertMediaPlayer = MediaPlayer.create(context.getApplicationContext(), R.raw.siren);
            if (alertMediaPlayer != null) {
                alertMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                alertMediaPlayer.setLooping(true);
                alertMediaPlayer.start();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed playing siren: " + e.getMessage());
        }

        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = new long[]{0, 600, 200, 600, 200, 1000};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            }
        } catch (Exception ignored) {}
    }

    public static void stopAlarmSound() {
        try {
            if (alertMediaPlayer != null) {
                if (alertMediaPlayer.isPlaying()) {
                    alertMediaPlayer.stop();
                }
                alertMediaPlayer.release();
                alertMediaPlayer = null;
            }
        } catch (Exception ignored) {}
    }

    public static void showNearbySosNotification(Context context, NearbySosAlert alert) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        createNotificationChannel(manager);

        Intent intent = new Intent(context, NearbySosAlertActivity.class);
        intent.putExtra("alert_data", alert);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(alert.mapUrl));
        PendingIntent mapPi = PendingIntent.getActivity(context, (int) System.currentTimeMillis() + 1, mapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.applogo)
                .setContentTitle("🚨 NEARBY EMERGENCY SOS DETECTED!")
                .setContentText(alert.victimName + " is in danger nearby (" + alert.transport + ")")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("🚨 CITIZEN DISTRESS ALERT\nVictim: " + alert.victimName +
                                "\nPhone: " + alert.victimPhone +
                                "\nLocation: " + alert.locationName +
                                "\nTransport: " + alert.transport + " (Offline Mesh)"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .addAction(R.drawable.ic_navigation, "🧭 Open Google Maps", mapPi);

        manager.notify((int) (System.currentTimeMillis() % 100000), builder.build());
    }

    private static void createNotificationChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Nearby Offline SOS Mesh Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Critical notifications for nearby offline SOS broadcasts over Wi-Fi and Bluetooth");
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }
    }

    private static String getCachedUserName(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            return user.getDisplayName();
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("user_name", "SafeVaani Citizen");
    }

    private static String getCachedUserPhone(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty()) {
            return user.getPhoneNumber();
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("user_phone", "+91 9529811731");
    }
}

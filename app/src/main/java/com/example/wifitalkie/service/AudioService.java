package com.example.wifitalkie.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.wifitalkie.R;
import com.example.wifitalkie.WalkieTalkieActivity;
import com.example.wifitalkie.util.SessionManager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class AudioService extends Service {

    private static final String TAG = "AudioService";

    // Upgrade to 16 kHz Wideband HD Voice
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = Math.max(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT),
            2048);
    private static final int PORT = 50005;
    private static final int FILE_PORT = 50012;
    private static final String CHANNEL_ID = "WalkieTalkieChannel";

    // Packet Protocol Constants
    private static final byte MAGIC_HEADER = (byte) 0x57; // 'W'
    private static final byte PACKET_TYPE_PTT_CONTROL = 1;
    private static final byte PACKET_TYPE_AUDIO = 2;
    private static final byte PACKET_TYPE_CHAT_NOTIF = 3;

    public static final byte CONTROL_ACTION_START = 1;
    public static final byte CONTROL_ACTION_STOP = 2;

    // Broadcast Actions
    public static final String ACTION_FLOOR_CHANGED = "com.example.wifitalkie.ACTION_FLOOR_CHANGED";
    public static final String EXTRA_IS_BUSY = "is_busy";
    public static final String EXTRA_SPEAKER_ID = "speaker_id";
    public static final String EXTRA_SPEAKER_NAME = "speaker_name";

    public static final String ACTION_NEW_MESSAGE_ARRIVED = "com.example.wifitalkie.ACTION_NEW_MESSAGE_ARRIVED";
    public static final String EXTRA_SENDER_ID = "sender_id";
    public static final String EXTRA_RECEIVER_ID = "receiver_id";
    public static final String EXTRA_IS_GROUP = "is_group";

    public static final String ACTION_FILE_TRANSFER_PROGRESS = "com.example.wifitalkie.ACTION_FILE_TRANSFER_PROGRESS";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_FILE_NAME = "file_name";
    public static final String EXTRA_STATUS = "status";

    private boolean isRecording = false;
    private boolean isTransferring = false;
    private boolean isServiceRunning = false;
    private DatagramSocket udpSocket;
    private DatagramSocket senderSocket;
    private WifiManager.MulticastLock multicastLock;
    private PowerManager.WakeLock wakeLock;

    // Floor Control State
    private volatile int currentSpeakerId = -1;
    private volatile String currentSpeakerName = null;
    private volatile long lastSpeakerTimestamp = 0;
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private long lastReceivedSequence = -1;

    private SessionManager sessionManager;
    private com.example.wifitalkie.network.NetworkDiscoveryManager.DiscoveredPeer targetPeer = null;
    private final IBinder binder = new LocalBinder();
    private String lastPeerIp = null;
    private String p2pGroupOwnerAddress = null;
    private boolean amIGroupOwner = false;

    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService senderExecutor = Executors.newSingleThreadExecutor();

    public class LocalBinder extends Binder {
        public AudioService getService() {
            return AudioService.this;
        }
    }

    public boolean isTransferring() {
        return isTransferring;
    }

    public int getCurrentSpeakerId() {
        return currentSpeakerId;
    }

    public String getCurrentSpeakerName() {
        return currentSpeakerName;
    }

    public boolean isFloorBusy() {
        int myId = getMyUserId();
        return currentSpeakerId != -1 && currentSpeakerId != myId && (System.currentTimeMillis() - lastSpeakerTimestamp < 3500);
    }

    private int getMyUserId() {
        if (sessionManager == null) {
            sessionManager = new SessionManager(this);
        }
        return sessionManager.getUserDetails().getId();
    }

    private String getMyUserName() {
        if (sessionManager == null) {
            sessionManager = new SessionManager(this);
        }
        String name = sessionManager.getUserName();
        return (name != null && !name.isEmpty()) ? name : "User";
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sessionManager = new SessionManager(this);
        createNotificationChannel();
        acquireLocks();
        startFloorWatchdog();

        try {
            senderSocket = new DatagramSocket();
            senderSocket.setBroadcast(true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create sender socket", e);
        }
    }

    public void setTargetPeer(com.example.wifitalkie.network.NetworkDiscoveryManager.DiscoveredPeer peer) {
        this.targetPeer = peer;
    }

    private void acquireLocks() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WifiTalkie::WakeLock");
            wakeLock.acquire(10 * 60 * 1000L);
        }

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            multicastLock = wifiManager.createMulticastLock("WifiTalkie::MulticastLock");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();
        }
    }

    private void releaseLocks() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("ACTION_START_AUDIO".equals(action)) {
                p2pGroupOwnerAddress = intent.getStringExtra("GROUP_OWNER_ADDRESS");
                amIGroupOwner = intent.getBooleanExtra("IS_GROUP_OWNER", false);

                if (!isServiceRunning) {
                    startForegroundService();
                    startReceivingAudio();
                    startFileServer();
                }
            } else if ("ACTION_STOP_AUDIO".equals(action)) {
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private void startForegroundService() {
        isServiceRunning = true;
        Intent notificationIntent = new Intent(this, WalkieTalkieActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Walkie Talkie Active")
                .setContentText("Listening for incoming audio...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(1, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service", e);
            stopSelf();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Walkie Talkie Service",
                    NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void startFloorWatchdog() {
        watchdogHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (currentSpeakerId != -1 && currentSpeakerId != getMyUserId()) {
                    if (System.currentTimeMillis() - lastSpeakerTimestamp > 3500) {
                        Log.d(TAG, "Watchdog auto-releasing floor from speaker " + currentSpeakerId);
                        currentSpeakerId = -1;
                        currentSpeakerName = null;
                        notifyFloorChanged(false, -1, null);
                    }
                }
                watchdogHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void notifyFloorChanged(boolean isBusy, int speakerId, String speakerName) {
        Intent intent = new Intent(ACTION_FLOOR_CHANGED);
        intent.putExtra(EXTRA_IS_BUSY, isBusy);
        intent.putExtra(EXTRA_SPEAKER_ID, speakerId);
        intent.putExtra(EXTRA_SPEAKER_NAME, speakerName);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    /**
     * Attempts to start talking.
     * Returns true if floor is acquired, false if channel is busy.
     */
    public boolean startTalking() {
        if (isRecording) {
            return true;
        }

        if (isFloorBusy()) {
            Log.w(TAG, "Cannot talk: Floor is occupied by " + currentSpeakerName);
            return false;
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted.");
            return false;
        }

        int myId = getMyUserId();
        String myName = getMyUserName();

        currentSpeakerId = myId;
        currentSpeakerName = myName;
        lastSpeakerTimestamp = System.currentTimeMillis();
        sequenceCounter.set(0);

        // Broadcast Floor Start control packet
        sendPttControlPacket(CONTROL_ACTION_START, myId, myName);
        notifyFloorChanged(true, myId, myName);

        isRecording = true;
        new Thread(() -> {
            AudioRecord recorder = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    recorder = new AudioRecord.Builder()
                            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setEncoding(AUDIO_FORMAT)
                                    .setSampleRate(SAMPLE_RATE)
                                    .setChannelMask(CHANNEL_CONFIG_IN)
                                    .build())
                            .setBufferSizeInBytes(BUFFER_SIZE)
                            .build();
                } else {
                    recorder = new AudioRecord(
                            MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT,
                            BUFFER_SIZE);
                }

                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord could not be initialized.");
                    isRecording = false;
                    currentSpeakerId = -1;
                    sendPttControlPacket(CONTROL_ACTION_STOP, myId, myName);
                    notifyFloorChanged(false, -1, null);
                    return;
                }

                recorder.startRecording();
                byte[] buffer = new byte[BUFFER_SIZE];

                while (isRecording) {
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        sendAudioPacket(buffer, read, myId, sequenceCounter.getAndIncrement());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during audio recording", e);
            } finally {
                if (recorder != null) {
                    try {
                        recorder.stop();
                        recorder.release();
                    } catch (Exception ignored) {}
                }
                isRecording = false;
            }
        }).start();

        return true;
    }

    public void stopTalking() {
        if (!isRecording && currentSpeakerId != getMyUserId()) {
            return;
        }
        isRecording = false;

        int myId = getMyUserId();
        String myName = getMyUserName();

        currentSpeakerId = -1;
        currentSpeakerName = null;

        sendPttControlPacket(CONTROL_ACTION_STOP, myId, myName);
        notifyFloorChanged(false, -1, null);
    }

    private void sendPttControlPacket(byte action, int senderId, String senderName) {
        senderExecutor.execute(() -> {
            try {
                byte[] nameBytes = senderName.getBytes(StandardCharsets.UTF_8);
                int nameLen = Math.min(nameBytes.length, 64);

                ByteBuffer bb = ByteBuffer.allocate(1 + 1 + 1 + 4 + 1 + nameLen);
                bb.put(MAGIC_HEADER);
                bb.put(PACKET_TYPE_PTT_CONTROL);
                bb.put(action);
                bb.putInt(senderId);
                bb.put((byte) nameLen);
                bb.put(nameBytes, 0, nameLen);

                byte[] data = bb.array();
                dispatchPacket(data, data.length);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send PTT control packet", e);
            }
        });
    }

    private void sendAudioPacket(byte[] audioData, int length, int senderId, long seq) {
        senderExecutor.execute(() -> {
            try {
                ByteBuffer bb = ByteBuffer.allocate(1 + 1 + 4 + 8 + length);
                bb.put(MAGIC_HEADER);
                bb.put(PACKET_TYPE_AUDIO);
                bb.putInt(senderId);
                bb.putLong(seq);
                bb.put(audioData, 0, length);

                byte[] data = bb.array();
                dispatchPacket(data, data.length);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send audio packet", e);
            }
        });
    }

    public void broadcastChatNotification(int senderId, int receiverId, boolean isGroup) {
        senderExecutor.execute(() -> {
            try {
                ByteBuffer bb = ByteBuffer.allocate(1 + 1 + 4 + 4 + 1);
                bb.put(MAGIC_HEADER);
                bb.put(PACKET_TYPE_CHAT_NOTIF);
                bb.putInt(senderId);
                bb.putInt(receiverId);
                bb.put((byte) (isGroup ? 1 : 0));

                byte[] data = bb.array();
                dispatchPacket(data, data.length);
            } catch (Exception e) {
                Log.e(TAG, "Failed to broadcast chat notification", e);
            }
        });
    }

    private void dispatchPacket(byte[] data, int length) throws IOException {
        if (senderSocket == null || senderSocket.isClosed()) {
            senderSocket = new DatagramSocket();
            senderSocket.setBroadcast(true);
        }

        if (targetPeer != null && targetPeer.address != null) {
            DatagramPacket packet = new DatagramPacket(data, length, targetPeer.address, PORT);
            senderSocket.send(packet);
        } else {
            List<InetAddress> broadcastAddresses = getBroadcastAddresses();
            for (InetAddress broadcastAddr : broadcastAddresses) {
                DatagramPacket packet = new DatagramPacket(data, length, broadcastAddr, PORT);
                senderSocket.send(packet);
            }
        }
    }

    private List<InetAddress> getBroadcastAddresses() {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp())
                    continue;

                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast != null) {
                        addresses.add(broadcast);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting broadcast addresses", e);
        }
        if (addresses.isEmpty()) {
            try {
                addresses.add(InetAddress.getByName("255.255.255.255"));
            } catch (UnknownHostException ignored) {}
        }
        return addresses;
    }

    private void startReceivingAudio() {
        new Thread(() -> {
            try {
                if (udpSocket == null) {
                    udpSocket = new DatagramSocket(PORT);
                    udpSocket.setBroadcast(true);
                }

                byte[] buffer = new byte[BUFFER_SIZE + 64];
                AudioTrack track;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    track = new AudioTrack.Builder()
                            .setAudioAttributes(new android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build())
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setEncoding(AUDIO_FORMAT)
                                    .setSampleRate(SAMPLE_RATE)
                                    .setChannelMask(CHANNEL_CONFIG_OUT)
                                    .build())
                            .setBufferSizeInBytes(BUFFER_SIZE)
                            .build();
                } else {
                    track = new AudioTrack(AudioManager.STREAM_MUSIC,
                            SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT, BUFFER_SIZE,
                            AudioTrack.MODE_STREAM);
                }
                track.play();

                while (isServiceRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);

                    if (isOwnAddress(packet.getAddress())) {
                        continue;
                    }

                    int len = packet.getLength();
                    if (len <= 0) continue;

                    byte[] data = packet.getData();
                    lastPeerIp = packet.getAddress().getHostAddress();

                    if (data[0] == MAGIC_HEADER && len >= 2) {
                        byte type = data[1];

                        if (type == PACKET_TYPE_PTT_CONTROL && len >= 8) {
                            byte action = data[2];
                            ByteBuffer bb = ByteBuffer.wrap(data, 3, len - 3);
                            int senderId = bb.getInt();
                            int nameLen = bb.get() & 0xFF;
                            byte[] nameBytes = new byte[Math.min(nameLen, bb.remaining())];
                            bb.get(nameBytes);
                            String senderName = new String(nameBytes, StandardCharsets.UTF_8);

                            if (action == CONTROL_ACTION_START) {
                                currentSpeakerId = senderId;
                                currentSpeakerName = senderName;
                                lastSpeakerTimestamp = System.currentTimeMillis();
                                lastReceivedSequence = -1;
                                notifyFloorChanged(true, senderId, senderName);
                            } else if (action == CONTROL_ACTION_STOP) {
                                if (currentSpeakerId == senderId) {
                                    currentSpeakerId = -1;
                                    currentSpeakerName = null;
                                    notifyFloorChanged(false, -1, null);
                                }
                            }
                        } else if (type == PACKET_TYPE_AUDIO && len > 14) {
                            ByteBuffer bb = ByteBuffer.wrap(data, 2, 12);
                            int senderId = bb.getInt();
                            long seq = bb.getLong();

                            currentSpeakerId = senderId;
                            lastSpeakerTimestamp = System.currentTimeMillis();

                            // Packet sequence check: drop significantly out-of-order packets
                            if (seq >= lastReceivedSequence || (lastReceivedSequence - seq) < 5) {
                                lastReceivedSequence = Math.max(lastReceivedSequence, seq);
                                int audioLen = len - 14;
                                track.write(data, 14, audioLen);
                            }
                        } else if (type == PACKET_TYPE_CHAT_NOTIF && len >= 11) {
                            ByteBuffer bb = ByteBuffer.wrap(data, 2, 9);
                            int senderId = bb.getInt();
                            int receiverId = bb.getInt();
                            boolean isGroup = bb.get() == 1;

                            int myId = getMyUserId();
                            if (isGroup || receiverId == myId) {
                                Intent notifIntent = new Intent(ACTION_NEW_MESSAGE_ARRIVED);
                                notifIntent.putExtra(EXTRA_SENDER_ID, senderId);
                                notifIntent.putExtra(EXTRA_RECEIVER_ID, receiverId);
                                notifIntent.putExtra(EXTRA_IS_GROUP, isGroup);
                                notifIntent.setPackage(getPackageName());
                                sendBroadcast(notifIntent);
                            }
                        }
                    } else {
                        // Legacy unformatted audio packet fallback
                        track.write(data, 0, len);
                    }
                }
                track.release();
            } catch (Exception e) {
                if (isServiceRunning)
                    Log.e(TAG, "Error receiving audio", e);
            }
        }).start();
    }

    private void startFileServer() {
        new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(FILE_PORT);
                while (isServiceRunning) {
                    Socket socket = serverSocket.accept();
                    lastPeerIp = socket.getInetAddress().getHostAddress();
                    new Thread(() -> receiveFile(socket)).start();
                }
            } catch (Exception e) {
                if (isServiceRunning)
                    Log.e(TAG, "File server error", e);
            }
        }).start();
    }

    private void receiveFile(Socket socket) {
        isTransferring = true;
        try (InputStream inputStream = socket.getInputStream();
             DataInputStream dataInputStream = new DataInputStream(inputStream)) {

            String rawFileName = dataInputStream.readUTF();
            long fileSize = dataInputStream.readLong();

            // 1. Path Traversal Protection: Strip any directory path components & illegal chars
            String fileName = new File(rawFileName).getName();
            fileName = fileName.replaceAll("[/\\\\?%*:|\"<>]", "_").trim();
            if (fileName.isEmpty() || fileName.equals(".") || fileName.equals("..")) {
                fileName = "received_file_" + System.currentTimeMillis();
            }

            showFileNotification("Receiving: " + fileName);
            broadcastProgress(fileName, 0, "Receiving...");

            // 2. Android 10+ / 11+ / 13+ / 14 Scoped Storage Compatible Directory
            File downloadDir;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use app-specific external files dir to avoid Scoped Storage Permission Denied
                downloadDir = new File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "WifiTalkie");
            } else {
                downloadDir = new File(android.os.Environment
                        .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "WifiTalkie");
            }

            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
            if (!downloadDir.exists()) {
                downloadDir = new File(getFilesDir(), "WifiTalkie");
                downloadDir.mkdirs();
            }

            File file = new File(downloadDir, fileName);

            // Canonical Path Traversal check: ensure file cannot escape downloadDir
            String canonicalDest = downloadDir.getCanonicalPath();
            String canonicalFile = file.getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalDest + File.separator)) {
                throw new SecurityException("Path traversal attempt detected in filename: " + rawFileName);
            }

            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                byte[] buffer = new byte[16384];
                int bytesRead;
                long totalRead = 0;
                long lastProgressTime = 0;

                while (totalRead < fileSize && (bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    long now = System.currentTimeMillis();
                    if (now - lastProgressTime > 150) {
                        lastProgressTime = now;
                        int pct = (fileSize > 0) ? (int) ((totalRead * 100) / fileSize) : 0;
                        broadcastProgress(fileName, pct, "Receiving " + pct + "%");
                    }
                }
            }
            String location = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ? "App Downloads/WifiTalkie" : "Downloads/WifiTalkie";
            broadcastProgress(fileName, 100, "Received in " + location);
            showFileNotification("Received: " + fileName + " in " + location);
        } catch (Exception e) {
            Log.e(TAG, "File receive error", e);
            broadcastProgress("File", 0, "Error receiving file: " + e.getMessage());
        } finally {
            isTransferring = false;
        }
    }

    public void sendFile(Uri fileUri, String fileName) {
        new Thread(() -> {
            isTransferring = true;
            broadcastProgress(fileName, 0, "Connecting...");
            try {
                String targetIp = null;

                if (targetPeer != null && targetPeer.address != null) {
                    targetIp = targetPeer.address.getHostAddress();
                } else if (p2pGroupOwnerAddress != null || amIGroupOwner) {
                    if (amIGroupOwner) {
                        targetIp = lastPeerIp;
                    } else {
                        targetIp = (p2pGroupOwnerAddress != null) ? p2pGroupOwnerAddress : "192.168.49.1";
                    }
                } else if (lastPeerIp != null) {
                    targetIp = lastPeerIp;
                }

                if (targetIp == null) {
                    Log.e(TAG, "Receiver IP is unknown.");
                    broadcastProgress(fileName, 0, "Error: Target peer IP not found.");
                    showFileNotification("Error: Receiver IP not found.");
                    return;
                }

                Log.d(TAG, "Connecting for file transfer to: " + targetIp + " on port " + FILE_PORT);

                try (Socket socket = new Socket()) {
                    socket.connect(new java.net.InetSocketAddress(targetIp, FILE_PORT), 5000);

                    try (OutputStream outputStream = socket.getOutputStream();
                         DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
                         InputStream fileInputStream = getContentResolver().openInputStream(fileUri)) {

                        dataOutputStream.writeUTF(fileName);
                        long fileSize = getFileSize(fileUri);
                        dataOutputStream.writeLong(fileSize);

                        byte[] buffer = new byte[16384];
                        int bytesRead;
                        long totalRead = 0;
                        long lastProgressTime = 0;

                        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;

                            long now = System.currentTimeMillis();
                            if (now - lastProgressTime > 150) {
                                lastProgressTime = now;
                                int pct = (fileSize > 0) ? (int) ((totalRead * 100) / fileSize) : 0;
                                broadcastProgress(fileName, pct, "Sending " + pct + "%");
                            }
                        }
                        outputStream.flush();
                        broadcastProgress(fileName, 100, "File Sent Successfully!");
                        showFileNotification("File Sent: " + fileName);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "File send failed", e);
                    broadcastProgress(fileName, 0, "Send Failed: " + e.getMessage());
                    showFileNotification("Send Failed: " + e.getMessage());
                }
            } finally {
                isTransferring = false;
            }
        }).start();
    }

    private void broadcastProgress(String fileName, int progress, String status) {
        Intent intent = new Intent(ACTION_FILE_TRANSFER_PROGRESS);
        intent.putExtra(EXTRA_FILE_NAME, fileName);
        intent.putExtra(EXTRA_PROGRESS, progress);
        intent.putExtra(EXTRA_STATUS, status);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private long getFileSize(Uri fileUri) {
        try (android.database.Cursor cursor = getContentResolver().query(fileUri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private void showFileNotification(String content) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("File Transfer")
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), notification);
        }
    }

    private boolean isOwnAddress(InetAddress addr) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress ownAddr = addresses.nextElement();
                    if (ownAddr.equals(addr))
                        return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceRunning = false;
        isRecording = false;
        watchdogHandler.removeCallbacksAndMessages(null);
        releaseLocks();
        if (udpSocket != null) {
            udpSocket.close();
        }
        if (senderSocket != null) {
            senderSocket.close();
        }
        senderExecutor.shutdown();
    }
}

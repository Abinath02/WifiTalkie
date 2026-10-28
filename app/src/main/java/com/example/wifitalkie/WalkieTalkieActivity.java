package com.example.wifitalkie;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wifitalkie.receiver.WifiDirectHandler;
import com.example.wifitalkie.service.AudioService;
import com.example.wifitalkie.util.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class WalkieTalkieActivity extends AppCompatActivity implements WifiDirectHandler {

    private com.example.wifitalkie.network.NetworkDiscoveryManager discoveryManager;
    private List<com.example.wifitalkie.network.NetworkDiscoveryManager.DiscoveredPeer> discoveredPeers = new ArrayList<>();
    private List<com.example.wifitalkie.network.NetworkDiscoveryManager.DiscoveredPeer> activePeersList = new ArrayList<>();

    private TextView tvConnectionStatus, tvLoadingText;
    private View loadingLayout;
    private ImageButton btnPtt;
    private Button btnStop;
    private RecyclerView rvUsers;

    private AudioService audioService;
    private boolean isBound = false;
    private SessionManager sessionManager;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            AudioService.LocalBinder binder = (AudioService.LocalBinder) service;
            audioService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    private final BroadcastReceiver floorReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioService.ACTION_FLOOR_CHANGED.equals(intent.getAction())) {
                boolean isBusy = intent.getBooleanExtra(AudioService.EXTRA_IS_BUSY, false);
                String speakerName = intent.getStringExtra(AudioService.EXTRA_SPEAKER_NAME);
                int speakerId = intent.getIntExtra(AudioService.EXTRA_SPEAKER_ID, -1);

                int myId = (sessionManager != null) ? sessionManager.getUserDetails().getId() : -1;
                if (isBusy && speakerId != myId) {
                    tvConnectionStatus.setText("🎙️ " + (speakerName != null ? speakerName : "Peer") + " is speaking...");
                    btnPtt.setEnabled(false);
                    btnPtt.setAlpha(0.4f);
                } else if (!isBusy) {
                    btnPtt.setEnabled(true);
                    btnPtt.setAlpha(1.0f);
                    updateConnectionStatusText();
                }
            }
        }
    };

    private void updateConnectionStatusText() {
        if (tvConnectionStatus == null) return;
        if (discoveredPeers == null || discoveredPeers.isEmpty()) {
            tvConnectionStatus.setText("Status: Scanning for peers...");
        } else {
            tvConnectionStatus.setText("Status: " + discoveredPeers.size() + " Peers Found (Ready)");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walkie_talkie);

        sessionManager = new SessionManager(this);

        // Add self to list initially (optional, but good for confirmation)
        activePeersList.add(new com.example.wifitalkie.network.NetworkDiscoveryManager.DiscoveredPeer(-1,
                sessionManager.getUserName() + " (You)", null));

        initUI();

        // Always enable Network Discovery so peers can see each other's names/IDs
        // regardless of whether they connected via Wi-Fi Direct or regular LAN.
        initNetworkDiscovery();

        if (getIntent().getBooleanExtra("IS_P2P", false)) {
            tvConnectionStatus.setText("Status: Private P2P Connection");
            loadingLayout.setVisibility(View.GONE);
        }

        // Start Audio Service immediately for listening
        // Check for permission before starting service
        checkAudioPermission();

        Intent intent = new Intent(this, AudioService.class);
        // Bind service
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        // IntentFilter filter = new IntentFilter("com.example.wifitalkie.USER_JOINED");
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // registerReceiver(userJoinReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        // } else {
        // registerReceiver(userJoinReceiver, filter);
        // }

        if ("FILE_TRANSFER".equals(getIntent().getStringExtra("ACTION"))) {
            // Delay slightly to ensure UI is ready or just launch
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (filePickerLauncher != null) {
                    filePickerLauncher.launch("*/*");
                }
            }, 500);
        }
    }

    private androidx.activity.result.ActivityResultLauncher<String> filePickerLauncher;

    private void initUI() {
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvLoadingText = findViewById(R.id.tvLoadingText);
        loadingLayout = findViewById(R.id.loadingLayout);
        btnPtt = findViewById(R.id.btnPtt);
        btnStop = findViewById(R.id.btnStop);
        rvUsers = findViewById(R.id.rvUsers);
        ImageButton btnSendFile = findViewById(R.id.btnSendFile);

        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        ActiveUserAdapter adapter = new ActiveUserAdapter(activePeersList, peer -> {

            if (peer.id != -1) {

                String[] items = { "Private Talk", "Chat", "Send File", "Group Talk" };
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(peer.name)
                        .setItems(items, (dialog, which) -> {
                            if (which == 0) { // Private Talk
                                if (isBound && audioService != null) {
                                    audioService.setTargetPeer(peer);
                                    Toast.makeText(WalkieTalkieActivity.this, "PTT is now private to " + peer.name,
                                            Toast.LENGTH_SHORT).show();
                                    tvConnectionStatus.setText("Status: Private talk with " + peer.name);
                                }
                            } else if (which == 1) { // Chat
                                Intent chatIntent = new Intent(WalkieTalkieActivity.this, ChatActivity.class);
                                chatIntent.putExtra("receiver_id", peer.id);
                                chatIntent.putExtra("receiver_name", peer.name);
                                chatIntent.putExtra("is_group", false);
                                startActivity(chatIntent);
                            } else if (which == 2) { // Send File
                                if (isBound && audioService != null) {
                                    audioService.setTargetPeer(peer); // Ensure target is set for file
                                    filePickerLauncher.launch("*/*");
                                }
                            } else { // Group Talk
                                if (isBound && audioService != null) {
                                    audioService.setTargetPeer(null);
                                    Toast.makeText(WalkieTalkieActivity.this, "PTT is now public (Group Talk)",
                                            Toast.LENGTH_SHORT).show();
                                    tvConnectionStatus.setText("Status: " + discoveredPeers.size() + " Peers Found");
                                }
                            }
                        })
                        .show();
            }
        });
        rvUsers.setAdapter(adapter);

        filePickerLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && isBound && audioService != null) {
                        // Get file name
                        String fileName = "unknown_file";
                        android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                            if (nameIndex != -1) {
                                fileName = cursor.getString(nameIndex);
                            }
                            cursor.close();
                        }
                        audioService.sendFile(uri, fileName);
                        Toast.makeText(this, "Sending " + fileName + "...", Toast.LENGTH_SHORT).show();
                    }
                });

        btnSendFile.setOnClickListener(v -> filePickerLauncher.launch("*/*"));

        btnPtt.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (isBound && audioService != null) {
                        boolean started = audioService.startTalking();
                        if (started) {
                            v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).start();
                            tvConnectionStatus.setText("Status: Transmitting (You are speaking)...");
                        } else {
                            String speaker = audioService.getCurrentSpeakerName();
                            Toast.makeText(WalkieTalkieActivity.this, "Channel Busy! " + (speaker != null ? speaker : "Someone") + " is speaking", Toast.LENGTH_SHORT).show();
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (isBound && audioService != null) {
                        audioService.stopTalking();
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                        updateConnectionStatusText();
                    }
                    v.performClick();
                    return true;
            }
            return false;
        });

        btnStop.setOnClickListener(v -> {
            stopService();
            finish();
        });
    }

    private void stopService() {
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        Intent intent = new Intent(this, AudioService.class);
        intent.setAction("ACTION_STOP_AUDIO");
        startService(intent);
    }

    private void initNetworkDiscovery() {
        discoveryManager = new com.example.wifitalkie.network.NetworkDiscoveryManager(
                this,
                sessionManager.getUserDetails().getId(),
                sessionManager.getUserName(),
                peers -> {
                    discoveredPeers = peers;
                    activePeersList.clear();
                    // Add self
                    activePeersList.add(new com.example.wifitalkie.network.NetworkDiscoveryManager.DiscoveredPeer(-1,
                            sessionManager.getUserName() + " (You)", null));
                    // Add peers
                    activePeersList.addAll(peers);

                    if (rvUsers.getAdapter() != null) {
                        ((ActiveUserAdapter) rvUsers.getAdapter()).updateList(activePeersList);
                    }

                    if (peers.isEmpty()) {
                        tvConnectionStatus.setText("Status: Scanning...");
                    } else {
                        tvConnectionStatus.setText("Status: " + peers.size() + " Peers Found");
                    }

                    loadingLayout.setVisibility(View.GONE);
                });
        discoveryManager.startDiscovery();
        tvConnectionStatus.setText("Status: Discovery Started");
    }

    // P2P Methods removed for LAN implementation

    public WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            // Unused in LAN mode
        }
    };

    public WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            // Unused in LAN mode
        }
    };

    private void checkAudioPermission() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.RECORD_AUDIO },
                    100);
        } else {
            startAudioService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioService();
            } else {
                Toast.makeText(this, "Microphone permission is required for Walkie Talkie", Toast.LENGTH_LONG).show();
                finish(); // Close activity if permission denied
            }
        }
    }

    private void startAudioService() {
        Intent serviceIntent = new Intent(this, AudioService.class);
        serviceIntent.setAction("ACTION_START_AUDIO");

        // Forward P2P intent extras
        if (getIntent().getBooleanExtra("IS_P2P", false)) {
            serviceIntent.putExtra("GROUP_OWNER_ADDRESS", getIntent().getStringExtra("groupOwnerAddress"));
            serviceIntent.putExtra("IS_GROUP_OWNER", getIntent().getBooleanExtra("isGroupOwner", false));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (discoveryManager != null) {
            discoveryManager.startDiscovery();
        }
        androidx.core.content.ContextCompat.registerReceiver(
                this,
                floorReceiver,
                new IntentFilter(AudioService.ACTION_FLOOR_CHANGED),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(floorReceiver);
        } catch (Exception ignored) {}
        if (discoveryManager != null) {
            discoveryManager.stopDiscovery();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        if (discoveryManager != null) {
            discoveryManager.stopDiscovery();
        }
        // unregisterReceiver(userJoinReceiver);
    }

    // Required for the BroadcastReceiver to call back
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        if (!isWifiP2pEnabled) {
            Toast.makeText(this, "Please enable Wi-Fi", Toast.LENGTH_SHORT).show();
        }
    }

    public void resetData() {
        // Reset data
    }

    @Override
    public WifiP2pManager.PeerListListener getPeerListListener() {
        return peerListListener;
    }

    @Override
    public WifiP2pManager.ConnectionInfoListener getConnectionInfoListener() {
        return connectionInfoListener;
    }
}

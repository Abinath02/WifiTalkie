package com.example.wifitalkie;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wifitalkie.network.NetworkDiscoveryManager;
import com.example.wifitalkie.service.AudioService;
import com.example.wifitalkie.util.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class FileTransferActivity extends AppCompatActivity {

    private TextView tvStatus;
    private ProgressBar progressBar;
    private View cardSend, cardReceive;
    private TextView tvAvailableDevices;
    private RecyclerView rvDevices;
    private ActivityResultLauncher<String> filePickerLauncher;

    private AudioService audioService;
    private boolean isBound = false;
    private SessionManager sessionManager;
    private NetworkDiscoveryManager discoveryManager;
    private List<NetworkDiscoveryManager.DiscoveredPeer> peerList = new ArrayList<>();
    private ActiveUserAdapter peerAdapter;
    private NetworkDiscoveryManager.DiscoveredPeer selectedPeer = null;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioService.LocalBinder binder = (AudioService.LocalBinder) service;
            audioService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioService.ACTION_FILE_TRANSFER_PROGRESS.equals(intent.getAction())) {
                String fileName = intent.getStringExtra(AudioService.EXTRA_FILE_NAME);
                int progress = intent.getIntExtra(AudioService.EXTRA_PROGRESS, 0);
                String status = intent.getStringExtra(AudioService.EXTRA_STATUS);

                tvStatus.setText(status + " (" + fileName + ")");
                if (progress > 0 && progress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setIndeterminate(false);
                    progressBar.setMax(100);
                    progressBar.setProgress(progress);
                } else if (progress >= 100) {
                    progressBar.setProgress(100);
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_transfer);

        sessionManager = new SessionManager(this);

        // Ensure AudioService is running for listening on Port 50012
        Intent serviceIntent = new Intent(this, AudioService.class);
        serviceIntent.setAction("ACTION_START_AUDIO");
        ContextCompat.startForegroundService(this, serviceIntent);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        initUI();
        initFilePicker();
        initDiscovery();
    }

    private void initUI() {
        tvStatus = findViewById(R.id.tvStatus);
        progressBar = findViewById(R.id.progressBar);
        cardSend = findViewById(R.id.cardSend);
        cardReceive = findViewById(R.id.cardReceive);
        tvAvailableDevices = findViewById(R.id.tvAvailableDevices);
        rvDevices = findViewById(R.id.rvDevices);

        rvDevices.setLayoutManager(new LinearLayoutManager(this));
        peerAdapter = new ActiveUserAdapter(peerList, peer -> {
            selectedPeer = peer;
            if (isBound && audioService != null) {
                audioService.setTargetPeer(peer);
            }
            tvStatus.setText("Selected: " + peer.name + "\nOpening file picker...");
            filePickerLauncher.launch("*/*");
        });
        rvDevices.setAdapter(peerAdapter);

        cardSend.setOnClickListener(v -> {
            tvAvailableDevices.setVisibility(View.VISIBLE);
            rvDevices.setVisibility(View.VISIBLE);
            if (peerList.isEmpty()) {
                tvStatus.setText("Scanning for devices on Wi-Fi...\nMake sure other devices have the app open.");
            } else {
                tvStatus.setText("Select a peer to send files over Wi-Fi:");
            }
        });

        cardReceive.setOnClickListener(v -> {
            tvAvailableDevices.setVisibility(View.GONE);
            rvDevices.setVisibility(View.GONE);
            tvStatus.setText("📥 Receiver Mode Ready\nListening for incoming files on Wi-Fi...\nReceived files will be saved in Downloads/WifiTalkie");
        });
    }

    private void initDiscovery() {
        discoveryManager = new NetworkDiscoveryManager(
                this,
                sessionManager.getUserDetails().getId(),
                sessionManager.getUserName(),
                peers -> {
                    peerList.clear();
                    peerList.addAll(peers);
                    peerAdapter.updateList(peerList);
                    if (tvAvailableDevices.getVisibility() == View.VISIBLE) {
                        if (peers.isEmpty()) {
                            tvStatus.setText("Scanning for devices on Wi-Fi...");
                        } else {
                            tvStatus.setText("Select a peer to send files over Wi-Fi (" + peers.size() + " found):");
                        }
                    }
                });
        discoveryManager.startDiscovery();
    }

    private void initFilePicker() {
        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                String fileName = getFileName(uri);
                if (selectedPeer != null) {
                    tvStatus.setText("Sending " + fileName + " to " + selectedPeer.name + "...");
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setIndeterminate(true);
                    if (isBound && audioService != null) {
                        audioService.setTargetPeer(selectedPeer);
                        audioService.sendFile(uri, fileName);
                    } else {
                        Toast.makeText(this, "Transfer service not ready, please retry.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Please select a destination device first", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1)
                        result = cursor.getString(index);
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1)
                result = result.substring(cut + 1);
        }
        return (result != null) ? result : "file_" + System.currentTimeMillis();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(
                this,
                progressReceiver,
                new IntentFilter(AudioService.ACTION_FILE_TRANSFER_PROGRESS),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        if (discoveryManager != null) {
            discoveryManager.startDiscovery();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(progressReceiver);
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
    }
}
package com.example.wifitalkie;

import android.Manifest;
import android.os.Build;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wifitalkie.receiver.WifiDirectBroadcastReceiver;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import com.example.wifitalkie.receiver.WifiDirectHandler;

public class WiFiDirectActivity extends AppCompatActivity implements WifiDirectHandler {

    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private BroadcastReceiver mReceiver;
    private IntentFilter mIntentFilter;

    private Button btnDiscover;
    private RecyclerView recyclerView;
    private WiFiDirectAdapter adapter;
    private TextView connectionStatus;
    private View emptyState;
    private boolean isWifiP2pEnabled;

    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_direct);

        btnDiscover = findViewById(R.id.btnDiscover);
        recyclerView = findViewById(R.id.peerRecyclerView);
        connectionStatus = findViewById(R.id.connectionStatus);
        emptyState = findViewById(R.id.emptyState);

        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        adapter = new WiFiDirectAdapter(peers, device -> {
            connectToDevice(device);
        });
        recyclerView.setAdapter(adapter);

        initialWork();
        exqListener();
    }

    private void initialWork() {
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mReceiver = new WifiDirectBroadcastReceiver(mManager, mChannel, this);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    private void exqListener() {
        btnDiscover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDiscovery();
            }
        });
    }

    private void startDiscovery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(WiFiDirectActivity.this,
                    Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(WiFiDirectActivity.this,
                        new String[] { Manifest.permission.NEARBY_WIFI_DEVICES }, 1);
                return;
            }
        } else {
            if (ActivityCompat.checkSelfPermission(WiFiDirectActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(WiFiDirectActivity.this,
                        new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 1);
                return;
            }
        }

        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                connectionStatus.setText("Status: Discovery Started");
                Toast.makeText(WiFiDirectActivity.this, "Discovery Started", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int i) {
                connectionStatus.setText("Status: Discovery Failed (" + i + ")");
            }
        });
    }

    private void connectToDevice(WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(WiFiDirectActivity.this,
                    Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(WiFiDirectActivity.this,
                        new String[] { Manifest.permission.NEARBY_WIFI_DEVICES }, 1);
                return;
            }
        } else {
            if (ActivityCompat.checkSelfPermission(WiFiDirectActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(WiFiDirectActivity.this,
                        new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 1);
                return;
            }
        }

        connectionStatus.setText("Status: Connecting to " + device.deviceName);
        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(getApplicationContext(), "Connection request sent to " + device.deviceName,
                        Toast.LENGTH_SHORT)
                        .show();
            }

            @Override
            public void onFailure(int i) {
                connectionStatus.setText("Status: Connection Failed");
                Toast.makeText(getApplicationContext(), "Connection Failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions,
            @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDiscovery();
            } else {
                Toast.makeText(this, "Permission required for discovery", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            if (!peerList.getDeviceList().equals(peers)) {
                peers.clear();
                peers.addAll(peerList.getDeviceList());

                adapter.updateList(peers);

                if (peers.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                } else {
                    emptyState.setVisibility(View.GONE);
                }
            }

            if (peers.size() == 0) {
                connectionStatus.setText("Status: No Devices Found");
            } else {
                connectionStatus.setText("Status: " + peers.size() + " Devices Found");
            }
        }
    };

    public WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            final InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;

            if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                connectionStatus.setText("Status: Host");
            } else if (wifiP2pInfo.groupFormed) {
                connectionStatus.setText("Status: Client");
            }

            if (wifiP2pInfo.groupFormed) {
                // Navigate to WalkieTalkieActivity for PTT
                Intent intent = new Intent(WiFiDirectActivity.this, WalkieTalkieActivity.class);
                intent.putExtra("IS_P2P", true);
                intent.putExtra("isGroupOwner", wifiP2pInfo.isGroupOwner);
                if (groupOwnerAddress != null) {
                    intent.putExtra("groupOwnerAddress", groupOwnerAddress.getHostAddress());
                }
                startActivity(intent);
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mReceiver, mIntentFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(mReceiver, mIntentFilter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
        if (mManager != null && mChannel != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED) {
                    mManager.stopPeerDiscovery(mChannel, null);
                }
            } else {
                if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mManager.stopPeerDiscovery(mChannel, null);
                }
            }
        }
    }

    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
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

package com.example.wifitalkie.receiver;

import android.net.wifi.p2p.WifiP2pManager;

public interface WifiDirectHandler {
    void setIsWifiP2pEnabled(boolean enabled);

    void resetData();

    WifiP2pManager.PeerListListener getPeerListListener();

    WifiP2pManager.ConnectionInfoListener getConnectionInfoListener();
}

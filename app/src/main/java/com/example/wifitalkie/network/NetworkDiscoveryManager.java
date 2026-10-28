package com.example.wifitalkie.network;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NetworkDiscoveryManager {

    private static final String TAG = "NetworkDiscoveryManager";
    private static final String SERVICE_TYPE = "_wifitalkie._tcp.";
    private final String myName;
    private final int myId;
    private final DiscoveryListener listener;
    private final Context context;

    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private NsdManager.DiscoveryListener discoveryListener;
    private String serviceName;

    private final List<DiscoveredPeer> discoveredPeers = new CopyOnWriteArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface DiscoveryListener {
        void onPeersChanged(List<DiscoveredPeer> peers);
    }

    public static class DiscoveredPeer {
        public int id;
        public String name;
        public InetAddress address;
        public long lastSeen;

        public DiscoveredPeer(int id, String name, InetAddress address) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.lastSeen = System.currentTimeMillis();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            DiscoveredPeer that = (DiscoveredPeer) o;
            return address != null && address.equals(that.address);
        }

        @Override
        public int hashCode() {
            return address != null ? address.hashCode() : 0;
        }
    }

    public NetworkDiscoveryManager(Context context, int myId, String myName, DiscoveryListener listener) {
        this.context = context.getApplicationContext();
        this.myId = myId;
        this.myName = myName;
        this.listener = listener;
        this.nsdManager = (NsdManager) this.context.getSystemService(Context.NSD_SERVICE);
    }

    public void startAdvertising() {
        if (registrationListener != null)
            return;
        registerService();
    }

    public void startDiscovery() {
        if (discoveryListener != null)
            return;
        discoverServices();
        startCleanupTask();
    }

    public void stopDiscovery() {
        stopCleanupTask();
        try {
            if (nsdManager != null) {
                if (registrationListener != null) {
                    nsdManager.unregisterService(registrationListener);
                    registrationListener = null;
                }
                if (discoveryListener != null) {
                    nsdManager.stopServiceDiscovery(discoveryListener);
                    discoveryListener = null;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping discovery", e);
        }
    }

    private void registerService() {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("WifiTalkie_" + myId + "_" + myName.replace(" ", "_"));
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(50005); // Using the audio port

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo info) {
                serviceName = info.getServiceName();
                Log.d(TAG, "Service registered: " + serviceName);
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Registration failed: " + errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            }
        };

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }

    private void discoverServices() {
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: " + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Stop discovery failed: " + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Discovery stopped");
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (!serviceInfo.getServiceType().equals(SERVICE_TYPE)) {
                    Log.d(TAG, "Unknown Service Type: " + serviceInfo.getServiceType());
                } else if (serviceInfo.getServiceName().equals(serviceName)) {
                    Log.d(TAG, "Same machine: " + serviceName);
                } else if (serviceInfo.getServiceName().contains("WifiTalkie_")) {
                    nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                            Log.e(TAG, "Resolve failed: " + errorCode + ". User should check Wi-Fi connection.");
                            // Retry once after a short delay
                            handler.postDelayed(() -> {
                                try {
                                    nsdManager.resolveService(serviceInfo, this);
                                } catch (Exception ignored) {
                                }
                            }, 2000);
                        }

                        @Override
                        public void onServiceResolved(NsdServiceInfo resolvedInfo) {
                            Log.d(TAG, "Resolve Succeeded. " + resolvedInfo.getHost());
                            handleServiceResolved(resolvedInfo);
                        }
                    });
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.e(TAG, "service lost " + serviceInfo);
                removePeerByServiceName(serviceInfo.getServiceName());
            }
        };

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    private synchronized void handleServiceResolved(NsdServiceInfo info) {
        try {
            String sName = info.getServiceName(); // WifiTalkie_ID_Name
            String[] parts = sName.split("_");
            if (parts.length >= 3) {
                int peerId = Integer.parseInt(parts[1]);
                String peerName = parts[parts.length - 1].replace("_", " ");
                InetAddress address = info.getHost();

                DiscoveredPeer newPeer = new DiscoveredPeer(peerId, peerName, address);
                boolean isUpdate = false;
                for (DiscoveredPeer p : discoveredPeers) {
                    if (p.address.equals(address)) {
                        p.id = peerId;
                        p.name = peerName;
                        p.lastSeen = System.currentTimeMillis();
                        isUpdate = true;
                        break;
                    }
                }

                if (!isUpdate) {
                    discoveredPeers.add(newPeer);
                }

                notifyPeersChanged();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling resolved service", e);
        }
    }

    private void removePeerByServiceName(String sName) {
        // Services named: WifiTalkie_ID_Name
        String[] parts = sName.split("_");
        if (parts.length >= 3) {
            String peerName = parts[parts.length - 1].replace("_", " ");
            discoveredPeers.removeIf(p -> p.name.equals(peerName));
            notifyPeersChanged();
        }
    }

    private void notifyPeersChanged() {
        handler.post(() -> {
            if (listener != null) {
                listener.onPeersChanged(new ArrayList<>(discoveredPeers));
            }
        });
    }

    private void startCleanupTask() {
        handler.postDelayed(cleanupRunnable, 10000);
    }

    private void stopCleanupTask() {
        handler.removeCallbacks(cleanupRunnable);
    }

    private final Runnable cleanupRunnable = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            boolean changed = false;
            List<DiscoveredPeer> toRemove = new ArrayList<>();

            for (DiscoveredPeer peer : discoveredPeers) {
                // Increased timeout to 2 minutes so they don't disappear quickly
                if (now - peer.lastSeen > 120000) {
                    toRemove.add(peer);
                    changed = true;
                }
            }

            if (changed) {
                discoveredPeers.removeAll(toRemove);
                notifyPeersChanged();
            }
            handler.postDelayed(this, 30000);
        }
    };
}

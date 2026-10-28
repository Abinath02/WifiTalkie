package com.example.wifitalkie.util;

import android.util.Log;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class NetworkUtils {

    private static final String TAG = "NetworkUtils";

    /**
     * Retrieves all valid directed subnet broadcast addresses for active network interfaces.
     * Prevents packet dropping caused by hardcoded 255.255.255.255 on Android and Wi-Fi routers.
     */
    public static List<InetAddress> getBroadcastAddresses() {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    if (networkInterface.isLoopback() || !networkInterface.isUp())
                        continue;

                    for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                        InetAddress broadcast = interfaceAddress.getBroadcast();
                        if (broadcast != null && !addresses.contains(broadcast)) {
                            addresses.add(broadcast);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating broadcast addresses", e);
        }

        if (addresses.isEmpty()) {
            try {
                addresses.add(InetAddress.getByName("255.255.255.255"));
            } catch (UnknownHostException ignored) {}
        }
        return addresses;
    }
}

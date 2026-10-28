package com.example.wifitalkie;

import android.net.wifi.p2p.WifiP2pDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class WiFiDirectAdapter extends RecyclerView.Adapter<WiFiDirectAdapter.DeviceViewHolder> {

    private List<WifiP2pDevice> devices;
    private OnDeviceClickListener listener;

    public interface OnDeviceClickListener {
        void onDeviceClick(WifiP2pDevice device);
    }

    public WiFiDirectAdapter(List<WifiP2pDevice> devices, OnDeviceClickListener listener) {
        this.devices = devices;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        WifiP2pDevice device = devices.get(position);
        holder.tvDeviceName.setText(device.deviceName);
        holder.tvStatus.setText(getDeviceStatus(device.status));

        // Custom styling for status
        if (device.status == WifiP2pDevice.CONNECTED) {
            holder.tvStatus.setTextColor(0xFF00FF00); // Green
        } else if (device.status == WifiP2pDevice.AVAILABLE) {
            holder.tvStatus.setTextColor(0xFF00E5FF); // Cyan
        } else {
            holder.tvStatus.setTextColor(0xFFAAAAAA); // Gray
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeviceClick(device);
            }
        });
    }

    private String getDeviceStatus(int status) {
        switch (status) {
            case WifiP2pDevice.AVAILABLE:
                return "Available";
            case WifiP2pDevice.INVITED:
                return "Invited";
            case WifiP2pDevice.CONNECTED:
                return "Connected";
            case WifiP2pDevice.FAILED:
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
                return "Unavailable";
            default:
                return "Unknown";
        }
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public void updateList(List<WifiP2pDevice> newDevices) {
        this.devices = newDevices;
        notifyDataSetChanged();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView tvDeviceName, tvStatus;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDeviceName = itemView.findViewById(R.id.tvUserName);
            tvStatus = itemView.findViewById(R.id.tvUserStatus);
        }
    }
}

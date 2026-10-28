package com.example.wifitalkie;

import android.net.wifi.p2p.WifiP2pDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private List<WifiP2pDevice> devices;

    public UserAdapter(List<WifiP2pDevice> devices) {
        this.devices = devices;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        WifiP2pDevice device = devices.get(position);
        holder.tvUserName.setText(device.deviceName);

        // Status logic can be improved (e.g., if connected)
        if (device.status == WifiP2pDevice.CONNECTED) {
            holder.tvUserStatus.setText("Connected");
            holder.tvUserStatus.setTextColor(0xFF00E5FF); // Cyan
        } else if (device.status == WifiP2pDevice.AVAILABLE) {
            holder.tvUserStatus.setText("Available");
            holder.tvUserStatus.setTextColor(0xFF00FF00); // Green
        } else {
            holder.tvUserStatus.setText("Unavailable");
            holder.tvUserStatus.setTextColor(0xFFAAAAAA); // Gray
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

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName, tvUserStatus;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvUserStatus = itemView.findViewById(R.id.tvUserStatus);
        }
    }
}

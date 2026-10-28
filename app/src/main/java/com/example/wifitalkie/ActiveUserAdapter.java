package com.example.wifitalkie;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wifitalkie.network.NetworkDiscoveryManager.DiscoveredPeer;

import java.util.List;

public class ActiveUserAdapter extends RecyclerView.Adapter<ActiveUserAdapter.UserViewHolder> {

    private List<DiscoveredPeer> peers;
    private OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(DiscoveredPeer peer);
    }

    public ActiveUserAdapter(List<DiscoveredPeer> peers, OnUserClickListener listener) {
        this.peers = peers;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        DiscoveredPeer peer = peers.get(position);
        holder.tvUserName.setText(peer.name);
        holder.tvUserStatus.setText(peer.id == -1 ? "" : "Online"); // -1 used for "You" initially or special cases
        holder.tvUserStatus.setTextColor(0xFF00FF00); // Green

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUserClick(peer);
            }
        });
    }

    @Override
    public int getItemCount() {
        return peers.size();
    }

    public void updateList(List<DiscoveredPeer> newPeers) {
        this.peers = newPeers;
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

package com.example.wifitalkie.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wifitalkie.ChatActivity;
import com.example.wifitalkie.R;
import com.example.wifitalkie.model.User;

import java.util.List;

public class UserListAdapter extends RecyclerView.Adapter<UserListAdapter.ViewHolder> {

    private Context context;
    private List<User> userList;

    public UserListAdapter(Context context, List<User> userList) {
        this.context = context;
        this.userList = userList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = userList.get(position);
        holder.tvUserName.setText(user.getFirstName() + " " + user.getLastName());
        holder.tvUserStatus.setText(user.getRole());

        if (user.getRole().equals("GROUP")) {
            holder.tvUserStatus.setTextColor(0xFF00E5FF); // Cyan for group
        } else {
            holder.tvUserStatus.setTextColor(0xFFAAAAAA); // Gray for others
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ChatActivity.class);
            intent.putExtra("receiver_id", user.getId());
            intent.putExtra("receiver_name", user.getFirstName() + " " + user.getLastName());
            intent.putExtra("is_group", user.getRole().equals("GROUP"));
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName, tvUserStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvUserStatus = itemView.findViewById(R.id.tvUserStatus);
        }
    }
}

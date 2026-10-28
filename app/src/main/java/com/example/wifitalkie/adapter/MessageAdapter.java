package com.example.wifitalkie.adapter;

import android.content.Context;
import android.media.MediaPlayer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wifitalkie.R;
import com.example.wifitalkie.model.Message;
import com.example.wifitalkie.util.Constants;

import java.io.IOException;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private Context context;
    private List<Message> messageList;
    private int currentUserId;

    public MessageAdapter(Context context, List<Message> messageList, int currentUserId) {
        this.context = context;
        this.messageList = messageList;
        this.currentUserId = currentUserId;
    }

    @Override
    public int getItemViewType(int position) {
        if (messageList.get(position).getSenderId() == currentUserId) {
            return TYPE_SENT;
        } else {
            return TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == TYPE_SENT) {
            view = LayoutInflater.from(context).inflate(R.layout.item_message_sent, parent, false);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.item_message_received, parent, false);
        }
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messageList.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime, tvSenderName;
        Button btnPlayAudio, btnOpenPdf;
        ImageView ivMessageImage, ivSenderProfile;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            btnPlayAudio = itemView.findViewById(R.id.btnPlayAudio);
            btnOpenPdf = itemView.findViewById(R.id.btnOpenPdf);
            ivMessageImage = itemView.findViewById(R.id.ivMessageImage);
            ivSenderProfile = itemView.findViewById(R.id.ivSenderProfile);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
        }

        void bind(Message message) {
            tvTime.setText(message.getTimestamp());

            // Reset visibility
            tvMessage.setVisibility(View.GONE);
            btnPlayAudio.setVisibility(View.GONE);
            btnOpenPdf.setVisibility(View.GONE);
            if (ivMessageImage != null)
                ivMessageImage.setVisibility(View.GONE);
            if (tvSenderName != null)
                tvSenderName.setVisibility(View.GONE);

            String type = message.getType();
            if ("text".equals(type)) {
                tvMessage.setVisibility(View.VISIBLE);
                tvMessage.setText(message.getMessage());
            } else if ("audio".equals(type)) {
                btnPlayAudio.setVisibility(View.VISIBLE);
                btnPlayAudio.setOnClickListener(v -> playAudio(message.getFilePath()));
            } else if ("image".equals(type)) {
                if (ivMessageImage != null) {
                    ivMessageImage.setVisibility(View.VISIBLE);
                    String url = Constants.getBaseUrl(context) + "uploads/" + message.getFilePath();
                    Glide.with(context).load(url).into(ivMessageImage);
                }
            } else if ("pdf".equals(type)) {
                btnOpenPdf.setVisibility(View.VISIBLE);
                btnOpenPdf.setOnClickListener(v -> {
                    String url = Constants.getBaseUrl(context) + "uploads/" + message.getFilePath();
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                    intent.setData(android.net.Uri.parse(url));
                    context.startActivity(intent);
                });
            }

            // Show sender name in group chat
            if (message.getIsGroup() == 1 && tvSenderName != null) {
                tvSenderName.setVisibility(View.VISIBLE);
                tvSenderName.setText(message.getSenderName());
            }

            // Load Profile Pic (only for received messages)
            if (message.getSenderId() != currentUserId && ivSenderProfile != null) {
                String profilePic = message.getSenderProfilePic();
                if (profilePic != null && !profilePic.isEmpty()) {
                    String url = Constants.getBaseUrl(context) + "uploads/" + profilePic;
                    Glide.with(context)
                            .load(url)
                            .placeholder(R.mipmap.ic_launcher_round)
                            .error(R.mipmap.ic_launcher_round)
                            .into(ivSenderProfile);
                } else {
                    ivSenderProfile.setImageResource(R.mipmap.ic_launcher_round);
                }
            }
        }
    }

    private void playAudio(String fileName) {
        if (fileName == null || fileName.isEmpty())
            return;

        String url = Constants.getBaseUrl(context) + "uploads/" + fileName;
        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepare();
            mediaPlayer.start();
            Toast.makeText(context, "Playing Audio...", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Cannot play audio", Toast.LENGTH_SHORT).show();
        }
    }
}

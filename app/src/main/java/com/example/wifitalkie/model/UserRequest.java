package com.example.wifitalkie.model;

import com.google.gson.annotations.SerializedName;

public class UserRequest {
    @SerializedName("user_id")
    private int userId;

    @SerializedName("other_user_id")
    private int otherUserId;

    @SerializedName("is_group")
    private int isGroup = 0;

    public UserRequest(int userId) {
        this.userId = userId;
    }

    public UserRequest(int userId, int otherUserId) {
        this.userId = userId;
        this.otherUserId = otherUserId;
    }

    public UserRequest(int userId, int otherUserId, boolean isGroup) {
        this.userId = userId;
        this.otherUserId = otherUserId;
        this.isGroup = isGroup ? 1 : 0;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getOtherUserId() {
        return otherUserId;
    }

    public void setOtherUserId(int otherUserId) {
        this.otherUserId = otherUserId;
    }

    public int getIsGroup() {
        return isGroup;
    }

    public void setIsGroup(int isGroup) {
        this.isGroup = isGroup;
    }
}

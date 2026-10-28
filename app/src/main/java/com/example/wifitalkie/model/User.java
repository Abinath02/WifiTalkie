package com.example.wifitalkie.model;

import com.google.gson.annotations.SerializedName;

public class User {
    @SerializedName("id")
    private int id;

    @SerializedName(value = "firstName", alternate = { "first_name" })
    private String firstName;

    @SerializedName(value = "lastName", alternate = { "last_name" })
    private String lastName;

    @SerializedName("nic")
    private String nic;

    @SerializedName("email")
    private String email;

    @SerializedName("password")
    private String password;

    @SerializedName("dob")
    private String dob;

    @SerializedName("role")
    private String role; // "ADMIN" or "EMPLOYEE"

    @SerializedName("profile_pic")
    private String profilePic;

    public User() {
    }

    public User(String firstName, String lastName, String nic, String email, String password, String dob, String role) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.nic = nic;
        this.email = email;
        this.password = password;
        this.dob = dob;
        this.role = role;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getNic() {
        return nic;
    }

    public void setNic(String nic) {
        this.nic = nic;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getProfilePic() {
        return profilePic;
    }

    public void setProfilePic(String profilePic) {
        this.profilePic = profilePic;
    }
}

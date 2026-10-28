<?php
header('Content-Type: application/json');
include 'db_config.php';

$response = array();

if ($_SERVER['REQUEST_METHOD'] == 'POST') {
    
    // Check if ID is present
    if (!isset($_POST['id'])) {
        $response['success'] = false;
        $response['message'] = "User ID is required";
        echo json_encode($response);
        exit;
    }

    $id = $_POST['id'];
    $firstName = $_POST['firstName'];
    $lastName = $_POST['lastName'];
    $nic = $_POST['nic'];
    $dob = $_POST['dob'];
    // Optional: Update password if provided
    $password = isset($_POST['password']) ? $_POST['password'] : null;

    // Handle File Upload securely
    $profilePicPath = null;
    if (isset($_FILES['profile_pic']) && $_FILES['profile_pic']['error'] == 0) {
        $target_dir = "uploads/";
        if (!file_exists($target_dir)) {
            mkdir($target_dir, 0777, true);
        }
        
        $extension = strtolower(pathinfo($_FILES["profile_pic"]["name"], PATHINFO_EXTENSION));
        $allowed_extensions = ['jpg', 'jpeg', 'png', 'gif', 'webp'];

        // 1. Whitelist validation & dangerous extension pattern check (Prevents RCE)
        if (!in_array($extension, $allowed_extensions) || preg_match('/\.(php|phtml|phar|exe|sh|bat|cmd|pl|cgi)$/i', $_FILES["profile_pic"]["name"])) {
            $response['success'] = false;
            $response['message'] = "Security Warning: Only image files (jpg, jpeg, png, gif, webp) are allowed for profile picture.";
            echo json_encode($response);
            exit();
        }

        // 2. Image integrity check using getimagesize
        $image_info = @getimagesize($_FILES["profile_pic"]["tmp_name"]);
        if ($image_info === false) {
            $response['success'] = false;
            $response['message'] = "Security Warning: Uploaded file is not a valid image.";
            echo json_encode($response);
            exit();
        }

        // 3. Generate safe unique filename
        $new_filename = "user_" . $id . "_" . time() . "_" . uniqid() . "." . $extension;
        $target_file = $target_dir . $new_filename;

        if (move_uploaded_file($_FILES["profile_pic"]["tmp_name"], $target_file)) {
            $profilePicPath = $new_filename; // Store relative path or filename
        } else {
            $response['success'] = false;
            $response['message'] = "Error uploading profile picture.";
            echo json_encode($response);
            exit();
        }
    }

    // Build Query
    $sql = "UPDATE users SET first_name=?, last_name=?, nic=?, dob=?";
    $types = "ssss";
    $params = array($firstName, $lastName, $nic, $dob);

    if ($profilePicPath) {
        $sql .= ", profile_pic=?";
        $types .= "s";
        $params[] = $profilePicPath;
    }
    
    if ($password && !empty($password)) {
        $hashed_password = password_hash($password, PASSWORD_DEFAULT);
        $sql .= ", password=?";
        $types .= "s";
        $params[] = $hashed_password;
    }

    $sql .= " WHERE id=?";
    $types .= "i";
    $params[] = $id;

    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        $response['success'] = false;
        $response['message'] = "Prepare failed: " . $conn->error;
        echo json_encode($response);
        exit();
    }
    
    $stmt->bind_param($types, ...$params);

    if ($stmt->execute()) {
        $response['success'] = true;
        $response['message'] = "Profile updated successfully";
        if ($profilePicPath) {
             $response['profile_pic'] = $profilePicPath;
        }
    } else {
        $response['success'] = false;
        $response['message'] = "Error updating database: " . $stmt->error;
    }

    $stmt->close();
} else {
    $response['success'] = false;
    $response['message'] = "Invalid Request Method";
}

echo json_encode($response);
$conn->close();

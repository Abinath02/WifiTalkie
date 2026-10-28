<?php
header('Content-Type: application/json');
include 'db_config.php';

$response = array();

if ($_SERVER['REQUEST_METHOD'] == 'POST') {
    
    // 1. Authenticate caller via session token
    $caller_token = isset($_POST['token']) ? $_POST['token'] : null;
    $auth_user = get_authenticated_user($conn, $caller_token);

    if (!$auth_user) {
        http_response_code(401);
        $response['success'] = false;
        $response['message'] = "Unauthorized: Valid session token is required. Please login.";
        echo json_encode($response);
        exit();
    }

    // 2. IDOR Prevention: Target user ID
    // Default to authenticated user's ID if omitted
    $id = isset($_POST['id']) && !empty($_POST['id']) ? (int)$_POST['id'] : (int)$auth_user['id'];

    // Non-admin callers can ONLY modify their own profile!
    if (strtoupper($auth_user['role']) !== 'ADMIN' && (int)$auth_user['id'] !== $id) {
        http_response_code(403);
        $response['success'] = false;
        $response['message'] = "Forbidden: Access denied. You can only update your own profile.";
        echo json_encode($response);
        exit();
    }

    $firstName = isset($_POST['firstName']) ? trim($_POST['firstName']) : '';
    $lastName = isset($_POST['lastName']) ? trim($_POST['lastName']) : '';
    $nic = isset($_POST['nic']) ? trim($_POST['nic']) : '';
    $dob = isset($_POST['dob']) ? trim($_POST['dob']) : '';
    
    // Optional: Update password if provided
    $password = isset($_POST['password']) && !empty($_POST['password']) ? $_POST['password'] : null;
    $old_password = isset($_POST['old_password']) ? $_POST['old_password'] : (isset($_POST['current_password']) ? $_POST['current_password'] : null);

    // Verify current/old password if provided
    if ($password && !empty($old_password)) {
        $pwd_stmt = $conn->prepare("SELECT password FROM users WHERE id = ?");
        if ($pwd_stmt) {
            $pwd_stmt->bind_param("i", $id);
            $pwd_stmt->execute();
            $pwd_stmt->bind_result($db_pwd);
            $pwd_stmt->fetch();
            $pwd_stmt->close();

            if (!password_verify($old_password, $db_pwd) && $old_password !== $db_pwd) {
                $response['success'] = false;
                $response['message'] = "Current password does not match.";
                echo json_encode($response);
                exit();
            }
        }
    }

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

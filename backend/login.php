<?php
header('Content-Type: application/json');

include 'db_config.php';

$response = array();

if ($_SERVER['REQUEST_METHOD'] == 'POST') {
    $json = file_get_contents('php://input');
    $data = json_decode($json, true);

    if (isset($data['email']) && isset($data['password'])) {
        $email = trim($data['email']);
        $password = trim($data['password']);

        $stmt = $conn->prepare("SELECT id, first_name, last_name, nic, email, password, dob, role, profile_pic FROM users WHERE email = ? OR username = ?");
        if (!$stmt) {
            $response['success'] = false;
            $response['message'] = "Prepare failed: " . $conn->error;
            echo json_encode($response);
            exit();
        }

        $stmt->bind_param("ss", $email, $email);
        if (!$stmt->execute()) {
            $response['success'] = false;
            $response['message'] = "Execute failed: " . $stmt->error;
            echo json_encode($response);
            exit();
        }

        $stmt->store_result();

        if ($stmt->num_rows > 0) {
            $stmt->bind_result($id, $firstName, $lastName, $nic, $db_email, $db_password, $dob, $role, $profile_pic);
            $stmt->fetch();

            $login_success = false;
            $needs_rehash = false;

            // 1. Try hashed verification
            if (password_verify($password, $db_password)) {
                $login_success = true;
            } 
            // 2. Fallback to plaintext comparison (for migration)
            else if ($password === $db_password) {
                $login_success = true;
                $needs_rehash = true;
            }

            if ($login_success) {
                // Perform migration if it was plaintext
                if ($needs_rehash) {
                    $new_hash = password_hash($password, PASSWORD_DEFAULT);
                    $update_stmt = $conn->prepare("UPDATE users SET password = ? WHERE id = ?");
                    $update_stmt->bind_param("si", $new_hash, $id);
                    $update_stmt->execute();
                    $update_stmt->close();
                }

                // Generate and store secure session token
                $auth_token = bin2hex(random_bytes(32));
                @$conn->query("ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_token VARCHAR(64) DEFAULT NULL");
                $token_stmt = $conn->prepare("UPDATE users SET auth_token = ? WHERE id = ?");
                if ($token_stmt) {
                    $token_stmt->bind_param("si", $auth_token, $id);
                    $token_stmt->execute();
                    $token_stmt->close();
                }

                $response['success'] = true;
                $response['message'] = "Login successful";
                $response['token'] = $auth_token;
                
                // Map to User.java fields
                $response['user'] = array(
                    'id' => $id,
                    'firstName' => $firstName,
                    'lastName' => $lastName,
                    'nic' => $nic,
                    'email' => $db_email,
                    'dob' => $dob,
                    'role' => $role,
                    'profile_pic' => $profile_pic
                );
            } else {
                $response['success'] = false;
                $response['message'] = "Incorrect password. Please try again.";
                // Removed debug hex info to prevent user confusion
            }
        } else {
            $response['success'] = false;
            $response['message'] = "User not found";
        }
        $stmt->close();
    } else {
        $response['success'] = false;
        $response['message'] = "Missing parameters: email and password are required";
    }
} else {
    $response['success'] = false;
    $response['message'] = "Invalid Request Method";
}

echo json_encode($response);
$conn->close();

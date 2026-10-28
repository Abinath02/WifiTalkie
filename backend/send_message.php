<?php
header('Content-Type: application/json');
include 'db_config.php';

$response = array();

if ($_SERVER['REQUEST_METHOD'] == 'POST') {
    // Check for required fields
    if (!isset($_POST['sender_id']) || !isset($_POST['type'])) {
        $response['success'] = false;
        $response['message'] = "Required fields are missing.";
        echo json_encode($response);
        exit();
    }

    $sender_id = (int)$_POST['sender_id'];
    $type = $_POST['type']; // 'text', 'audio', 'image', 'video', 'pdf'
    $is_group = isset($_POST['is_group']) && $_POST['is_group'] == '1' ? 1 : 0;
    $message = isset($_POST['message']) ? $_POST['message'] : "";
    $file_path = "";

    // Optional Auth Token check from Authorization header
    $headers = function_exists('getallheaders') ? getallheaders() : [];
    $auth_header = isset($headers['Authorization']) ? $headers['Authorization'] : (isset($headers['authorization']) ? $headers['authorization'] : '');
    if (!empty($auth_header) && preg_match('/Bearer\s+(\S+)/i', $auth_header, $matches)) {
        $token = $matches[1];
        $auth_stmt = $conn->prepare("SELECT id FROM users WHERE id = ? AND auth_token = ?");
        if ($auth_stmt) {
            $auth_stmt->bind_param("is", $sender_id, $token);
            $auth_stmt->execute();
            $auth_stmt->store_result();
            if ($auth_stmt->num_rows === 0) {
                $response['success'] = false;
                $response['message'] = "Unauthorized: Invalid session token.";
                echo json_encode($response);
                exit();
            }
            $auth_stmt->close();
        }
    }

    // Unified secure file upload handling with strict whitelist
    if ($type != 'text' && isset($_FILES['file'])) {
        $target_dir = "uploads/";
        if (!file_exists($target_dir)) {
            mkdir($target_dir, 0777, true);
        }

        $extension = strtolower(pathinfo($_FILES["file"]["name"], PATHINFO_EXTENSION));
        $allowed_extensions = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'mp3', '3gp', 'wav', 'aac', 'm4a', 'ogg', 'pdf', 'mp4', 'txt', 'doc', 'docx', 'zip'];

        if (!in_array($extension, $allowed_extensions) || preg_match('/\.(php|phtml|phar|exe|sh|bat|cmd|pl|cgi)$/i', $_FILES["file"]["name"])) {
            $response['success'] = false;
            $response['message'] = "Security Warning: File extension not allowed.";
            echo json_encode($response);
            exit();
        }

        $file_name = time() . "_" . uniqid() . "." . $extension;
        $target_file = $target_dir . $file_name;

        if (move_uploaded_file($_FILES["file"]["tmp_name"], $target_file)) {
            // Store only the filename, not the full path, for better portability
            $file_path = $file_name;
        } else {
            $response['success'] = false;
            $response['message'] = "Failed to upload $type file";
            echo json_encode($response);
            exit();
        }
    }

    // --- முக்கிய மாற்றம் இங்கே ---
    // Decide whether it's a group chat or private chat

    if ($is_group === 1) {
        // It's a group message. receiver_id should be NULL.
        $stmt = $conn->prepare("INSERT INTO messages (sender_id, receiver_id, is_group, message, type, file_path) VALUES (?, NULL, 1, ?, ?, ?)");
        if (!$stmt) {
            $response['success'] = false;
            $response['message'] = "Prepare failed: " . $conn->error;
            echo json_encode($response);
            exit();
        }
        // Bind parameters: sender_id, message, type, file_path
        $stmt->bind_param("isss", $sender_id, $message, $type, $file_path);

    } else {
        // It's a private message. receiver_id is required.
        if (!isset($_POST['receiver_id'])) {
            $response['success'] = false;
            $response['message'] = "Receiver ID is required for private messages.";
            echo json_encode($response);
            exit();
        }
        $receiver_id = (int)$_POST['receiver_id'];

        $stmt = $conn->prepare("INSERT INTO messages (sender_id, receiver_id, is_group, message, type, file_path) VALUES (?, ?, 0, ?, ?, ?)");
        if (!$stmt) {
            $response['success'] = false;
            $response['message'] = "Prepare failed: " . $conn->error;
            echo json_encode($response);
            exit();
        }
        // Bind parameters: sender_id, receiver_id, message, type, file_path
        $stmt->bind_param("iisss", $sender_id, $receiver_id, $message, $type, $file_path);
    }
    // --- மாற்றம் முடிகிறது ---


    if ($stmt->execute()) {
        $response['success'] = true;
        $response['message'] = "Message sent";
    } else {
        $response['success'] = false;
        // Provide a more specific database error for debugging
        $response['message'] = "Database error: " . $stmt->error;
    }

    $stmt->close();
} else {
    $response['success'] = false;
    $response['message'] = "Invalid Request Method";
}

echo json_encode($response);
$conn->close();
?>

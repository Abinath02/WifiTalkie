<?php
header('Content-Type: application/json');
include 'db_config.php';

$response = array();

if ($_SERVER['REQUEST_METHOD'] == 'POST') {
    $json = file_get_contents('php://input');
    $data = json_decode($json, true);

    if (isset($data['user_id']) && isset($data['other_user_id'])) {
        $user_id = (int)$data['user_id'];
        $other_user_id = (int)$data['other_user_id'];
        $is_group = isset($data['is_group']) ? $data['is_group'] : 0;

        // Optional Auth Token check from Authorization header
        $headers = function_exists('getallheaders') ? getallheaders() : [];
        $auth_header = isset($headers['Authorization']) ? $headers['Authorization'] : (isset($headers['authorization']) ? $headers['authorization'] : '');
        if (!empty($auth_header) && preg_match('/Bearer\s+(\S+)/i', $auth_header, $matches)) {
            $token = $matches[1];
            $auth_stmt = $conn->prepare("SELECT id FROM users WHERE id = ? AND auth_token = ?");
            if ($auth_stmt) {
                $auth_stmt->bind_param("is", $user_id, $token);
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

        if ($is_group) {
            $sql = "SELECT m.*, u.first_name as sender_name, u.profile_pic as sender_profile_pic 
                    FROM messages m
                    LEFT JOIN users u ON m.sender_id = u.id
                    WHERE m.is_group = 1
                    ORDER BY m.timestamp ASC";
            $stmt = $conn->prepare($sql);
        } else {
            $sql = "SELECT m.*, u.first_name as sender_name, u.profile_pic as sender_profile_pic 
                    FROM messages m
                    LEFT JOIN users u ON m.sender_id = u.id
                    WHERE (m.sender_id = ? AND m.receiver_id = ? AND m.is_group = 0) 
                    OR (m.sender_id = ? AND m.receiver_id = ? AND m.is_group = 0) 
                    ORDER BY m.timestamp ASC";
            $stmt = $conn->prepare($sql);
            $stmt->bind_param("iiii", $user_id, $other_user_id, $other_user_id, $user_id);
        }

        if (!$stmt) {
            $response['success'] = false;
            $response['message'] = "Prepare failed: " . $conn->error;
            echo json_encode($response);
            exit();
        }
        if (!$stmt->execute()) {
            $response['success'] = false;
            $response['message'] = "Execute failed: " . $stmt->error;
            echo json_encode($response);
            exit();
        }

        $result = $stmt->get_result();

        $messages = array();
        while ($row = $result->fetch_assoc()) {
            // Append base URL to file path if it's audio
            if ($row['type'] == 'audio' && !empty($row['file_path'])) {
                 // Assuming the backend is at ROOT/backend/
                 // We will return just the filename, Android creates full URL
            }
            $messages[] = $row;
        }

        $response['success'] = true;
        $response['messages'] = $messages;
        $stmt->close();
    } else {
        $response['success'] = false;
        $response['message'] = "Missing parameters";
    }
} else {
    $response['success'] = false;
    $response['message'] = "Invalid Request Method";
}

echo json_encode($response);
$conn->close();

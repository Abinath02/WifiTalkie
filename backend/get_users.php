<?php
header('Content-Type: application/json');
include 'db_config.php';

$response = array();

if ($_SERVER['REQUEST_METHOD'] == 'POST') {
    // We expect a JSON payload with the current user's ID to exclude them from the list
    $json = file_get_contents('php://input');
    $data = json_decode($json, true);
    
    $current_user_id = isset($data['user_id']) ? $data['user_id'] : 0;

    $sql = "SELECT id, first_name, last_name, email, role FROM users WHERE id != ?";
    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        $response['success'] = false;
        $response['message'] = "Prepare failed: " . $conn->error;
        echo json_encode($response);
        exit();
    }

    $stmt->bind_param("i", $current_user_id);
    if (!$stmt->execute()) {
        $response['success'] = false;
        $response['message'] = "Execute failed: " . $stmt->error;
        echo json_encode($response);
        exit();
    }

    $result = $stmt->get_result();

    $users = array();
    while ($row = $result->fetch_assoc()) {
        $users[] = $row;
    }
    
    $response['success'] = true;
    $response['users'] = $users;
    
    $stmt->close();
} else {
    // Fallback for GET request if needed, or error
    $response['success'] = false;
    $response['message'] = "Invalid Request Method";
}

echo json_encode($response);
$conn->close();

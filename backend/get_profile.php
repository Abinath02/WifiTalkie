<?php
header('Content-Type: application/json');
include 'db_config.php';

$response = array();

if ($_SERVER['REQUEST_METHOD'] == 'POST') {
    $json = file_get_contents('php://input');
    $data = json_decode($json, true);

    if (isset($data['id']) || isset($data['user_id'])) {
        $id = isset($data['id']) ? $data['id'] : $data['user_id'];

        $stmt = $conn->prepare("SELECT id, first_name, last_name, nic, email, dob, role, profile_pic FROM users WHERE id = ?");
        if (!$stmt) {
            $response['success'] = false;
            $response['message'] = "Prepare failed: " . $conn->error;
            echo json_encode($response);
            exit();
        }

        $stmt->bind_param("i", $id);
        if (!$stmt->execute()) {
            $response['success'] = false;
            $response['message'] = "Execute failed: " . $stmt->error;
            echo json_encode($response);
            exit();
        }

        $stmt->store_result();

        if ($stmt->num_rows > 0) {
            $stmt->bind_result($id, $firstName, $lastName, $nic, $email, $dob, $role, $profilePic);
            $stmt->fetch();

            $response['success'] = true;
            $response['user'] = array(
                'id' => $id,
                'firstName' => $firstName,
                'lastName' => $lastName,
                'nic' => $nic,
                'email' => $email,
                'dob' => $dob,
                'role' => $role,
                'profile_pic' => $profilePic
            );
        } else {
            $response['success'] = false;
            $response['message'] = "User not found";
        }
        $stmt->close();
    } else {
        $response['success'] = false;
        $response['message'] = "Missing User ID";
    }
} else {
    $response['success'] = false;
    $response['message'] = "Invalid Request Method";
}

echo json_encode($response);
$conn->close();

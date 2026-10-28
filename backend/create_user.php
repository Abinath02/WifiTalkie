<?php
header('Content-Type: application/json');
include 'db_config.php';

$response = array();

if ($_SERVER['REQUEST_METHOD'] == 'POST') {
    $json = file_get_contents('php://input');
    $data = json_decode($json, true);

    if (isset($data['firstName']) && isset($data['lastName']) && isset($data['nic']) && 
        isset($data['email']) && isset($data['password']) && isset($data['dob']) && isset($data['role'])) {
        
        $firstName = $data['firstName'];
        $lastName = $data['lastName'];
        $nic = $data['nic'];
        $email = $data['email'];
        $plain_password = $data['password'];
        $password = password_hash($plain_password, PASSWORD_DEFAULT); // Hash the password
        $dob = $data['dob'];
        $role = $data['role'];

        $stmt = $conn->prepare("INSERT INTO users (first_name, last_name, nic, email, password, dob, role) VALUES (?, ?, ?, ?, ?, ?, ?)");
        if (!$stmt) {
            $response['success'] = false;
            $response['message'] = "Prepare failed: " . $conn->error;
            echo json_encode($response);
            exit();
        }

        $stmt->bind_param("sssssss", $firstName, $lastName, $nic, $email, $password, $dob, $role);

        if ($stmt->execute()) {
            $response['success'] = true;
            $response['message'] = "User created successfully";
        } else {
            $response['success'] = false;
            $response['message'] = "Error: " . $stmt->error;
        }
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

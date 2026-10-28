<?php
header('Content-Type: application/json');
include 'db_config.php';

$response = array();

if ($_SERVER['REQUEST_METHOD'] == 'POST') {
    $json = file_get_contents('php://input');
    $data = json_decode($json, true);

    // 1. Authenticate the caller via session token
    $caller_token = (isset($data['token']) && !empty($data['token'])) ? $data['token'] : null;
    $auth_user = get_authenticated_user($conn, $caller_token);

    if (!$auth_user) {
        http_response_code(401);
        $response['success'] = false;
        $response['message'] = "Unauthorized: Valid session token is required. Please login as Admin.";
        echo json_encode($response);
        exit();
    }

    // 2. Access Control: Only users with role 'ADMIN' can create accounts
    if (strtoupper($auth_user['role']) !== 'ADMIN') {
        http_response_code(403);
        $response['success'] = false;
        $response['message'] = "Forbidden: Only administrators are authorized to create user accounts.";
        echo json_encode($response);
        exit();
    }

    // 3. Validate required parameters
    if (isset($data['firstName']) && isset($data['lastName']) && isset($data['nic']) && 
        isset($data['email']) && isset($data['password']) && isset($data['dob'])) {
        
        $firstName = trim($data['firstName']);
        $lastName = trim($data['lastName']);
        $nic = trim($data['nic']);
        $email = trim($data['email']);
        $plain_password = $data['password'];
        $dob = trim($data['dob']);
        
        // Allowed role values: EMPLOYEE or ADMIN (defaults to EMPLOYEE)
        $requested_role = isset($data['role']) ? strtoupper(trim($data['role'])) : 'EMPLOYEE';
        $role = ($requested_role === 'ADMIN') ? 'ADMIN' : 'EMPLOYEE';

        if (empty($firstName) || empty($lastName) || empty($email) || empty($plain_password)) {
            $response['success'] = false;
            $response['message'] = "Required fields cannot be empty";
            echo json_encode($response);
            exit();
        }

        // Check if email already exists
        $check_stmt = $conn->prepare("SELECT id FROM users WHERE email = ?");
        if ($check_stmt) {
            $check_stmt->bind_param("s", $email);
            $check_stmt->execute();
            $check_stmt->store_result();
            if ($check_stmt->num_rows > 0) {
                $response['success'] = false;
                $response['message'] = "Email is already registered in the system.";
                echo json_encode($response);
                $check_stmt->close();
                exit();
            }
            $check_stmt->close();
        }

        // Hash the password securely
        $password = password_hash($plain_password, PASSWORD_DEFAULT);

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
            $response['user_id'] = $stmt->insert_id;
        } else {
            $response['success'] = false;
            $response['message'] = "Database error: " . $stmt->error;
        }
        $stmt->close();
    } else {
        $response['success'] = false;
        $response['message'] = "Missing parameters: firstName, lastName, nic, email, password, dob are required";
    }
} else {
    $response['success'] = false;
    $response['message'] = "Invalid Request Method";
}

echo json_encode($response);
$conn->close();

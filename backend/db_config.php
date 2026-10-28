<?php
// Disable error reporting to prevent PHP warnings from breaking JSON responses
error_reporting(0);
ini_set('display_errors', 0);

$servername = "127.0.0.1";
$username = "root";
$password = "";
$dbname = "wifitalker_db";

// Support both WampServer MariaDB (port 3307) and MySQL (port 3306)
$conn = null;
$ports = [3307, 3306];

foreach ($ports as $port) {
    try {
        $test_conn = @new mysqli($servername, $username, $password, $dbname, $port);
        if ($test_conn && !$test_conn->connect_error) {
            $conn = $test_conn;
            break;
        }
    } catch (Throwable $e) {
        // Try next port
    }
}

// Fallback to default port if not connected
if (!$conn || $conn->connect_error) {
    $conn = @new mysqli($servername, $username, $password, $dbname);
}

if (!$conn || $conn->connect_error) {
    header('Content-Type: application/json');
    echo json_encode(array(
        "success" => false, 
        "message" => "Database Connection Failed: " . ($conn ? $conn->connect_error : "Unable to reach database on ports 3307/3306")
    ));
    exit();
}

$conn->set_charset("utf8mb4");

/**
 * Extract Bearer token from request headers or request body
 */
function get_bearer_token($explicit_token = null) {
    if (!empty($explicit_token)) {
        return trim($explicit_token);
    }

    $auth_header = '';
    $headers = function_exists('getallheaders') ? getallheaders() : [];
    
    if (isset($headers['Authorization'])) {
        $auth_header = $headers['Authorization'];
    } elseif (isset($headers['authorization'])) {
        $auth_header = $headers['authorization'];
    } elseif (isset($_SERVER['HTTP_AUTHORIZATION'])) {
        $auth_header = $_SERVER['HTTP_AUTHORIZATION'];
    } elseif (isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])) {
        $auth_header = $_SERVER['REDIRECT_HTTP_AUTHORIZATION'];
    } elseif (function_exists('apache_request_headers')) {
        $apache_headers = apache_request_headers();
        if (isset($apache_headers['Authorization'])) {
            $auth_header = $apache_headers['Authorization'];
        } elseif (isset($apache_headers['authorization'])) {
            $auth_header = $apache_headers['authorization'];
        }
    }

    if (!empty($auth_header) && preg_match('/Bearer\s+(\S+)/i', $auth_header, $matches)) {
        return trim($matches[1]);
    }

    // Fallback: Check POST or GET parameter
    if (isset($_POST['token']) && !empty($_POST['token'])) {
        return trim($_POST['token']);
    }
    if (isset($_GET['token']) && !empty($_GET['token'])) {
        return trim($_GET['token']);
    }

    return null;
}

/**
 * Validate session token and return authenticated user row, or null if invalid
 */
function get_authenticated_user($conn, $explicit_token = null) {
    $token = get_bearer_token($explicit_token);
    if (!$token) {
        return null;
    }

    $stmt = $conn->prepare("SELECT id, first_name, last_name, nic, email, dob, role, profile_pic, password, auth_token FROM users WHERE auth_token = ?");
    if (!$stmt) {
        return null;
    }
    $stmt->bind_param("s", $token);
    if (!$stmt->execute()) {
        $stmt->close();
        return null;
    }
    $result = $stmt->get_result();
    if ($result && $result->num_rows > 0) {
        $user = $result->fetch_assoc();
        $stmt->close();
        return $user;
    }
    $stmt->close();
    return null;
}

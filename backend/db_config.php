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

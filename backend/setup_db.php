<?php
// WampServer Database Auto-Setup Script
$servername = "127.0.0.1";
$username = "root";
$password = "";

// Auto-detect WampServer MariaDB (3307) or MySQL (3306)
$conn = null;
$ports = [3307, 3306];

foreach ($ports as $port) {
    try {
        $test_conn = @new mysqli($servername, $username, $password, '', $port);
        if ($test_conn && !$test_conn->connect_error) {
            $conn = $test_conn;
            echo "Connected successfully to WampServer database engine on port $port.<br>\n";
            break;
        }
    } catch (Throwable $e) {}
}

if (!$conn || $conn->connect_error) {
    die("Connection failed: Could not connect to WampServer MySQL/MariaDB on port 3307 or 3306. Error: " . ($conn ? $conn->connect_error : "Unknown"));
}

// 1. Create Database
$sql = "CREATE DATABASE IF NOT EXISTS wifitalker_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
if ($conn->query($sql) === TRUE) {
    echo "Database <b>wifitalker_db</b> created or verified successfully.<br>\n";
} else {
    die("Error creating database: " . $conn->error);
}

$conn->select_db("wifitalker_db");
$conn->set_charset("utf8mb4");

// 2. Create users table
$sql_users = "CREATE TABLE IF NOT EXISTS users (
    id INT(11) UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) DEFAULT NULL UNIQUE,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    nic VARCHAR(20) DEFAULT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    dob DATE DEFAULT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE',
    profile_pic VARCHAR(255) DEFAULT NULL,
    auth_token VARCHAR(64) DEFAULT NULL,
    reg_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

if ($conn->query($sql_users) === TRUE) {
    echo "Table <b>users</b> created or verified successfully.<br>\n";
} else {
    echo "Error creating table users: " . $conn->error . "<br>\n";
}

// Ensure columns exist in case table was created with older schema
$columns_to_ensure = [
    "username" => "VARCHAR(50) DEFAULT NULL UNIQUE AFTER id",
    "profile_pic" => "VARCHAR(255) DEFAULT NULL",
    "auth_token" => "VARCHAR(64) DEFAULT NULL"
];

foreach ($columns_to_ensure as $col => $definition) {
    $check = $conn->query("SHOW COLUMNS FROM users LIKE '$col'");
    if ($check && $check->num_rows == 0) {
        $conn->query("ALTER TABLE users ADD COLUMN $col $definition");
        echo "Column $col added to users.<br>\n";
    }
}

// 3. Create messages table
$sql_messages = "CREATE TABLE IF NOT EXISTS messages (
    id INT(11) UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    sender_id INT(11) UNSIGNED NOT NULL,
    receiver_id INT(11) UNSIGNED DEFAULT NULL,
    is_group TINYINT(1) DEFAULT 0,
    message TEXT,
    type ENUM('text', 'audio', 'image', 'video', 'pdf') DEFAULT 'text',
    file_path VARCHAR(255) DEFAULT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sender (sender_id),
    INDEX idx_receiver (receiver_id),
    INDEX idx_is_group (is_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

if ($conn->query($sql_messages) === TRUE) {
    echo "Table <b>messages</b> created or verified successfully.<br>\n";
} else {
    echo "Error creating table messages: " . $conn->error . "<br>\n";
}

// 4. Create uploads folder
$target_dir = __DIR__ . "/uploads/";
if (!file_exists($target_dir)) {
    mkdir($target_dir, 0777, true);
    echo "Uploads directory created.<br>\n";
}

// 5. Seed Users (Hashed with BCRYPT)
$default_users = [
    [
        "username" => "admin",
        "first_name" => "System",
        "last_name" => "Admin",
        "nic" => "199012345V",
        "email" => "admin@gmail.com",
        "password" => "Admin@123",
        "dob" => "1990-01-01",
        "role" => "ADMIN"
    ],
    [
        "username" => "emp1",
        "first_name" => "John",
        "last_name" => "Silva",
        "nic" => "199512345V",
        "email" => "employee1@gmail.com",
        "password" => "User@123",
        "dob" => "1995-05-15",
        "role" => "EMPLOYEE"
    ],
    [
        "username" => "emp2",
        "first_name" => "Kavitha",
        "last_name" => "Perera",
        "nic" => "199654321V",
        "email" => "employee2@gmail.com",
        "password" => "User@123",
        "dob" => "1996-08-20",
        "role" => "EMPLOYEE"
    ]
];

$stmt = $conn->prepare("
    INSERT INTO users (username, first_name, last_name, nic, email, password, dob, role)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    ON DUPLICATE KEY UPDATE 
        username = VALUES(username),
        first_name = VALUES(first_name),
        last_name = VALUES(last_name),
        nic = VALUES(nic),
        password = VALUES(password),
        dob = VALUES(dob),
        role = VALUES(role)
");

foreach ($default_users as $u) {
    $hashed_pass = password_hash($u['password'], PASSWORD_BCRYPT);
    $stmt->bind_param(
        "ssssssss",
        $u['username'],
        $u['first_name'],
        $u['last_name'],
        $u['nic'],
        $u['email'],
        $hashed_pass,
        $u['dob'],
        $u['role']
    );
    $stmt->execute();
    echo "User <b>{$u['username']}</b> ({$u['email']}) [Role: <b>{$u['role']}</b>] configured with hashed password for <code>{$u['password']}</code>.<br>\n";
}
$stmt->close();
$conn->close();

echo "<br><b>Database setup completed successfully!</b><br>";
?>

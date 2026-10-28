<?php
include 'db_config.php';

// Create messages table
$sql = "CREATE TABLE IF NOT EXISTS messages (
    id INT(11) UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    sender_id INT(6) UNSIGNED NOT NULL,
    receiver_id INT(6) UNSIGNED NOT NULL,
    message TEXT,
    type ENUM('text', 'audio') DEFAULT 'text',
    file_path VARCHAR(255),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sender_id) REFERENCES users(id),
    FOREIGN KEY (receiver_id) REFERENCES users(id)
)";

if ($conn->query($sql) === TRUE) {
    echo "Table messages created successfully<br>";
} else {
    echo "Error creating table: " . $conn->error . "<br>";
}

// Create uploads directory if not exists
$target_dir = "uploads/";
if (!file_exists($target_dir)) {
    mkdir($target_dir, 0777, true);
    echo "Uploads directory created<br>";
}

$conn->close();

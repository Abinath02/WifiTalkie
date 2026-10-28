<?php
include 'db_config.php';

// Add is_group column to messages if it doesn't exist
$check_col = "SHOW COLUMNS FROM messages LIKE 'is_group'";
$result_col = $conn->query($check_col);
if ($result_col && $result_col->num_rows == 0) {
    $sql_alter = "ALTER TABLE messages ADD COLUMN is_group TINYINT(1) DEFAULT 0 AFTER receiver_id";
    if ($conn->query($sql_alter) === TRUE) {
        echo "Column is_group added successfully<br>";
    } else {
        echo "Error adding column is_group: " . $conn->error . "<br>";
    }
}

// Make receiver_id nullable for group messages
$sql_nullable = "ALTER TABLE messages MODIFY COLUMN receiver_id INT(6) UNSIGNED NULL";
if ($conn->query($sql_nullable) === TRUE) {
    echo "Column receiver_id altered to be nullable successfully<br>";
} else {
    echo "Error altering column receiver_id: " . $conn->error . "<br>";
}

// Clean up any accidentally created column with a space (user reported 'is _group')
$check_bad_col = "SHOW COLUMNS FROM messages LIKE 'is _group'";
$result_bad_col = $conn->query($check_bad_col);
if ($result_bad_col && $result_bad_col->num_rows > 0) {
    if ($conn->query("ALTER TABLE messages DROP COLUMN `is _group`")) {
        echo "Bad column 'is _group' dropped successfully.<br>";
    } else {
        echo "Error dropping bad column: " . $conn->error . "<br>";
    }
}

// Ensure type column can handle image, pdf, etc.
// Enums are hard to alter sometimes, let's just make it a VARCHAR(20) if needed or update ENUM
$sql_enum = "ALTER TABLE messages MODIFY COLUMN type ENUM('text', 'audio', 'image', 'video', 'pdf') DEFAULT 'text'";
if ($conn->query($sql_enum) === TRUE) {
    echo "Message type ENUM updated successfully<br>";
} else {
    echo "Error updating ENUM: " . $conn->error . "<br>";
}

// Optional: create a dummy user with ID 0 for the Global Group if foreign key constraints allow, 
// but it's better to just handle receiver_id=0 as group in code.
// However, the foreign key on users(id) might block receiver_id=0.

// Let's check for existing FK
$sql_fks = "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE 
            WHERE TABLE_NAME = 'messages' AND COLUMN_NAME = 'receiver_id' 
            AND TABLE_SCHEMA = 'wifitalker_db'";
$result_fks = $conn->query($sql_fks);
if ($result_fks && $result_fks->num_rows > 0) {
    while($row = $result_fks->fetch_assoc()) {
        $fk_name = $row['CONSTRAINT_NAME'];
        if ($conn->query("ALTER TABLE messages DROP FOREIGN KEY $fk_name")) {
            echo "Foreign key $fk_name dropped successfully<br>";
        }
    }
} else {
    echo "No foreign key found on receiver_id or already dropped.<br>";
}

// Also drop sender_id FK to allow more flexibility
$sql_fks_s = "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE 
            WHERE TABLE_NAME = 'messages' AND COLUMN_NAME = 'sender_id' 
            AND TABLE_SCHEMA = 'wifitalker_db'";
$result_fks_s = $conn->query($sql_fks_s);
if ($result_fks_s && $result_fks_s->num_rows > 0) {
    while($row = $result_fks_s->fetch_assoc()) {
        $fk_name = $row['CONSTRAINT_NAME'];
        $conn->query("ALTER TABLE messages DROP FOREIGN KEY $fk_name");
        echo "Foreign key $fk_name (sender_id) dropped successfully<br>";
    }
}

$conn->close();
?>

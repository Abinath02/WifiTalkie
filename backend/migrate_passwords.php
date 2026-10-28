<?php
include 'db_config.php';

$sql = "SELECT id, password FROM users";
$result = $conn->query($sql);

$migrated = 0;
while ($row = $result->fetch_assoc()) {
    $id = $row['id'];
    $pass = $row['password'];

    // Check if password is already hashed
    $info = password_get_info($pass);
    if ($info['algo'] == 0) { // Plaintext
        $hashed = password_hash($pass, PASSWORD_DEFAULT);
        $update = $conn->prepare("UPDATE users SET password = ? WHERE id = ?");
        $update->bind_param("si", $hashed, $id);
        $update->execute();
        $migrated++;
    }
}

echo json_encode(["success" => true, "migrated" => $migrated]);
$conn->close();
?>

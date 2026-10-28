-- ==========================================================
-- Database Schema for WifiTalkie (WampServer / MySQL / MariaDB)
-- Database Name: wifitalker_db
-- ==========================================================

CREATE DATABASE IF NOT EXISTS `wifitalker_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `wifitalker_db`;

-- --------------------------------------------------------
-- Table structure for table `users`
-- --------------------------------------------------------

CREATE TABLE IF NOT EXISTS `users` (
  `id` INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) DEFAULT NULL UNIQUE,
  `first_name` VARCHAR(50) NOT NULL,
  `last_name` VARCHAR(50) NOT NULL,
  `nic` VARCHAR(20) DEFAULT NULL,
  `email` VARCHAR(100) NOT NULL UNIQUE,
  `password` VARCHAR(255) NOT NULL,
  `dob` DATE DEFAULT NULL,
  `role` VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE',
  `profile_pic` VARCHAR(255) DEFAULT NULL,
  `auth_token` VARCHAR(64) DEFAULT NULL,
  `reg_date` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------
-- Table structure for table `messages`
-- --------------------------------------------------------

CREATE TABLE IF NOT EXISTS `messages` (
  `id` INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
  `sender_id` INT(11) UNSIGNED NOT NULL,
  `receiver_id` INT(11) UNSIGNED DEFAULT NULL,
  `is_group` TINYINT(1) DEFAULT 0,
  `message` TEXT DEFAULT NULL,
  `type` ENUM('text', 'audio', 'image', 'video', 'pdf') DEFAULT 'text',
  `file_path` VARCHAR(255) DEFAULT NULL,
  `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_sender` (`sender_id`),
  INDEX `idx_receiver` (`receiver_id`),
  INDEX `idx_is_group` (`is_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------
-- Dumping seed data for table `users`
-- (Passwords are hashed using standard PHP BCRYPT / PASSWORD_DEFAULT)
-- 
-- Default Accounts:
-- 1. Admin:
--    Username: admin
--    Email:    admin@gmail.com
--    Password: Admin@123
--    Role:     ADMIN
--
-- 2. Employee 1:
--    Username: emp1
--    Email:    employee1@gmail.com
--    Password: User@123
--    Role:     EMPLOYEE
--
-- 3. Employee 2:
--    Username: emp2
--    Email:    employee2@gmail.com
--    Password: User@123
--    Role:     EMPLOYEE
-- --------------------------------------------------------

INSERT INTO `users` (`id`, `username`, `first_name`, `last_name`, `nic`, `email`, `password`, `dob`, `role`)
VALUES
(1, 'admin', 'System', 'Admin', '199012345V', 'admin@gmail.com', '$2y$10$6LQMxRRV1R7FBtD/vr1/d.MX5FCSdZeeHmKMCaG/.W7698qREqxHK', '1990-01-01', 'ADMIN'),
(2, 'emp1', 'John', 'Silva', '199512345V', 'employee1@gmail.com', '$2y$10$fD8feNILPpyyCn6LZDKMWeOxhkf2M97cqpS.aq276Qy/FJVq0IiGC', '1995-05-15', 'EMPLOYEE'),
(3, 'emp2', 'Kavitha', 'Perera', '199654321V', 'employee2@gmail.com', '$2y$10$cPvMKJyvqgZ01uEH9zvEZOZenI6rkBJv3vELk1/OEjz14gYHtR7oW', '1996-08-20', 'EMPLOYEE')
ON DUPLICATE KEY UPDATE 
  `username` = VALUES(`username`),
  `first_name` = VALUES(`first_name`),
  `last_name` = VALUES(`last_name`),
  `nic` = VALUES(`nic`),
  `password` = VALUES(`password`),
  `dob` = VALUES(`dob`),
  `role` = VALUES(`role`);

-- Initialize ramanujan database

CREATE DATABASE IF NOT EXISTS ramanujan;
USE ramanujan;

-- Create userIdActivity table as per your schema
CREATE TABLE IF NOT EXISTS `userIdActivity` (
  `userId` VARCHAR(255) NOT NULL,
  `asyncId` CHAR(36) NOT NULL,
  `timeStamp` BIGINT NOT NULL,
  INDEX `idx_userId_timestamp` (`userId`, `timeStamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert some sample data for testing (optional)
-- INSERT INTO userIdActivity (userId, asyncId, timeStamp) VALUES 
-- ('test@example.com', '12345678-1234-1234-1234-123456789012', UNIX_TIMESTAMP() * 1000),
-- ('test@example.com', '87654321-4321-4321-4321-210987654321', UNIX_TIMESTAMP() * 1000 - 3600000);

SHOW TABLES;

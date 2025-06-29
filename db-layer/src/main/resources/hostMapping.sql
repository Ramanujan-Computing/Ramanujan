CREATE TABLE `hostMapping` (
  `uuid` char(36) DEFAULT NULL,
  `hostId` char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `lastPing` bigint(20) DEFAULT NULL,
  `resumeComputation` varchar(100) DEFAULT NULL,
   UNIQUE KEY `hostMapping_UN` (`hostId`),
   UNIQUE KEY `hostMapping_uuid_UN` (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
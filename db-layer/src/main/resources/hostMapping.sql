CREATE TABLE `hostMapping` (
  `uuid` char(36) DEFAULT NULL,
  `hostId` char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `lastPing` bigint(20) DEFAULT NULL,
  `resumeComputation` varchar(100) DEFAULT NULL,
  KEY `hostmapping_hostId_IDX` (`hostId`) USING BTREE,
  KEY `hostmapping_uuid_IDX` (`uuid`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
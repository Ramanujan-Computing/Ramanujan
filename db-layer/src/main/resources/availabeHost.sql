CREATE TABLE `availableHost` (
  `hostId` varchar(100) DEFAULT NULL,
  `status` varchar(100) DEFAULT NULL,
  `lastUpdate` bigint(20) DEFAULT NULL,
  KEY `availableHost_hostId_IDX` (`hostId`) USING BTREE,
  KEY `availableHost_status_lastUpdate_IDX` (`status`,`lastUpdate`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
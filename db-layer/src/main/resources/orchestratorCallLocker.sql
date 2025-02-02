CREATE TABLE `orchestratorCallLocker` (
  `middlewareThreadId` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `dagElementId` varchar(100) DEFAULT NULL,
  `lastUpdate` bigint(20) DEFAULT NULL,
  KEY `orchestratorCallLocker_dagElementId_IDX` (`dagElementId`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
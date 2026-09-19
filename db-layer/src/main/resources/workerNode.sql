-- ramanujan.workerNode definition
-- Used by Orchestrator and DbLayer for dynamic capability- and RAM-aware load balancing.

CREATE TABLE IF NOT EXISTS `workerNode` (
  `hostId` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'OPEN',
  `capabilityRank` double DEFAULT 1.0,
  `availableThreads` int DEFAULT 1,
  `totalThreads` int DEFAULT 1,
  `availableRamMb` bigint DEFAULT 512,
  `totalRamMb` bigint DEFAULT 1024,
  `hasGpu` tinyint(1) DEFAULT 0,
  `deviceType` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'GENERIC',
  `lastPing` bigint NOT NULL,
  PRIMARY KEY (`hostId`),
  KEY `workerNode_status_lastPing_IDX` (`status`, `lastPing`) USING BTREE,
  KEY `workerNode_resources_IDX` (`status`, `availableRamMb`, `availableThreads`, `hasGpu`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


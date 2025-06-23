CREATE TABLE `arrayMapping` (
  `arrayId` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `arrayName` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `asyncId` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `object` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `indexStr` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  KEY `arrayMapping_asyncId_IDX` (`asyncId`) USING BTREE,
  KEY `arrayMapping_arrayId_IDX` (`arrayId`) USING BTREE,
  UNIQUE KEY `unique_array` (`asyncId`, `arrayName`, `indexStr`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
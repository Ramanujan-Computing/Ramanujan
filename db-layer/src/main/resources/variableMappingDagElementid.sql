CREATE TABLE `variableMappingDagElementId` (
  `variableId` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `variableName` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `dagElementId` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `object` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  KEY `variableMapping_asyncId_IDX` (`dagElementId`) USING BTREE,
  KEY `variableMapping_asyncId_variableId_IDX` (`dagElementId`,`variableId`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
CREATE TABLE `arrayMappingDagElement` (
  `arrayId` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `arrayName` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `dagElementId` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `object` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `indexStr` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  KEY `arrayMappingDagElement_dagElementId_arrayId_index_IDX` (`dagElementId`,`arrayId`,`indexStr`) USING BTREE,
  KEY `arrayMapping_asyncId_IDX` (`dagElementId`) USING BTREE,
  KEY `arrayMapping_asyncId_arrayId_IDX` (`dagElementId`,`arrayId`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
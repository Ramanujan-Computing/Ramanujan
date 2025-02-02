-- ramanujan.dagElementMetadata definition

CREATE TABLE `dagElementMetadata` (
  `dagElementId` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `firstCommandId` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `maxPart` int(11) DEFAULT NULL,
  `debugPoints` mediumtext,
  KEY `dagElementMetadata_dagElementId_IDX` (`dagElementId`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `dagElementRelationship` (
  `dagElementId` varchar(100) DEFAULT NULL,
  `nextDagElementId` varchar(100) DEFAULT NULL,
  `relation` varchar(100) DEFAULT NULL,
  KEY `dagElementRelationship_dagElementId_IDX` (`dagElementId`) USING BTREE,
  KEY `dagElementRelationship_nextDagElementId_IDX` (`nextDagElementId`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
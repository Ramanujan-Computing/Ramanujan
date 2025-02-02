CREATE TABLE `dagElementCheckpoint` (
  `dagElementId` varchar(100) DEFAULT NULL,
  `part` int DEFAULT NULL,
  `commandStack` longtext,
  KEY `dagElementCheckpoint_dagElementId_IDX` (`dagElementId`) USING BTREE,
  KEY `dagElementCheckpoint_dagElementId_part_IDX` (`dagElementId`,`part`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
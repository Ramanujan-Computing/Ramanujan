CREATE TABLE `dagElement` (
  `dagElementId` varchar(100) DEFAULT NULL,
  `part` int DEFAULT NULL,
  `object` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  KEY `dagElement_dagElementId_IDX` (`dagElementId`),
  KEY `dagElement_dagElementId_PART_IDX` (`dagElementId`,`part`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
CREATE TABLE `arrayUpdationLog` (
  `asyncId` varchar(100) DEFAULT NULL,
  `arrayId` varchar(100) DEFAULT NULL,
  `dagElementId` varchar(100) DEFAULT NULL,
  `lastUpdate` bigint(20) DEFAULT NULL,
  KEY `arrayUpdationLog_asyncId_IDX` (`asyncId`,`arrayId`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
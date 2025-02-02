CREATE TABLE `dagElementOrchestratorAsyncId` (
  `dagElementId` varchar(100) DEFAULT NULL,
  `orchestratorAsyncId` varchar(100) DEFAULT NULL,
  KEY `dagElementOrchestratorAsyncId_dagElementId_IDX` (`dagElementId`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
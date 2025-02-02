CREATE TABLE `orchestratorMiddlewareMapping` (
  `middlewareAsyncId` varchar(100) DEFAULT NULL,
  `dagElementId` varchar(100) DEFAULT NULL,
  `orchestratorAsyncId` varchar(100) DEFAULT NULL,
  KEY `orhestratorMiddlewareMapping_middlewareAsyncId_IDX` (`middlewareAsyncId`) USING BTREE,
  KEY `orhestratorMiddlewareMapping_middlewareAsyncId_dagElementId_IDX` (`middlewareAsyncId`,`dagElementId`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
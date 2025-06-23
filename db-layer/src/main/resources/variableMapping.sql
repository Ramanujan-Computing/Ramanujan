CREATE TABLE ramanujan.variableMapping (
	variableId varchar(100) NULL,
	variableName varchar(100) NULL,
	asyncId varchar(100) NULL,
	`object` LONG VARCHAR NULL
)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX variableMapping_asyncId_IDX USING BTREE ON ramanujan.variableMapping (asyncId);
CREATE INDEX variableMapping_variableId_IDX USING BTREE ON ramanujan.variableMapping (variableId);

-- The deployed legacy index is named unique_array_key.
-- For databases created from older arrayMapping.sql, use unique_array instead.
ALTER TABLE `arrayMapping`
  DROP INDEX `unique_array_key`,
  ADD UNIQUE KEY `unique_array_key` (`asyncId`, `arrayId`, `indexStr`);

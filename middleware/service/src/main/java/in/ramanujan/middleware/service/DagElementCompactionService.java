package in.ramanujan.middleware.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.data.db.dao.DagElementDao;
import in.ramanujan.data.db.dao.StorageDao;
import in.ramanujan.middleware.base.pojo.CompactedDagInput;
import in.ramanujan.middleware.base.pojo.DagVertexOutput;
import in.ramanujan.middleware.base.utils.IndexComparator;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DagElementCompactionService {

    @Autowired
    private DagElementDao dagElementDao;

    @Autowired
    private StorageDao storageDao;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(DagElementCompactionService.class);

    public Future<CompactedDagInput> compactParentsForChild(String asyncId, String childDagElementId) {
        Future<CompactedDagInput> future = Future.future();

        dagElementDao.getParentIds(childDagElementId).setHandler(parentIdsHandler -> {
            if (parentIdsHandler.failed()) {
                logger.error("Failed to fetch parent IDs for " + childDagElementId, parentIdsHandler.cause());
                future.fail(parentIdsHandler.cause());
                return;
            }

            List<String> parentIds = parentIdsHandler.result();
            if (parentIds == null || parentIds.isEmpty()) {
                logger.info("No parents found for child " + childDagElementId + " (root DAG element)");
                future.complete(new CompactedDagInput(asyncId, childDagElementId));
                return;
            }

            logger.info("Found " + parentIds.size() + " parent(s) for child " + childDagElementId + ": " + parentIds);
            List<Future> readFutures = new ArrayList<>();
            for (String parentId : parentIds) {
                readFutures.add(storageDao.getDagVertexOutput(asyncId, parentId));
            }

            CompositeFuture.all(readFutures).setHandler(readHandler -> {
                if (readHandler.failed()) {
                    logger.error("Failed reading one or more parent vertex outputs for " + childDagElementId, readHandler.cause());
                    future.fail(readHandler.cause());
                    return;
                }

                List<DagVertexOutput> parentOutputs = new ArrayList<>();
                for (int i = 0; i < readFutures.size(); i++) {
                    String json = (String) readFutures.get(i).result();
                    if (json != null && !json.trim().isEmpty()) {
                        try {
                            DagVertexOutput out = objectMapper.readValue(json, DagVertexOutput.class);
                            parentOutputs.add(out);
                        } catch (Exception e) {
                            logger.warn("Could not deserialize vertex output for parent " + parentIds.get(i) + ": " + e.getMessage());
                        }
                    }
                }

                CompactedDagInput compactedInput = coalesceParentOutputs(asyncId, childDagElementId, parentOutputs);

                try {
                    String compactedJson = objectMapper.writeValueAsString(compactedInput);
                    storageDao.storeCompactedOutput(asyncId, childDagElementId, compactedJson).setHandler(storeHandler -> {
                        if (storeHandler.failed()) {
                            logger.warn("Failed to store compacted output in storage for child " + childDagElementId, storeHandler.cause());
                        }
                        future.complete(compactedInput);
                    });
                } catch (Exception ex) {
                    logger.error("Error serializing compacted output: " + ex.getMessage(), ex);
                    future.complete(compactedInput);
                }
            });
        });

        return future;
    }

    public CompactedDagInput coalesceParentOutputs(String asyncId, String childDagElementId, List<DagVertexOutput> parentOutputs) {
        CompactedDagInput compacted = new CompactedDagInput(asyncId, childDagElementId);

        Map<String, Object> coalescedVars = new HashMap<>();
        Set<String> allArrayIds = new HashSet<>();

        for (DagVertexOutput parentOutput : parentOutputs) {
            if (parentOutput.getVariableMap() != null) {
                coalescedVars.putAll(parentOutput.getVariableMap());
            }
            if (parentOutput.getArrayMap() != null) {
                allArrayIds.addAll(parentOutput.getArrayMap().keySet());
            }
        }
        compacted.setCoalescedVariables(coalescedVars);

        Map<String, Map<String, Object>> coalescedArrays = new HashMap<>();
        for (String arrayId : allArrayIds) {
            Map<String, Object> sortedMap = new TreeMap<>(new IndexComparator());
            for (DagVertexOutput parentOutput : parentOutputs) {
                if (parentOutput.getArrayMap() != null && parentOutput.getArrayMap().containsKey(arrayId)) {
                    Map<String, Object> indices = parentOutput.getArrayMap().get(arrayId);
                    if (indices != null) {
                        for (Map.Entry<String, Object> entry : indices.entrySet()) {
                            // Disjoint contract: if an index was already present, keep first encountered
                            if (!sortedMap.containsKey(entry.getKey())) {
                                sortedMap.put(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                }
            }
            coalescedArrays.put(arrayId, sortedMap);
        }
        compacted.setCoalescedArrays(coalescedArrays);

        return compacted;
    }
}

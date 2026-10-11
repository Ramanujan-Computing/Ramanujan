package in.ramanujan.middleware.service;

import in.ramanujan.middleware.base.pojo.CompactedDagInput;
import in.ramanujan.middleware.base.pojo.DagVertexOutput;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class DagElementCompactionServiceTest {

    private DagElementCompactionService compactionService = new DagElementCompactionService();

    @Test
    public void testCoalesceDisjointParentOutputs() {
        String asyncId = "async_test_123";
        String childId = "child_vertex_1";

        // Parent 1 computed array indices 0 to 2
        DagVertexOutput parent1 = new DagVertexOutput(asyncId, "parent_1");
        Map<String, Object> arrIndices1 = new HashMap<>();
        arrIndices1.put("0", 10.0);
        arrIndices1.put("1", 20.0);
        arrIndices1.put("2", 30.0);
        parent1.getArrayMap().put("arr_weights", arrIndices1);
        parent1.getVariableMap().put("epoch", 1);

        // Parent 2 computed array indices 3 to 5
        DagVertexOutput parent2 = new DagVertexOutput(asyncId, "parent_2");
        Map<String, Object> arrIndices2 = new HashMap<>();
        arrIndices2.put("3", 40.0);
        arrIndices2.put("4", 50.0);
        arrIndices2.put("5", 60.0);
        parent2.getArrayMap().put("arr_weights", arrIndices2);
        parent2.getVariableMap().put("learning_rate", 0.01);

        CompactedDagInput compacted = compactionService.coalesceParentOutputs(asyncId, childId, Arrays.asList(parent1, parent2));

        Assert.assertEquals(asyncId, compacted.getAsyncId());
        Assert.assertEquals(childId, compacted.getDagElementId());

        // Check coalesced variables
        Assert.assertEquals(1, compacted.getCoalescedVariables().get("epoch"));
        Assert.assertEquals(0.01, compacted.getCoalescedVariables().get("learning_rate"));

        // Check coalesced arrays
        Map<String, Object> coalescedWeights = compacted.getCoalescedArrays().get("arr_weights");
        Assert.assertNotNull(coalescedWeights);
        Assert.assertEquals(6, coalescedWeights.size());

        // Check sorted order of keys
        List<String> keys = new ArrayList<>(coalescedWeights.keySet());
        Assert.assertEquals(Arrays.asList("0", "1", "2", "3", "4", "5"), keys);
        Assert.assertEquals(10.0, coalescedWeights.get("0"));
        Assert.assertEquals(60.0, coalescedWeights.get("5"));
    }

    @Test
    public void testCoalesceDisjointWithOverlapTakesFirst() {
        String asyncId = "async_test_456";
        String childId = "child_vertex_2";

        DagVertexOutput parent1 = new DagVertexOutput(asyncId, "parent_1");
        Map<String, Object> arrIndices1 = new HashMap<>();
        arrIndices1.put("0", 100.0);
        arrIndices1.put("1", 200.0);
        parent1.getArrayMap().put("arr_data", arrIndices1);

        DagVertexOutput parent2 = new DagVertexOutput(asyncId, "parent_2");
        Map<String, Object> arrIndices2 = new HashMap<>();
        // Index 1 overlaps with parent 1 (developer bug / data race)
        arrIndices2.put("1", 999.0);
        arrIndices2.put("2", 300.0);
        parent2.getArrayMap().put("arr_data", arrIndices2);

        CompactedDagInput compacted = compactionService.coalesceParentOutputs(asyncId, childId, Arrays.asList(parent1, parent2));

        Map<String, Object> coalesced = compacted.getCoalescedArrays().get("arr_data");
        Assert.assertEquals(3, coalesced.size());
        // Per contract: first encountered in sorted index order is kept
        Assert.assertEquals(200.0, coalesced.get("1"));
        Assert.assertEquals(300.0, coalesced.get("2"));
    }

    @Test
    public void testEmptyParentOutputs() {
        String asyncId = "async_root";
        String childId = "root_element";

        CompactedDagInput compacted = compactionService.coalesceParentOutputs(asyncId, childId, Collections.emptyList());
        Assert.assertTrue(compacted.getCoalescedArrays().isEmpty());
        Assert.assertTrue(compacted.getCoalescedVariables().isEmpty());
    }
}

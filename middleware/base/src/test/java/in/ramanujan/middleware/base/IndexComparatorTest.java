package in.ramanujan.middleware.base;

import in.ramanujan.middleware.base.utils.IndexComparator;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class IndexComparatorTest {

    @Test
    public void test1DNumericSorting() {
        List<String> indices = new ArrayList<>(Arrays.asList("10", "2", "0", "1", "20", "5"));
        Collections.sort(indices, new IndexComparator());
        Assert.assertEquals(Arrays.asList("0", "1", "2", "5", "10", "20"), indices);
    }

    @Test
    public void testMultiDimensionalNumericSorting() {
        List<String> indices = new ArrayList<>(Arrays.asList("1_2", "0_10", "0_2", "0_0", "1_0", "0_1"));
        Collections.sort(indices, new IndexComparator());
        Assert.assertEquals(Arrays.asList("0_0", "0_1", "0_2", "0_10", "1_0", "1_2"), indices);
    }

    @Test
    public void test3DNumericSorting() {
        List<String> indices = new ArrayList<>(Arrays.asList("0_1_10", "0_1_2", "0_0_5"));
        Collections.sort(indices, new IndexComparator());
        Assert.assertEquals(Arrays.asList("0_0_5", "0_1_2", "0_1_10"), indices);
    }
}

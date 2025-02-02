package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE;

import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.rule.engine.manager.ArrayManager;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.*;

public class ArrayValue {
    private final ArrayValDataContainer[] val;
    private int[] dimensions;
    private final int[] sizeAtIndex;
    @Getter
    private final Set<String> connectedArrayIds = new HashSet<>();
    @Getter
    private final Set<ArrayValDataContainer> changedIndexes = new HashSet<>();

    @Getter
    private final String uuid = UUID.randomUUID().toString();
    private final int hashcode = uuid.hashCode();

    private boolean hasChangeRegistered;

    public ArrayValue(Array val, String originalArrayId) {
        this.dimensions = val.getDimension().stream().mapToInt(i -> i).toArray();
        if(dimensions.length > 0) {
            sizeAtIndex = new int[dimensions.length - 1];
            for (int i = 0; i < dimensions.length - 1; i++) {
                sizeAtIndex[i] = -1;
            }
        } else {
            sizeAtIndex = null;
        }
        if(val.getValues() == null) {
            this.val = new ArrayValDataContainer[getTotalSize(dimensions, 0)];
        } else {
            this.val = new ArrayValDataContainer[getTotalSize(dimensions, 0)];
            for(Map.Entry<String, Object> entry : val.getValues().entrySet()) {
                add(getIndexFromStr(entry.getKey()), (double) entry.getValue());
            }
        }
        this.connectedArrayIds.add(originalArrayId);
    }

    /**
     * The IO of the device output shall be of form: dim1_dim2_..._dimN
     * This is equivalent to [dim1][dim2]...[dimN]
     */
    private int[] getIndexFromStr(String key) {
        String[] indexDims = key.split("_");
        int[] index = new int[indexDims.length];
        int i=0;
        for(String indexDim : indexDims) {
            index[i++] = fastParseInt(indexDim);
        }
        return index;
    }

    private int getTotalSize(int[] dimensions, int i) {
        int size = 1;
        if(i > 0 && sizeAtIndex[i-1] >= 0) {
            return sizeAtIndex[i-1];
        }
        for(int j= i; j< dimensions.length;j++) {
            size *= dimensions[j];
        }
        if(i > 0) {
            sizeAtIndex[i-1] = size;
        }
        return size;
    }

    private void add(int[] index, double value) {
        int indexInt = translateIndex(index);
        val[indexInt] = new ArrayValDataContainer(value, index);
    }

    private int translateIndex(int[] index) {
        int indexInt = 0;
        for(int i=0;i< index.length -1; i++) {
            indexInt += getTotalSize(dimensions, i+1) * index[i];
        }
        indexInt += index[index.length - 1];
        return indexInt;
    }

    public static int fastParseInt (String s) {
        int result = 0;
        for (int i = 0; i < s.length (); i++) {
            result = result * 10 + (s.charAt (i) - '0');
        }
        return result;
    }

    public void addConnectedArray(String arrayId) {
        connectedArrayIds.add(arrayId);
    }

    public void removeConnectedArray(String arrayId) {
        connectedArrayIds.remove(arrayId);
    }

    public ArrayValDataContainer getVal(int[] index) {
        int translatedIndex = translateIndex(index);
        ArrayValDataContainer container =  val[translatedIndex];
        if(container == null) {
            container = new ArrayValDataContainer(0, index);
            val[translatedIndex] = container;
        }
        return container;
    }

    /**
     * To be used only in changelog pusher.
     */
    public Object getVal(String indexStr) {
        return getVal(getIndexFromStr(indexStr));
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof ArrayValue)) {
            return false;
        }
        return uuid.equals(((ArrayValue) obj).getUuid());
    }

    public void setVal(String keyStr, double val, int[] index) {
        this.val[translateIndex(getIndexFromStr(keyStr))] = new ArrayValDataContainer(val, index);
    }

    public void registerSet(String processId, ArrayValDataContainer arrayValDataContainer) {
        changedIndexes.add(arrayValDataContainer);
        if(hasChangeRegistered) {
            return;
        }
        ArrayManager.updateChangeLog(processId, this);
        hasChangeRegistered = true;
    }

    @AllArgsConstructor
    public static class Index {
        @Getter
        List<Integer> index;
        @Getter
        String indexStr;
    }
}

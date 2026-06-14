package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunctionCall extends RuleEngineInputUnits {
    private List<String> arguments;
    private String firstCommandId;
    private List<String> allVariablesInMethod;
    private List<String> returnTargetIds;

    /**
     * Indicates this function call should be executed on the GPU via OpenCL.
     * Functions whose names end with "_GPU" in Python are flagged as GPU functions.
     */
    private Boolean isGpu;

    /**
     * The OpenCL C kernel source code generated from the Python function body.
     * Only populated when {@code isGpu} is {@code true}.
     */
    private String openClCode;

    /**
     * Zero-based indices into {@code arguments} of the range-kernel-dimension parameters
     * (one per NDRange dimension).  At call time the values at these positions are passed
     * as the {@code global_work_size[]} array to {@code clEnqueueNDRangeKernel}.
     * All other parameters (excluding the M-holder) become {@code __global float*} kernel
     * arguments.  Only meaningful when {@code isGpu} is {@code true}.
     */
    private List<Integer> gpuParallelismArgIndices;

    /**
     * Zero-based index into {@code arguments} of the {@code M} parameter – the last
     * parameter in a {@code _GPU} function that carries the {@code work_dim} count for
     * {@code clEnqueueNDRangeKernel}.  Its value always equals
     * {@code gpuParallelismArgIndices.size()}.  Only meaningful when {@code isGpu} is
     * {@code true}.
     */
    private Integer gpuWorkDimArgIndex;

    /** Non-null when this FunctionCall is a class method definition; value is the owning class name. */
    private String classOwner;

    /** Non-null at a call-site when this is a class method invocation; identifies the object instance. */
    private String objectHandleId;

    public FunctionCall() {
        setClazz(FunctionCall.class);
    }
}

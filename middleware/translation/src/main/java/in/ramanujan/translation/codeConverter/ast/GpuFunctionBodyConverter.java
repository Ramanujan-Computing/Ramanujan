package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

/**
 * Converts the body of a {@code _GPU}-suffixed Python function into an OpenCL C kernel string,
 * and identifies which parameters carry global work sizes for {@code clEnqueueNDRangeKernel}.
 *
 * <h3>Function Signature Convention</h3>
 * <pre>
 * def funcName_GPU(arg1, arg2, ..., argN, rangeKernelDim1, rangeKernelDim2, ..., rangeKernelDimM, M)
 * </pre>
 * <ul>
 *   <li>{@code arg1..argN} – data arguments; become {@code __global float*} kernel parameters.</li>
 *   <li>{@code rangeKernelDim1..rangeKernelDimM} – range/index variables; detected by their
 *       appearance in subscript <em>slice</em> positions in the body (e.g., {@code a[rangeKernelDim1]}).
 *       In the kernel, each becomes {@code int rangeKernelDimK = get_global_id(K-1);}.</li>
 *   <li>{@code M} – the <strong>last</strong> parameter; an integer constant passed at call time
 *       that equals the number of NDRange dimensions ({@code work_dim}).  Its parameter index is
 *       stored in {@link GpuConversionResult#gpuWorkDimArgIndex}.  It is excluded from the kernel
 *       signature and from {@code parallelismArgIndices}.</li>
 * </ul>
 *
 * <h3>Calling Convention</h3>
 * <pre>
 * funcName_GPU(arg1val, arg2val, ..., rangeDimSize1, ..., rangeDimSizeM, M)
 * </pre>
 * At call time, the values at {@link GpuConversionResult#parallelismArgIndices} become the
 * {@code global_work_size[]} array, and the value at
 * {@link GpuConversionResult#gpuWorkDimArgIndex} becomes {@code work_dim}.
 *
 * <h3>Example – 1-D vector add</h3>
 * <pre>
 * def vector_add_GPU(a, b, c, gid, 1):
 *     c[gid] = a[gid] + b[gid]
 *
 * Generated kernel:
 *   __kernel void vector_add(__global float* a, __global float* b, __global float* c) {
 *       int gid = get_global_id(0);
 *       c[gid] = (a[gid] + b[gid]);
 *   }
 * parallelismArgIndices = [3]   (index of 'gid' parameter)
 * gpuWorkDimArgIndex    = 4     (index of M parameter)
 * </pre>
 *
 * <h3>Example – 2-D matrix kernel</h3>
 * <pre>
 * def matrix_mul_GPU(a, b, c, row, col, 2):
 *     c[row * N + col] = a[row * N + col] + b[row * N + col]
 *
 * Generated kernel:
 *   __kernel void matrix_mul(__global float* a, __global float* b, __global float* c) {
 *       int row = get_global_id(0);
 *       int col = get_global_id(1);
 *       c[((row * N) + col)] = (a[((row * N) + col)] + b[((row * N) + col)]);
 *   }
 * parallelismArgIndices = [3, 4]
 * gpuWorkDimArgIndex    = 5
 * </pre>
 */
public class GpuFunctionBodyConverter {

    private static final String GPU_SUFFIX_PREFIX = "_GPU_";

    // Per-conversion state – reset at the start of each convert() call.
    private Set<String> paramNames            = new HashSet<>();
    /**
     * Subset of paramNames that are float array parameters (__global float*).
     * Excludes range-dimension arguments (gid, etc.) which are integer.
     * Used by exprIsFloat to propagate float-ness through variable assignments.
     */
    private Set<String> floatParamNames       = new HashSet<>();
    private Set<String> declaredLocals        = new HashSet<>();
    /**
     * Local variables that must be declared {@code float} because they are ever
     * assigned from an expression containing an array read ({@link SubscriptNode})
     * or a floating-point literal.  All other locals are declared {@code int} so
     * that index arithmetic (e.g. {@code base = gid * 3072}) stays in the integer
     * domain and avoids float32 precision loss for large indices.
     */
    private Set<String> floatLocals           = new HashSet<>();
    /**
     * Name of the function currently being translated to OpenCL C.
     * Set during {@link #convert} and {@link #convertHelperFunction} to enable
     * self-recursion detection in {@link #convertExpr}.
     */
    private String currentGeneratingFuncName  = null;

    // =========================================================================
    //  Result type
    // =========================================================================

    /**
     * Holds the result of a GPU function conversion.
     */
    public static class GpuConversionResult {
        /** The complete OpenCL C kernel source. */
        public final String kernelCode;

        /**
         * Zero-based indices (into the original Python function's parameter list) of the
         * range-kernel-dimension arguments.  At call time, the values at these positions are
         * passed as the {@code global_work_size[]} array to {@code clEnqueueNDRangeKernel}.
         * The length of this list equals {@code work_dim}.
         */
        public final List<Integer> parallelismArgIndices;

        /**
         * Always {@code -1}: the work_dim count is now encoded in the function name suffix
         * ({@code _GPU_N}) and equals {@code parallelismArgIndices.size()}.  Kept for
         * API compatibility only.
         */
        public final int gpuWorkDimArgIndex;

        public GpuConversionResult(String kernelCode, List<Integer> parallelismArgIndices, int gpuWorkDimArgIndex) {
            this.kernelCode = kernelCode;
            this.parallelismArgIndices = Collections.unmodifiableList(parallelismArgIndices);
            this.gpuWorkDimArgIndex = gpuWorkDimArgIndex;
        }
    }

    // =========================================================================
    //  Public API
    // =========================================================================

    /**
     * Converts a {@code _GPU_N} {@link FunctionDefNode} to a {@link GpuConversionResult}.
     * Equivalent to {@code convert(funcDef, Collections.emptyMap())}.
     */
    public GpuConversionResult convert(FunctionDefNode funcDef) {
        return convert(funcDef, Collections.emptyMap());
    }

    /**
     * Converts a {@code _GPU_N} {@link FunctionDefNode} to a {@link GpuConversionResult},
     * optionally prepending OpenCL C device functions for each entry in
     * {@code helperFunctions}.
     *
     * <h3>Helper (device) functions</h3>
     * <p>Any non-GPU Python function in {@code helperFunctions} is emitted as a regular
     * OpenCL C function ({@code float name(float p1, float p2, ...)}) placed before the
     * {@code __kernel} declaration so that it is visible to the kernel body.</p>
     *
     * <h3>Restrictions</h3>
     * <ul>
     *   <li>A function may <strong>not</strong> call itself (direct recursion).  An
     *       {@link IllegalArgumentException} is thrown during translation if a recursive
     *       call is detected.</li>
     *   <li>GPU kernel functions (those matching {@code .*_GPU_\d+$}) may not be called
     *       from within kernel or device-function code.</li>
     *   <li>All helper-function parameters are treated as {@code float} scalars.
     *       Array pointers are not supported in helper functions.</li>
     * </ul>
     *
     * @param funcDef         the {@code _GPU_N} function definition to convert
     * @param helperFunctions map of name → {@link FunctionDefNode} for every non-GPU
     *                        helper function that the kernel may call; may be empty
     */
    public GpuConversionResult convert(FunctionDefNode funcDef,
                                        Map<String, FunctionDefNode> helperFunctions) {
        List<ArgNode> allArgs = funcDef.getArgs().getArgs();

        if (allArgs.isEmpty()) {
            throw new IllegalArgumentException(
                    "GPU function '" + funcDef.getName() + "' must have at least one parameter.");
        }

        // Track the kernel name so convertExpr can detect self-recursion.
        currentGeneratingFuncName = funcDef.getName();

        // Parallelism count (= work_dim) is encoded in the function name suffix.
        // E.g. "vector_add_GPU_1" → 1 dimension, "matrix_mul_GPU_2" → 2 dimensions.
        // There is NO dedicated M parameter in the argument list any more.
        int parallelismArgSize = extractParallelismSize(funcDef.getName());
        int gpuWorkDimArgIndex = -1;  // no longer a parameter; kept for API compatibility

        // Classify params:
        //   first (allArgs.size() - parallelismArgSize) params → data args  → __global float*
        //   last  parallelismArgSize params                    → range dims → get_global_id(k)
        List<ArgNode> dataArgs              = new ArrayList<>();
        List<ArgNode> rangeDimArgs          = new ArrayList<>();
        List<Integer> parallelismArgIndices = new ArrayList<>();

        int rangeStart = allArgs.size() - parallelismArgSize;
        if (rangeStart < 0) {
            throw new IllegalArgumentException(
                    "GPU function '" + funcDef.getName() + "' declares " + parallelismArgSize
                    + " range dimensions but only has " + allArgs.size() + " parameters.");
        }
        for (int i = 0; i < rangeStart; i++) {
            dataArgs.add(allArgs.get(i));
        }
        for (int i = rangeStart; i < allArgs.size(); i++) {
            rangeDimArgs.add(allArgs.get(i));
            parallelismArgIndices.add(i);
        }

        // All parameter names (data args + range dims) – used to distinguish locals.
        paramNames = new HashSet<>();
        for (ArgNode arg : allArgs) paramNames.add(arg.getArg());
        // Double-typed params: only data args (__global double*), NOT range-dim args (int gid).
        floatParamNames = new HashSet<>();
        for (ArgNode arg : dataArgs) floatParamNames.add(arg.getArg());
        declaredLocals = new HashSet<>();

        // Pre-scan: determine which locals must be double vs int.
        // A local is double if it is EVER assigned from an expression that contains an
        // array read (SubscriptNode) or a floating-point constant.  All other locals
        // are declared int, keeping index arithmetic in the integer domain so that
        // large indices (e.g. gid*3072 for gid up to 9215) are computed exactly.
        floatLocals = new HashSet<>();
        collectFloatLocalNamesFromStmts(funcDef.getBody(), floatLocals);

        // Build kernel source.
        String rawName    = funcDef.getName();
        String kernelName = extractKernelName(rawName);

        StringBuilder sb = new StringBuilder();

        // --- Emit helper device functions first (must precede the __kernel declaration) ---
        // Only emit helpers that are actually called from the kernel body.
        Set<String> calledNames = collectCalledFunctionNames(funcDef.getBody());
        for (FunctionDefNode helper : helperFunctions.values()) {
            // Skip GPU kernel functions – they cannot be used as device functions.
            if (helper.getName().matches(".*_GPU_\\d+$")) continue;
            // Only emit if the kernel body actually calls this helper.
            if (!calledNames.contains(helper.getName())) continue;
            sb.append(convertHelperFunction(helper));
        }

        // Kernel signature: data args only, each as __global float*.
        // Apple Metal-based OpenCL does not support cl_khr_fp64 (double precision on GPU).
        // The CPU stores arrays as double; conversion happens at the JNI/native boundary.
        sb.append("__kernel void ").append(kernelName).append("(");
        for (int i = 0; i < dataArgs.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("__global float* ").append(dataArgs.get(i).getArg());
        }
        sb.append(") {\n");

        // Range-dimension declarations: int rangeKernelDimK = get_global_id(K);
        for (int d = 0; d < rangeDimArgs.size(); d++) {
            sb.append("    int ").append(rangeDimArgs.get(d).getArg())
              .append(" = get_global_id(").append(d).append(");\n");
        }

        // Body statements.
        for (AstNode stmt : funcDef.getBody()) {
            String code = convertStatement(stmt, "    ");
            if (code != null && !code.isEmpty()) sb.append(code);
        }

        sb.append("}");

        return new GpuConversionResult(sb.toString(), parallelismArgIndices, gpuWorkDimArgIndex);
    }

    // =========================================================================
    //  Body analysis – find names used as subscript SLICE (index) positions
    // =========================================================================
    //  Statement conversion
    // =========================================================================

    private String convertStatement(AstNode stmt, String indent) {
        if (stmt instanceof AssignNode) {
            AssignNode assign = (AssignNode) stmt;
            if (assign.getTargets() == null || assign.getTargets().isEmpty()) return "";
            AstNode target = assign.getTargets().get(0);
            // Declare scalar locals with 'double' on their first assignment.
            // Parameters (data args + range dims) are already declared in the kernel
            // signature / via get_global_id() and must not be re-declared.
            String typePrefix = "";
            if (target instanceof NameNode) {
                String varName = ((NameNode) target).getId();
                if (!paramNames.contains(varName) && !declaredLocals.contains(varName)) {
                    // Use 'int' for pure index/counter variables to avoid precision
                    // loss when computing large array indices (e.g. gid*3072).
                    typePrefix = floatLocals.contains(varName) ? "float " : "int ";
                    declaredLocals.add(varName);
                }
            }
            return indent + typePrefix + convertExpr(target) + " = " + convertExpr(assign.getValue()) + ";\n";

        } else if (stmt instanceof AugAssignNode) {
            AugAssignNode aug = (AugAssignNode) stmt;
            return indent + convertExpr(aug.getTarget()) + " " + binOpToC(aug.getOp()) + "= " + convertExpr(aug.getValue()) + ";\n";

        } else if (stmt instanceof ExprNode) {
            // Handle in-place built-in functions: EXP(x) -> x = exp(x), LOG(x) -> x = log(x), SQRT(x) -> x = sqrt(x)
            AstNode exprVal = ((ExprNode) stmt).getValue();
            if (exprVal instanceof CallNode) {
                CallNode call = (CallNode) exprVal;
                String calledName = (call.getFunc() instanceof NameNode) ? ((NameNode) call.getFunc()).getId() : null;
                if (calledName != null && call.getArgs().size() == 1) {
                    String openclFunc = null;
                    if      ("EXP".equals(calledName))   openclFunc = "exp";
                    else if ("LOG".equals(calledName))   openclFunc = "log";
                    else if ("SQRT".equals(calledName))  openclFunc = "sqrt";
                    else if ("FLOOR".equals(calledName)) openclFunc = "floor";
                    if (openclFunc != null) {
                        String arg = convertExpr(call.getArgs().get(0));
                        return indent + arg + " = " + openclFunc + "(" + arg + ");\n";
                    }
                }
                // ATOMIC_ADD_F(arr, idx, delta) — atomic float-add via CAS loop (OpenCL 1.2).
                // No native atomic_add for float in OpenCL 1.2; use atomic_cmpxchg on the
                // int-reinterpreted bits.  Each invocation is wrapped in its own {} block so
                // multiple calls in the same kernel body don't produce duplicate declarations.
                if ("ATOMIC_ADD_F".equals(calledName) && call.getArgs().size() == 3) {
                    String arrExpr = convertExpr(call.getArgs().get(0));
                    String idxExpr = convertExpr(call.getArgs().get(1));
                    String valExpr = convertExpr(call.getArgs().get(2));
                    return indent + "{\n"
                         + indent + "    __global volatile int* _aAddr = (__global volatile int*)(&"
                         + arrExpr + "[(int)(" + idxExpr + ")]);\n"
                         + indent + "    int _aOld, _aNew;\n"
                         + indent + "    do {\n"
                         + indent + "        _aOld = *_aAddr;\n"
                         + indent + "        _aNew = as_int(as_float(_aOld) + (" + valExpr + "));\n"
                         + indent + "    } while (atomic_cmpxchg(_aAddr, _aOld, _aNew) != _aOld);\n"
                         + indent + "}\n";
                }
            }
            return indent + convertExpr(exprVal) + ";\n";

        } else if (stmt instanceof IfNode) {
            return convertIf((IfNode) stmt, indent);

        } else if (stmt instanceof WhileNode) {
            return convertWhile((WhileNode) stmt, indent);

        } else if (stmt instanceof ReturnNode) {
            ReturnNode ret = (ReturnNode) stmt;
            if (ret.getValue() != null) {
                return indent + "return " + convertExpr(ret.getValue()) + ";\n";
            } else {
                return indent + "return 0;\n";
            }
        }

        return indent + "/* unsupported statement: " + stmt.getClass().getSimpleName() + " */\n";
    }

    /**
     * Converts an {@link IfNode} (and its orelse chain) to a C {@code if / else if / else} block.
     * {@code indent} is the extra indentation prefix already applied by the caller; nested
     * statements get an additional four-space indent.
     */
    private String convertIf(IfNode ifNode, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("if (").append(convertExpr(ifNode.getTest())).append(") {\n");
        if (ifNode.getBody() != null) {
            for (AstNode s : ifNode.getBody()) {
                String code = convertStatement(s, indent + "    ");
                if (code != null && !code.isEmpty()) sb.append(code);
            }
        }
        sb.append(indent).append("}");

        List<AstNode> orelse = ifNode.getOrelse();
        if (orelse != null && !orelse.isEmpty()) {
            if (orelse.size() == 1 && orelse.get(0) instanceof IfNode) {
                // elif chain: the recursive call prepends `indent`; strip it so the
                // continuation appears on the same line as the closing brace.
                String elseIfBlock = convertIf((IfNode) orelse.get(0), indent);
                sb.append(" else ").append(elseIfBlock.substring(indent.length()));
            } else {
                sb.append(" else {\n");
                for (AstNode s : orelse) {
                    String code = convertStatement(s, indent + "    ");
                    if (code != null && !code.isEmpty()) sb.append(code);
                }
                sb.append(indent).append("}");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    /** Converts a {@link WhileNode} to a C {@code while} loop. */
    private String convertWhile(WhileNode whileNode, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("while (").append(convertExpr(whileNode.getTest())).append(") {\n");
        if (whileNode.getBody() != null) {
            for (AstNode s : whileNode.getBody()) {
                String code = convertStatement(s, indent + "    ");
                if (code != null && !code.isEmpty()) sb.append(code);
            }
        }
        sb.append(indent).append("}\n");
        return sb.toString();
    }

    // =========================================================================
    //  Expression conversion
    // =========================================================================

    private String convertExpr(AstNode expr) {
        if (expr == null) return "0";

        if (expr instanceof ConstantNode) {
            return convertConstant((ConstantNode) expr);

        } else if (expr instanceof NameNode) {
            return ((NameNode) expr).getId();

        } else if (expr instanceof BinOpNode) {
            BinOpNode bin = (BinOpNode) expr;
            return "(" + convertExpr(bin.getLeft()) + " " + binOpToC(bin.getOp()) + " " + convertExpr(bin.getRight()) + ")";

        } else if (expr instanceof UnaryOpNode) {
            UnaryOpNode unary = (UnaryOpNode) expr;
            return "(" + unaryOpToC(unary.getOp()) + convertExpr(unary.getOperand()) + ")";

        } else if (expr instanceof SubscriptNode) {
            SubscriptNode sub = (SubscriptNode) expr;
            // Cast the index to int: all locals are declared float, but OpenCL C
            // requires integer array subscripts.  (int)(...) is safe for any
            // index whose value fits in a 32-bit integer, which is always true
            // for the array sizes used in Ramanujan programs.
            return convertExpr(sub.getValue()) + "[(int)(" + convertExpr(sub.getSlice()) + ")]";

        } else if (expr instanceof CompareNode) {
            CompareNode cmp = (CompareNode) expr;
            // Python allows chained comparisons; flatten to C's left-to-right &&.
            List<String>  ops  = cmp.getOps();
            List<AstNode> cmps = cmp.getComparators();
            if (ops == null || ops.isEmpty()) return convertExpr(cmp.getLeft());
            StringBuilder sb = new StringBuilder();
            AstNode prev = cmp.getLeft();
            for (int ci = 0; ci < ops.size(); ci++) {
                if (ci > 0) sb.append(" && ");
                sb.append("(").append(convertExpr(prev))
                  .append(" ").append(compareOpToC(ops.get(ci))).append(" ")
                  .append(convertExpr(cmps.get(ci))).append(")");
                prev = cmps.get(ci);
            }
            return sb.toString();

        } else if (expr instanceof CallNode) {
            CallNode call = (CallNode) expr;
            // Resolve the function name (only simple NameNode callees are supported).
            String calledName = null;
            if (call.getFunc() instanceof NameNode) {
                calledName = ((NameNode) call.getFunc()).getId();
            }
            if ("PACKED_NIBBLE".equals(calledName) && call.getArgs().size() == 2) {
                String packedExpr = convertExpr(call.getArgs().get(0));
                String nibbleExpr = convertExpr(call.getArgs().get(1));
                return "((float)(((uint)(" + packedExpr + ") >> "
                        + "((uint)(" + nibbleExpr + ") * 4u)) & 15u))";
            }
            // Guard: self-recursion is not permitted in GPU kernel or device-function code.
            if (calledName != null && calledName.equals(currentGeneratingFuncName)) {
                throw new IllegalArgumentException(
                        "Recursive call to '" + calledName + "' is not allowed in GPU kernel/"
                        + "device-function code.  Recursive GPU functions cannot be compiled.");
            }
            // Guard: calling another GPU kernel from inside kernel/device code is not supported.
            if (calledName != null && calledName.matches(".*_GPU_\\d+$")) {
                throw new IllegalArgumentException(
                        "Cannot call GPU kernel function '" + calledName
                        + "' from within GPU kernel/device-function code.");
            }
            // Emit a C function call expression.
            StringBuilder callSb = new StringBuilder();
            callSb.append(calledName != null ? calledName : "/*unknown_callee*/").append("(");
            List<AstNode> callArgs = call.getArgs();
            for (int i = 0; i < callArgs.size(); i++) {
                if (i > 0) callSb.append(", ");
                callSb.append(convertExpr(callArgs.get(i)));
            }
            callSb.append(")");
            return callSb.toString();
        }

        return "/* unknown: " + expr.getClass().getSimpleName() + " */";
    }

    // =========================================================================
    //  Helper device-function conversion
    // =========================================================================

    /**
     * Converts a non-GPU Python function into an OpenCL C device function string.
     *
     * <p>The generated function has the form:</p>
     * <pre>{@code
     * float funcName(float param1, float param2, ...) {
     *     // body
     * }
     * }</pre>
     *
     * <p>All parameters are treated as {@code float} scalars.  Local variables are
     * declared {@code float} on their first assignment.  {@code return} statements are
     * emitted verbatim.  The function may call other device helper functions but may
     * <strong>not</strong> call itself (self-recursion is detected and rejected).</p>
     *
     * @param helper the function definition to convert
     * @return the complete OpenCL C device function source, followed by two newlines
     * @throws IllegalArgumentException if a recursive self-call is found in the body
     */
    private String convertHelperFunction(FunctionDefNode helper) {
        // Save per-conversion state so helper conversion does not corrupt kernel conversion.
        Set<String> savedParamNames            = this.paramNames;
        Set<String> savedFloatParamNames       = this.floatParamNames;
        Set<String> savedDeclaredLocals        = this.declaredLocals;
        Set<String> savedFloatLocals           = this.floatLocals;
        String      savedGeneratingFuncName    = this.currentGeneratingFuncName;

        this.paramNames             = new HashSet<>();
        this.floatParamNames        = new HashSet<>();
        this.declaredLocals         = new HashSet<>();
        this.floatLocals            = new HashSet<>();
        // Temporarily set the current function name to the helper's name so that
        // convertExpr can detect self-recursion within the helper body.
        this.currentGeneratingFuncName = helper.getName();
        // Pre-scan the helper body for float locals.
        if (helper.getBody() != null) collectFloatLocalNamesFromStmts(helper.getBody(), this.floatLocals);

        List<ArgNode> args = helper.getArgs() != null ? helper.getArgs().getArgs()
                                                      : Collections.emptyList();
        for (ArgNode arg : args) {
            this.paramNames.add(arg.getArg());
            this.floatParamNames.add(arg.getArg());  // helper params are all float
        }

        StringBuilder sb = new StringBuilder();
        sb.append("float ").append(helper.getName()).append("(");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("float ").append(args.get(i).getArg());
        }
        sb.append(") {\n");

        if (helper.getBody() != null) {
            for (AstNode stmt : helper.getBody()) {
                String code = convertStatement(stmt, "    ");
                if (code != null && !code.isEmpty()) sb.append(code);
            }
        }

        sb.append("}\n\n");

        // Restore state.
        this.paramNames             = savedParamNames;
        this.floatParamNames        = savedFloatParamNames;
        this.declaredLocals         = savedDeclaredLocals;
        this.floatLocals            = savedFloatLocals;
        this.currentGeneratingFuncName = savedGeneratingFuncName;

        return sb.toString();
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    /**
     * Parses the trailing integer from a {@code _GPU_N}-suffixed function name.
     * Examples: {@code "vector_add_GPU_1"} → {@code 1}, {@code "matrix_mul_GPU_2"} → {@code 2}.
     */
    private int extractParallelismSize(String name) {
        int idx = name.lastIndexOf(GPU_SUFFIX_PREFIX);
        if (idx < 0) {
            throw new IllegalArgumentException(
                    "GPU function name '" + name + "' does not follow the '_GPU_N' convention "
                    + "(e.g. 'vector_add_GPU_1').");
        }
        String numStr = name.substring(idx + GPU_SUFFIX_PREFIX.length());
        try {
            return Integer.parseInt(numStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "GPU function name '" + name + "': trailing value '" + numStr
                    + "' is not a valid integer.");
        }
    }

    /**
     * Strips the {@code _GPU_N} suffix to obtain the OpenCL kernel name.
     * Example: {@code "vector_add_GPU_1"} → {@code "vector_add"}.
     */
    private String extractKernelName(String funcName) {
        int idx = funcName.lastIndexOf(GPU_SUFFIX_PREFIX);
        if (idx < 0) return funcName;
        return funcName.substring(0, idx);
    }

    private String convertConstant(ConstantNode node) {
        Object val = node.getValue();
        if (val == null)            return "0";
        if (val instanceof Boolean) return ((Boolean) val) ? "1" : "0";
        if (val instanceof Integer) return String.valueOf(val);
        if (val instanceof Long)    return val + "L";
        if (val instanceof Double)  return val + "f";
        if (val instanceof Float)   return val + "f";
        return "\"" + val + "\"";
    }

    private String compareOpToC(String op) {
        if (op == null) return "==";
        switch (op) {
            case "Eq":    return "==";
            case "NotEq": return "!=";
            case "Lt":    return "<";
            case "LtE":   return "<=";
            case "Gt":    return ">";
            case "GtE":   return ">=";
            default:      return "==";
        }
    }

    private String binOpToC(String op) {
        if (op == null) return "+";
        switch (op) {
            case "Add":      return "+";
            case "Sub":      return "-";
            case "Mult":     return "*";
            case "Div":      return "/";
            case "FloorDiv": return "/";
            case "Mod":      return "%";
            case "Pow":      return "/*pow*/";
            case "BitAnd":   return "&";
            case "BitOr":    return "|";
            case "BitXor":   return "^";
            case "LShift":   return "<<";
            case "RShift":   return ">>";
            default:         return "+";
        }
    }

    private String unaryOpToC(String op) {
        if (op == null) return "-";
        switch (op) {
            case "USub":   return "-";
            case "UAdd":   return "+";
            case "Invert": return "~";
            case "Not":    return "!";
            default:       return "-";
        }
    }

    // =========================================================================
    //  Float-local analysis
    // =========================================================================

    /**
     * Pre-scans {@code stmts} (recursing into while/if bodies) and adds to
     * {@code floatSet} the name of every local variable that is EVER assigned
     * from an expression containing an array subscript, a floating-point
     * constant, or a variable already known to be float.
     *
     * <p>Iterates to a fixpoint so that float-ness propagates through chains
     * like {@code gate_val = arr[i]; neg_gate = 0 - gate_val;} —
     * {@code neg_gate} is also marked float even though its RHS has no direct
     * subscript.</p>
     */
    private void collectFloatLocalNamesFromStmts(List<AstNode> stmts, Set<String> floatSet) {
        if (stmts == null) return;
        // Iterate to a fixpoint so float-ness propagates through variable chains.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (AstNode stmt : flattenStmts(stmts)) {
                if (!(stmt instanceof AssignNode)) continue;
                AssignNode assign = (AssignNode) stmt;
                if (assign.getTargets() == null || assign.getTargets().isEmpty()) continue;
                AstNode target = assign.getTargets().get(0);
                if (!(target instanceof NameNode)) continue;
                String varName = ((NameNode) target).getId();
                if (!floatSet.contains(varName) && exprIsFloat(assign.getValue(), floatSet)) {
                    floatSet.add(varName);
                    changed = true;
                }
            }
        }
    }

    /** Flattens a nested block into a single list of all statements. */
    private List<AstNode> flattenStmts(List<AstNode> stmts) {
        List<AstNode> result = new ArrayList<>();
        if (stmts == null) return result;
        for (AstNode stmt : stmts) {
            result.add(stmt);
            if (stmt instanceof WhileNode) {
                result.addAll(flattenStmts(((WhileNode) stmt).getBody()));
            } else if (stmt instanceof IfNode) {
                IfNode ifNode = (IfNode) stmt;
                result.addAll(flattenStmts(ifNode.getBody()));
                result.addAll(flattenStmts(ifNode.getOrelse()));
            }
        }
        return result;
    }

    /**
     * Returns {@code true} if {@code expr} produces a {@code double} result.
     * An expression is double if it:
     * <ul>
     *   <li>is an array subscript ({@link SubscriptNode}),</li>
     *   <li>is a floating-point literal,</li>
     *   <li>is a {@link NameNode} whose name is already in {@code knownFloats}
     *       or in the data-arg parameter set (kernel {@code __global double*} args),</li>
     *   <li>is a binary/unary expression with at least one double operand,</li>
     *   <li>is a function call (helper calls return {@code double}).</li>
     * </ul>
     */
    private boolean exprIsFloat(AstNode expr, Set<String> knownFloats) {
        if (expr == null) return false;
        if (expr instanceof SubscriptNode) return true;
        if (expr instanceof ConstantNode) {
            Object val = ((ConstantNode) expr).getValue();
            return (val instanceof Double) || (val instanceof Float);
        }
        if (expr instanceof NameNode) {
            String name = ((NameNode) expr).getId();
            // data-arg parameters are __global double* — using them unsubscripted is unusual
            // but accessing by name alone (as a scalar) should be treated as double.
            return knownFloats.contains(name) || floatParamNames.contains(name);
        }
        if (expr instanceof BinOpNode) {
            BinOpNode bin = (BinOpNode) expr;
            return exprIsFloat(bin.getLeft(), knownFloats) ||
                   exprIsFloat(bin.getRight(), knownFloats);
        }
        if (expr instanceof UnaryOpNode) {
            return exprIsFloat(((UnaryOpNode) expr).getOperand(), knownFloats);
        }
        // CallNode results (e.g. helper function calls) are float by convention.
        if (expr instanceof CallNode) return true;
        return false;
    }

    // Keep the old name as a delegate for the helper-function path (uses empty knownFloats).
    @SuppressWarnings("unused")
    private boolean containsSubscriptOrFloat(AstNode expr) {
        return exprIsFloat(expr, floatLocals);
    }

    // =========================================================================
    //  Helper-function reference analysis
    // =========================================================================

    /**
     * Recursively walks a list of AST statements and collects the names of all
     * functions that are directly called (via {@link CallNode} with a
     * {@link NameNode} callee).  Used to filter the helper-function set so that
     * only actually-referenced helpers are emitted as device functions.
     */
    private Set<String> collectCalledFunctionNames(List<AstNode> stmts) {
        Set<String> names = new HashSet<>();
        if (stmts == null) return names;
        for (AstNode node : stmts) {
            collectCalledNamesFromNode(node, names);
        }
        return names;
    }

    private void collectCalledNamesFromNode(AstNode node, Set<String> names) {
        if (node == null) return;

        if (node instanceof CallNode) {
            CallNode call = (CallNode) node;
            if (call.getFunc() instanceof NameNode) {
                names.add(((NameNode) call.getFunc()).getId());
            }
            // Also recurse into the call's arguments (they may contain nested calls).
            for (AstNode arg : call.getArgs()) {
                collectCalledNamesFromNode(arg, names);
            }

        } else if (node instanceof IfNode) {
            IfNode ifNode = (IfNode) node;
            collectCalledNamesFromNode(ifNode.getTest(), names);
            if (ifNode.getBody() != null) {
                for (AstNode s : ifNode.getBody()) collectCalledNamesFromNode(s, names);
            }
            if (ifNode.getOrelse() != null) {
                for (AstNode s : ifNode.getOrelse()) collectCalledNamesFromNode(s, names);
            }

        } else if (node instanceof WhileNode) {
            WhileNode w = (WhileNode) node;
            collectCalledNamesFromNode(w.getTest(), names);
            if (w.getBody() != null) {
                for (AstNode s : w.getBody()) collectCalledNamesFromNode(s, names);
            }

        } else if (node instanceof AssignNode) {
            AssignNode assign = (AssignNode) node;
            if (assign.getTargets() != null) {
                for (AstNode t : assign.getTargets()) collectCalledNamesFromNode(t, names);
            }
            collectCalledNamesFromNode(assign.getValue(), names);

        } else if (node instanceof AugAssignNode) {
            AugAssignNode aug = (AugAssignNode) node;
            collectCalledNamesFromNode(aug.getTarget(), names);
            collectCalledNamesFromNode(aug.getValue(), names);

        } else if (node instanceof BinOpNode) {
            BinOpNode bin = (BinOpNode) node;
            collectCalledNamesFromNode(bin.getLeft(), names);
            collectCalledNamesFromNode(bin.getRight(), names);

        } else if (node instanceof UnaryOpNode) {
            collectCalledNamesFromNode(((UnaryOpNode) node).getOperand(), names);

        } else if (node instanceof CompareNode) {
            CompareNode cmp = (CompareNode) node;
            collectCalledNamesFromNode(cmp.getLeft(), names);
            if (cmp.getComparators() != null) {
                for (AstNode c : cmp.getComparators()) collectCalledNamesFromNode(c, names);
            }

        } else if (node instanceof SubscriptNode) {
            SubscriptNode sub = (SubscriptNode) node;
            collectCalledNamesFromNode(sub.getValue(), names);
            collectCalledNamesFromNode(sub.getSlice(), names);

        } else if (node instanceof ExprNode) {
            collectCalledNamesFromNode(((ExprNode) node).getValue(), names);

        } else if (node instanceof ReturnNode) {
            collectCalledNamesFromNode(((ReturnNode) node).getValue(), names);
        }
        // NameNode, ConstantNode, etc. – leaf nodes, nothing to recurse into.
    }
}

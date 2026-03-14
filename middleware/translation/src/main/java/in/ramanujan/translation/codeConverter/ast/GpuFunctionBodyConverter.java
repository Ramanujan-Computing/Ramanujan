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

    private static final String GPU_SUFFIX = "_GPU";

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
         * Zero-based index of the {@code M} parameter in the original parameter list.
         * {@code M} is always the last parameter and its value at call time equals
         * {@code work_dim} (i.e., {@code parallelismArgIndices.size()}).
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
     * Converts a {@code _GPU} {@link FunctionDefNode} to a {@link GpuConversionResult}.
     *
     * <p>The <strong>last parameter</strong> is always treated as {@code M} (the work_dim count
     * passed to {@code clEnqueueNDRangeKernel}).  Parameters that appear as subscript indices
     * in the body (e.g., {@code a[gid]}) are treated as range-kernel-dimension arguments and
     * receive a {@code int dim = get_global_id(n);} declaration in the kernel.  All remaining
     * parameters (excluding M) become {@code __global float*} data arguments.</p>
     */
    public GpuConversionResult convert(FunctionDefNode funcDef) {
        List<ArgNode> allArgs = funcDef.getArgs().getArgs();

        if (allArgs.isEmpty()) {
            throw new IllegalArgumentException(
                    "GPU function '" + funcDef.getName() + "' must have at least one parameter (M).");
        }

        // Last param is M – the work_dim count.  Excluded from kernel signature.
        int gpuWorkDimArgIndex = allArgs.size() - 1;

        // Scan body to find names used as subscript indices (→ range dim args).
        Set<String> sliceNames = collectSliceNames(funcDef.getBody());

        // Classify params (all except M).
        List<ArgNode> dataArgs             = new ArrayList<>();
        List<ArgNode> rangeDimArgs         = new ArrayList<>();
        List<Integer> parallelismArgIndices = new ArrayList<>();

        for (int i = 0; i < allArgs.size() - 1; i++) {   // exclude M (last)
            ArgNode arg = allArgs.get(i);
            if (sliceNames.contains(arg.getArg())) {
                rangeDimArgs.add(arg);
                parallelismArgIndices.add(i);
            } else {
                dataArgs.add(arg);
            }
        }

        // Build kernel source.
        String rawName    = funcDef.getName();
        String kernelName = rawName.substring(0, rawName.length() - GPU_SUFFIX.length());

        StringBuilder sb = new StringBuilder();

        // Kernel signature: data args only, each as __global float*.
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
            String line = convertStatement(stmt);
            if (line != null && !line.isEmpty()) {
                sb.append("    ").append(line).append("\n");
            }
        }

        sb.append("}");

        return new GpuConversionResult(sb.toString(), parallelismArgIndices, gpuWorkDimArgIndex);
    }

    // =========================================================================
    //  Body analysis – find names used as subscript SLICE (index) positions
    // =========================================================================

    /**
     * Returns the set of all identifier names that appear anywhere inside a subscript's
     * slice expression in the function body (e.g., {@code a[x]} → "x" is a slice name).
     */
    private Set<String> collectSliceNames(List<AstNode> body) {
        Set<String> names = new HashSet<>();
        for (AstNode stmt : body) {
            collectSliceNamesFromNode(stmt, names);
        }
        return names;
    }

    /** Walk the AST; when a SubscriptNode is found, collect all name IDs from its slice. */
    private void collectSliceNamesFromNode(AstNode node, Set<String> names) {
        if (node == null) return;

        if (node instanceof SubscriptNode) {
            SubscriptNode sub = (SubscriptNode) node;
            // All names in the slice expression are candidate range dims.
            collectAllNamesInExpr(sub.getSlice(), names);
            // Recurse into the base (handles nested subscripts like a[b[i]]).
            collectSliceNamesFromNode(sub.getValue(), names);

        } else if (node instanceof AssignNode) {
            AssignNode a = (AssignNode) node;
            for (AstNode t : a.getTargets()) collectSliceNamesFromNode(t, names);
            collectSliceNamesFromNode(a.getValue(), names);

        } else if (node instanceof AugAssignNode) {
            AugAssignNode a = (AugAssignNode) node;
            collectSliceNamesFromNode(a.getTarget(), names);
            collectSliceNamesFromNode(a.getValue(), names);

        } else if (node instanceof BinOpNode) {
            BinOpNode b = (BinOpNode) node;
            collectSliceNamesFromNode(b.getLeft(), names);
            collectSliceNamesFromNode(b.getRight(), names);

        } else if (node instanceof UnaryOpNode) {
            collectSliceNamesFromNode(((UnaryOpNode) node).getOperand(), names);

        } else if (node instanceof ExprNode) {
            collectSliceNamesFromNode(((ExprNode) node).getValue(), names);

        } else if (node instanceof IfNode) {
            IfNode ifNode = (IfNode) node;
            collectSliceNamesFromNode(ifNode.getTest(), names);
            if (ifNode.getBody() != null)
                for (AstNode s : ifNode.getBody())   collectSliceNamesFromNode(s, names);
            if (ifNode.getOrelse() != null)
                for (AstNode s : ifNode.getOrelse()) collectSliceNamesFromNode(s, names);

        } else if (node instanceof WhileNode) {
            WhileNode whileNode = (WhileNode) node;
            collectSliceNamesFromNode(whileNode.getTest(), names);
            if (whileNode.getBody() != null)
                for (AstNode s : whileNode.getBody())   collectSliceNamesFromNode(s, names);
            if (whileNode.getOrelse() != null)
                for (AstNode s : whileNode.getOrelse()) collectSliceNamesFromNode(s, names);

        } else if (node instanceof CompareNode) {
            CompareNode cmp = (CompareNode) node;
            collectSliceNamesFromNode(cmp.getLeft(), names);
            if (cmp.getComparators() != null)
                for (AstNode c : cmp.getComparators()) collectSliceNamesFromNode(c, names);
        }
    }

    /** Recursively collect every NameNode ID that appears in {@code node} (for slice analysis). */
    private void collectAllNamesInExpr(AstNode node, Set<String> names) {
        if (node == null) return;

        if (node instanceof NameNode) {
            names.add(((NameNode) node).getId());
        } else if (node instanceof BinOpNode) {
            BinOpNode b = (BinOpNode) node;
            collectAllNamesInExpr(b.getLeft(), names);
            collectAllNamesInExpr(b.getRight(), names);
        } else if (node instanceof UnaryOpNode) {
            collectAllNamesInExpr(((UnaryOpNode) node).getOperand(), names);
        } else if (node instanceof SubscriptNode) {
            collectAllNamesInExpr(((SubscriptNode) node).getSlice(), names);
        } else if (node instanceof CompareNode) {
            CompareNode cmp = (CompareNode) node;
            collectAllNamesInExpr(cmp.getLeft(), names);
            if (cmp.getComparators() != null)
                for (AstNode c : cmp.getComparators()) collectAllNamesInExpr(c, names);
        }
    }

    // =========================================================================
    //  Statement conversion
    // =========================================================================

    private String convertStatement(AstNode stmt) {
        if (stmt instanceof AssignNode) {
            AssignNode assign = (AssignNode) stmt;
            if (assign.getTargets() == null || assign.getTargets().isEmpty()) return null;
            return convertExpr(assign.getTargets().get(0)) + " = " + convertExpr(assign.getValue()) + ";";

        } else if (stmt instanceof AugAssignNode) {
            AugAssignNode aug = (AugAssignNode) stmt;
            return convertExpr(aug.getTarget()) + " " + binOpToC(aug.getOp()) + "= " + convertExpr(aug.getValue()) + ";";

        } else if (stmt instanceof ExprNode) {
            return convertExpr(((ExprNode) stmt).getValue()) + ";";

        } else if (stmt instanceof IfNode) {
            return convertIf((IfNode) stmt, "");

        } else if (stmt instanceof WhileNode) {
            return convertWhile((WhileNode) stmt);
        }

        return "/* unsupported statement: " + stmt.getClass().getSimpleName() + " */";
    }

    /**
     * Converts an {@link IfNode} (and its orelse chain) to a C {@code if / else if / else} block.
     * {@code indent} is the extra indentation prefix already applied by the caller; nested
     * statements get an additional four-space indent.
     */
    private String convertIf(IfNode ifNode, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("if (").append(convertExpr(ifNode.getTest())).append(") {\n");
        if (ifNode.getBody() != null) {
            for (AstNode s : ifNode.getBody()) {
                String line = convertStatement(s);
                if (line != null && !line.isEmpty())
                    sb.append(indent).append("    ").append(line).append("\n");
            }
        }
        sb.append(indent).append("}");

        List<AstNode> orelse = ifNode.getOrelse();
        if (orelse != null && !orelse.isEmpty()) {
            if (orelse.size() == 1 && orelse.get(0) instanceof IfNode) {
                // elif chain
                sb.append(" else ").append(convertIf((IfNode) orelse.get(0), indent));
            } else {
                sb.append(" else {\n");
                for (AstNode s : orelse) {
                    String line = convertStatement(s);
                    if (line != null && !line.isEmpty())
                        sb.append(indent).append("    ").append(line).append("\n");
                }
                sb.append(indent).append("}");
            }
        }
        return sb.toString();
    }

    /** Converts a {@link WhileNode} to a C {@code while} loop. */
    private String convertWhile(WhileNode whileNode) {
        StringBuilder sb = new StringBuilder();
        sb.append("while (").append(convertExpr(whileNode.getTest())).append(") {\n");
        if (whileNode.getBody() != null) {
            for (AstNode s : whileNode.getBody()) {
                String line = convertStatement(s);
                if (line != null && !line.isEmpty())
                    sb.append("    ").append(line).append("\n");
            }
        }
        sb.append("}");
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
            return convertExpr(sub.getValue()) + "[" + convertExpr(sub.getSlice()) + "]";

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
        }

        return "/* unknown: " + expr.getClass().getSimpleName() + " */";
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

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
}

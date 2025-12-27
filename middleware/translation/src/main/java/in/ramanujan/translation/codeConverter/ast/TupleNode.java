package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

/**
 * Represents a tuple literal in Python.
 *
 * <p>TupleNode represents tuple literals created using commas with optional parentheses.
 * Tuples are immutable, ordered sequences that can contain elements of any type.</p>
 *
 * <h3>Python Examples</h3>
 * <pre>
 * # Empty tuple
 * empty = ()
 *
 * # Tuple of constants
 * coords = (10, 20)
 *
 * # Single-element tuple (note trailing comma)
 * single = (42,)
 *
 * # Tuple without parentheses (in return)
 * def f():
 *     return a, b
 * </pre>
 *
 * <h3>AST Structure</h3>
 * <pre>
 * # (a, b)
 * Tuple(
 *   elts=[
 *     Name(id='a', ctx=Load()),
 *     Name(id='b', ctx=Load())],
 *   ctx=Load())
 * </pre>
 */
 public class TupleNode extends AstNode {
     private List<AstNode> elts = new ArrayList<>();
     private String ctx;
 
     public List<AstNode> getElts() {
         return elts;
     }
 
     public void setElts(List<AstNode> elts) {
         this.elts = elts != null ? elts : new ArrayList<>();
     }
 
     public String getCtx() {
         return ctx;
     }
 
     public void setCtx(String ctx) {
         this.ctx = ctx;
     }
 
     @Override
     public String toString(int indent) {
         StringBuilder sb = new StringBuilder();
         sb.append(getIndent(indent)).append("Tuple(\n");
         sb.append(getIndent(indent + 1)).append("elts=[\n");
         for (int i = 0; i < elts.size(); i++) {
             AstNode elt = elts.get(i);
             if (elt != null) {
                 sb.append(elt.toString(indent + 2));
             } else {
                 sb.append(getIndent(indent + 2)).append("null");
             }
             if (i < elts.size() - 1) {
                 sb.append(",");
             }
             sb.append("\n");
         }
         sb.append(getIndent(indent + 1)).append("],\n");
         sb.append(getIndent(indent + 1)).append("ctx=").append(ctx).append("()\n");
         sb.append(getIndent(indent)).append(")");
         return sb.toString();
     }
 }

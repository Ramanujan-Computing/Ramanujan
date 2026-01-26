package in.ramanujan.translation.codeConverter.ast;

public abstract class AstNode {
    protected int lineno;
    protected int colOffset;
    
    public int getLineno() { 
        return lineno; 
    }
    
    public void setLineno(int lineno) { 
        this.lineno = lineno; 
    }
    
    public int getColOffset() { 
        return colOffset; 
    }
    
    public void setColOffset(int colOffset) { 
        this.colOffset = colOffset; 
    }
    
    /**
     * Returns a string representation of this AST node with full structural details.
     * This method should be overridden by subclasses to provide detailed information.
     */
    @Override
    public String toString() {
        return toString(0);
    }
    
    /**
     * Returns a string representation of this AST node with indentation for nested structures.
     * @param indent The indentation level (number of spaces)
     * @return Formatted string representation
     */
    public String toString(int indent) {
        return getIndent(indent) + getClass().getSimpleName() + "()";
    }
    
    /**
     * Helper method to create indentation string.
     * @param indent The indentation level
     * @return String of spaces for indentation
     */
    protected String getIndent(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }
}

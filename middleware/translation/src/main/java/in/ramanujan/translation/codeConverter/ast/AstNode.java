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
}

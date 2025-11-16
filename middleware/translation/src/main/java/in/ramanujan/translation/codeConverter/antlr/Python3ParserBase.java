package in.ramanujan.translation.codeConverter.antlr;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

/**
 * Base class for Python3Parser providing common functionality.
 */
public abstract class Python3ParserBase extends Parser {
    protected Python3ParserBase(TokenStream input) {
        super(input);
    }
    
    protected boolean checkPreviousTokenText(String text) {
        return checkPreviousTokenText(text, 1);
    }
    
    protected boolean checkPreviousTokenText(String text, int offset) {
        Token token = _input.LT(-offset);
        return token != null && text.equals(token.getText());
    }
    
    protected boolean CannotBePlusMinus() {
        return true; // Placeholder implementation
    }
    
    protected boolean CannotBeDotLpEq() {
        return true; // Placeholder implementation
    }
}
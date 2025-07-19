package in.ramanujan.translation.codeConverter.antlr;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.CodeConverterLogicFactory;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;
import in.ramanujan.translation.codeConverter.utils.StringUtils;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.util.List;
import java.util.Map;

/**
 * Enhanced CodeConverter that supports both the original Ramanujan syntax and Python syntax.
 * This class acts as a bridge between the old parsing logic and the new ANTLR-based Python parsing.
 */
public class PythonAwareCodeConverter extends CodeConverter {
    
    private boolean usePythonSyntax = false;
    
    public PythonAwareCodeConverter(CodeConverterLogicFactory codeConverterLogicFactory, StringUtils stringUtils) {
        super(codeConverterLogicFactory, stringUtils);
    }
    
    /**
     * Enable or disable Python syntax parsing
     */
    public void setUsePythonSyntax(boolean usePythonSyntax) {
        this.usePythonSyntax = usePythonSyntax;
    }
    
    /**
     * Check if the code appears to be Python syntax
     */
    public boolean isPythonCode(String code) {
        // Simple heuristics to detect Python syntax
        // Look for Python-specific patterns
        
        // Strong indicators of Ramanujan syntax
        if (code.contains("{") && code.contains("}") && code.contains("=")) {
            return false; // Likely Ramanujan variable access
        }
        if (code.startsWith("var ") && code.contains(":") && !code.contains("def")) {
            return false; // Ramanujan variable declaration
        }
        if (code.contains("exec ")) {
            return false; // Ramanujan function execution
        }
        
        // Strong indicators of Python syntax
        if (code.contains("def ") && code.contains(":")) {
            return true; // Python function definition
        }
        if ((code.contains("if ") || code.contains("while ") || code.contains("for ")) && code.contains(":")) {
            return true; // Python control structures
        }
        if (code.contains("import ") || code.contains("from ")) {
            return true; // Python imports
        }
        
        // Default to Python if no clear Ramanujan indicators and contains assignment
        if (code.contains("=") && !code.contains("{") && !code.contains("}")) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public List<Command> interpret(String code, RuleEngineInput ruleEngineInput, List<String> variableScope,
                                   DebugLevelCodeCreator debugLevelCodeCreator, 
                                   Map<Integer, in.ramanujan.pojo.RuleEngineInputUnits> functionFrameVariableMap,
                                   Integer[] frameVariableCounterId) throws CompilationException {
        
        // Auto-detect syntax if not explicitly set
        boolean shouldUsePython = usePythonSyntax || isPythonCode(code);
        
        if (shouldUsePython) {
            return interpretPythonCode(code, ruleEngineInput, variableScope, debugLevelCodeCreator, 
                                     functionFrameVariableMap, frameVariableCounterId);
        } else {
            // Use the original Ramanujan parsing logic
            return super.interpret(code, ruleEngineInput, variableScope, debugLevelCodeCreator,
                                 functionFrameVariableMap, frameVariableCounterId);
        }
    }
    
    /**
     * Parse Python code using ANTLR and convert to Ramanujan intermediate representation
     */
    private List<Command> interpretPythonCode(String code, RuleEngineInput ruleEngineInput, List<String> variableScope,
                                            DebugLevelCodeCreator debugLevelCodeCreator,
                                            Map<Integer, in.ramanujan.pojo.RuleEngineInputUnits> functionFrameVariableMap,
                                            Integer[] frameVariableCounterId) throws CompilationException {
        
        try {
            // Create ANTLR input stream
            CharStream input = CharStreams.fromString(code);
            
            // Create lexer
            Python3Lexer lexer = new Python3Lexer(input);
            
            // Create token stream
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            
            // Create parser
            Python3Parser parser = new Python3Parser(tokens);
            
            // Parse starting from file_input rule (top-level Python code)
            Python3Parser.File_inputContext tree = parser.file_input();
            
            // Create our converter listener
            PythonToRamanujanConverter converter = new PythonToRamanujanConverter(
                ruleEngineInput, debugLevelCodeCreator, variableScope, 
                getVariableMap(), getArrayMap()
            );
            
            // Walk the parse tree
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(converter, tree);
            
            return converter.getCommands();
            
        } catch (Exception e) {
            throw new CompilationException(0, 0, "Failed to parse Python code: " + e.getMessage());
        }
    }
}
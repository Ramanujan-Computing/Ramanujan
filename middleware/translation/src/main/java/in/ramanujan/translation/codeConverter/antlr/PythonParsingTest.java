package in.ramanujan.translation.codeConverter.antlr;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.ramanujan.translation.codeConverter.utils.StringUtils;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.util.*;

/**
 * Simple test to demonstrate Python code parsing capabilities
 */
public class PythonParsingTest {
    
    public static void main(String[] args) {
        System.out.println("Testing Python grammar parsing...");
        
        // Test Python code examples
        String[] pythonCodes = {
            "x = 10",
            "def hello():\n    print('Hello World')",
            "if x > 5:\n    y = x + 1",
            "while i < 10:\n    i = i + 1"
        };
        
        for (String code : pythonCodes) {
            System.out.println("\nTesting code: " + code.replace("\n", "\\n"));
            testPythonParsing(code);
        }
        
        // Test Ramanujan syntax detection
        System.out.println("\n\nTesting syntax detection:");
        testSyntaxDetection();
    }
    
    private static void testPythonParsing(String code) {
        try {
            // Create ANTLR input stream
            CharStream input = CharStreams.fromString(code);
            
            // Create lexer
            Python3Lexer lexer = new Python3Lexer(input);
            
            // Create token stream
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            
            // Create parser
            Python3Parser parser = new Python3Parser(tokens);
            
            // Parse starting from file_input rule
            Python3Parser.File_inputContext tree = parser.file_input();
            
            System.out.println("  ✓ Parsing successful");
            System.out.println("  Tree: " + tree.toStringTree(parser));
            
        } catch (Exception e) {
            System.out.println("  ✗ Parsing failed: " + e.getMessage());
        }
    }
    
    private static void testSyntaxDetection() {
        PythonAwareCodeConverter converter = new PythonAwareCodeConverter(null, new StringUtils());
        
        String[] testCodes = {
            "x = 10",  // Python
            "def test(): pass",  // Python
            "{x}={10};",  // Ramanujan
            "var x:integer;",  // Ramanujan
            "if x > 5:",  // Python
            "if({x}<{5}) {}"  // Ramanujan
        };
        
        for (String code : testCodes) {
            boolean isPython = converter.isPythonCode(code);
            System.out.println("Code: '" + code + "' -> " + (isPython ? "Python" : "Ramanujan"));
        }
    }
}
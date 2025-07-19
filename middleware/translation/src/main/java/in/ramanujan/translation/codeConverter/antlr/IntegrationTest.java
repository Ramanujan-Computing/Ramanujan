package in.ramanujan.translation.codeConverter.antlr;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.translation.codeConverter.CodeConverterLogicFactory;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.ramanujan.translation.codeConverter.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Integration test to verify that the new Python-aware converter doesn't break existing Ramanujan syntax
 */
public class IntegrationTest {
    
    public static void main(String[] args) {
        System.out.println("Integration Test: Python-aware CodeConverter");
        System.out.println("============================================");
        
        // Test both syntaxes
        testRamanujanSyntax();
        testPythonSyntax();
        testMixedScenarios();
    }
    
    private static void testRamanujanSyntax() {
        System.out.println("\n1. Testing Ramanujan Syntax:");
        
        String[] ramanujanCodes = {
            "{x}={10};",
            "var x:integer;",
            "if({x}<{5}) {{y}={1};}",
            "while({i}<{10}) {{i}={{i}+{1}};}",
            "exec func(x,y);"
        };
        
        PythonAwareCodeConverter converter = new PythonAwareCodeConverter(null, new StringUtils());
        
        for (String code : ramanujanCodes) {
            boolean detectedAsPython = converter.isPythonCode(code);
            System.out.println("  Code: " + code);
            System.out.println("  Detected as: " + (detectedAsPython ? "Python" : "Ramanujan") + 
                             (detectedAsPython ? " ❌" : " ✅"));
        }
    }
    
    private static void testPythonSyntax() {
        System.out.println("\n2. Testing Python Syntax:");
        
        String[] pythonCodes = {
            "x = 10",
            "def func(a, b): pass",
            "if x > 5: y = 1",
            "while i < 10: i = i + 1",
            "import math"
        };
        
        PythonAwareCodeConverter converter = new PythonAwareCodeConverter(null, new StringUtils());
        
        for (String code : pythonCodes) {
            boolean detectedAsPython = converter.isPythonCode(code);
            System.out.println("  Code: " + code);
            System.out.println("  Detected as: " + (detectedAsPython ? "Python" : "Ramanujan") + 
                             (detectedAsPython ? " ✅" : " ❌"));
        }
    }
    
    private static void testMixedScenarios() {
        System.out.println("\n3. Testing Mixed/Edge Cases:");
        
        String[] edgeCases = {
            "x=10",  // Could be either, should detect as Python due to lack of braces
            "",       // Empty code
            "# comment",  // Python comment
            "// comment", // Not typical for either language
            "var x = 10;" // Mixed style
        };
        
        PythonAwareCodeConverter converter = new PythonAwareCodeConverter(null, new StringUtils());
        
        for (String code : edgeCases) {
            if (code.isEmpty()) {
                System.out.println("  Code: <empty>");
            } else {
                System.out.println("  Code: " + code);
            }
            
            boolean detectedAsPython = converter.isPythonCode(code);
            System.out.println("  Detected as: " + (detectedAsPython ? "Python" : "Ramanujan"));
        }
    }
}
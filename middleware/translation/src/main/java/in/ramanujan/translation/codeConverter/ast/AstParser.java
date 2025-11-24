package in.ramanujan.translation.codeConverter.ast;

import in.ramanujan.translation.codeConverter.exception.CompilationException;
import java.util.*;
import java.util.regex.*;

public class AstParser {
    
    private String[] lines;
    private int currentLine;
    
    public ModuleNode parse(String astDump) throws CompilationException {
        lines = astDump.split("\n");
        currentLine = 0;
        
        // First line should be "Module("
        String firstLine = lines[0].trim();
        if (!firstLine.startsWith("Module(")) {
            throw new CompilationException(null, null, 
                "Expected Module at root, got: " + firstLine);
        }
        
        ModuleNode module = new ModuleNode();
        currentLine = 1; // Skip "Module("
        
        // Parse body
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            if (line.startsWith("body=[")) {
                currentLine++;
                module.setBody(parseBodyList());
                break;
            }
            currentLine++;
        }
        
        return module;
    }
    
    private List<AstNode> parseBodyList() throws CompilationException {
        List<AstNode> body = new ArrayList<>();
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("]")) {
                currentLine++;
                break;
            }
            
            AstNode node = parseNode(line);
            if (node != null) {
                body.add(node);
            }
        }
        
        return body;
    }
    
    private AstNode parseNode(String line) throws CompilationException {
        line = line.trim();
        
        if (line.startsWith("Assign(")) {
            return parseAssign();
        } else if (line.startsWith("AugAssign(")) {
            return parseAugAssign();
        } else if (line.startsWith("If(")) {
            return parseIf();
        } else if (line.startsWith("While(")) {
            return parseWhile();
        } else if (line.startsWith("FunctionDef(")) {
            return parseFunctionDef();
        } else if (line.startsWith("Expr(")) {
            return parseExpr();
        } else if (line.startsWith("Return(")) {
            return parseReturn();
        }
        
        currentLine++;
        return null;
    }
    
    private AssignNode parseAssign() throws CompilationException {
        AssignNode assign = new AssignNode();
        currentLine++;
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("targets=[")) {
                currentLine++;
                assign.setTargets(parseTargetsList());
            } else if (line.startsWith("value=")) {
                assign.setValue(parseValue(line));
            } else if (line.equals(")") || line.startsWith("),")) {
                currentLine++;
                break;
            } else {
                currentLine++;
            }
        }
        
        return assign;
    }
    
    private List<AstNode> parseTargetsList() throws CompilationException {
        List<AstNode> targets = new ArrayList<>();
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("]")) {
                currentLine++;
                break;
            }
            
            if (line.startsWith("Name(")) {
                targets.add(parseName());
            } else if (line.startsWith("Subscript(")) {
                targets.add(parseSubscript());
            } else {
                currentLine++;
            }
        }
        
        return targets;
    }
    
    private NameNode parseName() throws CompilationException {
        NameNode name = new NameNode();
        String line = lines[currentLine].trim();
        
        // Extract id from "Name(id='varName', ctx=Store())"
        Pattern pattern = Pattern.compile("id='([^']+)'");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            name.setId(matcher.group(1));
        }
        
        if (line.contains("ctx=Store()")) {
            name.setCtx("Store");
        } else if (line.contains("ctx=Load()")) {
            name.setCtx("Load");
        }
        
        // Skip closing parenthesis if on same line
        if (!line.endsWith(")") && !line.endsWith("),")) {
            currentLine++;
            // Find closing paren
            while (currentLine < lines.length) {
                line = lines[currentLine].trim();
                if (line.equals(")") || line.startsWith("),")) {
                    break;
                }
                currentLine++;
            }
        }
        currentLine++;
        return name;
    }
    
    private AstNode parseValue(String line) throws CompilationException {
        if (line.contains("Constant(")) {
            return parseConstant(line);
        } else if (line.contains("BinOp(")) {
            currentLine++;
            return parseBinOp();
        } else if (line.contains("Name(")) {
            currentLine++;
            return parseName();
        } else if (line.contains("Call(")) {
            currentLine++;
            return parseCall();
        } else if (line.contains("List(")) {
            currentLine++;
            return parseList();
        } else if (line.contains("Subscript(")) {
            currentLine++;
            return parseSubscript();
        } else if (line.contains("Compare(")) {
            currentLine++;
            return parseCompare();
        }
        
        currentLine++;
        return null;
    }
    
    private ConstantNode parseConstant(String line) {
        ConstantNode constant = new ConstantNode();
        
        // Extract value from "Constant(value=123)" or "value=Constant(value=123)"
        Pattern pattern = Pattern.compile("value=([^),]+)");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            String valueStr = matcher.group(1).trim();
            try {
                if (valueStr.contains(".")) {
                    constant.setValue(Double.parseDouble(valueStr));
                } else {
                    constant.setValue(Integer.parseInt(valueStr));
                }
            } catch (NumberFormatException e) {
                // String value - remove quotes
                constant.setValue(valueStr.replaceAll("'", "").replaceAll("\"", ""));
            }
        }
        
        currentLine++;
        return constant;
    }
    
    private BinOpNode parseBinOp() throws CompilationException {
        BinOpNode binOp = new BinOpNode();
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("left=")) {
                binOp.setLeft(parseValue(line));
            } else if (line.startsWith("op=")) {
                binOp.setOp(extractOp(line));
                currentLine++;
            } else if (line.startsWith("right=")) {
                binOp.setRight(parseValue(line));
            } else if (line.equals(")") || line.startsWith("),")) {
                currentLine++;
                break;
            } else {
                currentLine++;
            }
        }
        
        return binOp;
    }
    
    private String extractOp(String line) {
        // Extract "Add()" -> "Add", "Sub()" -> "Sub", etc.
        Pattern pattern = Pattern.compile("op=([A-Za-z]+)\\(\\)");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    private CompareNode parseCompare() throws CompilationException {
        CompareNode compare = new CompareNode();
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("left=")) {
                compare.setLeft(parseValue(line));
            } else if (line.startsWith("ops=[")) {
                currentLine++;
                compare.setOps(parseOpsList());
            } else if (line.startsWith("comparators=[")) {
                currentLine++;
                compare.setComparators(parseComparatorsList());
            } else if (line.equals(")") || line.startsWith("),")) {
                currentLine++;
                break;
            } else {
                currentLine++;
            }
        }
        
        return compare;
    }
    
    private List<String> parseOpsList() {
        List<String> ops = new ArrayList<>();
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("]")) {
                currentLine++;
                break;
            }
            
            // Extract "Lt()" -> "Lt"
            Pattern pattern = Pattern.compile("([A-Za-z]+)\\(\\)");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                ops.add(matcher.group(1));
            }
            
            currentLine++;
        }
        
        return ops;
    }
    
    private List<AstNode> parseComparatorsList() throws CompilationException {
        List<AstNode> comparators = new ArrayList<>();
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("]")) {
                currentLine++;
                break;
            }
            
            if (line.startsWith("Name(")) {
                comparators.add(parseName());
            } else if (line.startsWith("Constant(")) {
                comparators.add(parseConstant(line));
            } else {
                currentLine++;
            }
        }
        
        return comparators;
    }
    
    private IfNode parseIf() throws CompilationException {
        IfNode ifNode = new IfNode();
        currentLine++;
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("test=")) {
                ifNode.setTest(parseValue(line));
            } else if (line.startsWith("body=[")) {
                currentLine++;
                ifNode.setBody(parseBodyList());
            } else if (line.startsWith("orelse=[")) {
                currentLine++;
                ifNode.setOrelse(parseBodyList());
            } else if (line.equals(")") || line.startsWith("),")) {
                currentLine++;
                break;
            } else {
                currentLine++;
            }
        }
        
        return ifNode;
    }
    
    private WhileNode parseWhile() throws CompilationException {
        WhileNode whileNode = new WhileNode();
        currentLine++;
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("test=")) {
                whileNode.setTest(parseValue(line));
            } else if (line.startsWith("body=[")) {
                currentLine++;
                whileNode.setBody(parseBodyList());
            } else if (line.startsWith("orelse=[")) {
                currentLine++;
                whileNode.setOrelse(parseBodyList());
            } else if (line.equals(")") || line.startsWith("),")) {
                currentLine++;
                break;
            } else {
                currentLine++;
            }
        }
        
        return whileNode;
    }
    
    private FunctionDefNode parseFunctionDef() throws CompilationException {
        FunctionDefNode funcDef = new FunctionDefNode();
        String line = lines[currentLine].trim();
        
        // Extract name
        Pattern namePattern = Pattern.compile("name='([^']+)'");
        Matcher nameMatcher = namePattern.matcher(line);
        if (nameMatcher.find()) {
            funcDef.setName(nameMatcher.group(1));
        }
        
        currentLine++;
        
        while (currentLine < lines.length) {
            line = lines[currentLine].trim();
            
            if (line.startsWith("args=arguments(")) {
                funcDef.setArgs(parseArguments());
            } else if (line.startsWith("body=[")) {
                currentLine++;
                funcDef.setBody(parseBodyList());
            } else if (line.equals(")") || line.startsWith("),")) {
                currentLine++;
                break;
            } else {
                currentLine++;
            }
        }
        
        return funcDef;
    }
    
    private ArgumentsNode parseArguments() {
        ArgumentsNode arguments = new ArgumentsNode();
        currentLine++;
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("args=[")) {
                currentLine++;
                arguments.setArgs(parseArgsList());
            } else if (line.equals(")") || line.startsWith("),")) {
                currentLine++;
                break;
            } else {
                currentLine++;
            }
        }
        
        return arguments;
    }
    
    private List<ArgNode> parseArgsList() {
        List<ArgNode> args = new ArrayList<>();
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("]")) {
                currentLine++;
                break;
            }
            
            if (line.startsWith("arg(arg='") || line.contains("arg(arg='")) {
                ArgNode arg = new ArgNode();
                Pattern pattern = Pattern.compile("arg='([^']+)'");
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    arg.setArg(matcher.group(1));
                }
                args.add(arg);
            }
            
            currentLine++;
        }
        
        return args;
    }
    
    private CallNode parseCall() throws CompilationException {
        CallNode call = new CallNode();
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("func=")) {
                call.setFunc(parseValue(line));
            } else if (line.startsWith("args=[")) {
                currentLine++;
                call.setArgs(parseCallArgsList());
            } else if (line.equals(")") || line.startsWith("),")) {
                currentLine++;
                break;
            } else {
                currentLine++;
            }
        }
        
        return call;
    }
    
    private List<AstNode> parseCallArgsList() throws CompilationException {
        List<AstNode> args = new ArrayList<>();
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("]")) {
                currentLine++;
                break;
            }
            
            if (line.startsWith("Name(") || line.startsWith("Constant(") || 
                line.startsWith("BinOp(") || line.startsWith("Subscript(")) {
                AstNode arg = parseNode(line);
                if (arg != null) {
                    args.add(arg);
                }
            } else {
                currentLine++;
            }
        }
        
        return args;
    }
    
    private SubscriptNode parseSubscript() throws CompilationException {
        SubscriptNode subscript = new SubscriptNode();
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("value=")) {
                subscript.setValue(parseValue(line));
            } else if (line.startsWith("slice=")) {
                subscript.setSlice(parseValue(line));
            } else if (line.contains("ctx=Store()")) {
                subscript.setCtx("Store");
                currentLine++;
            } else if (line.contains("ctx=Load()")) {
                subscript.setCtx("Load");
                currentLine++;
            } else if (line.equals(")") || line.startsWith("),")) {
                currentLine++;
                break;
            } else {
                currentLine++;
            }
        }
        
        return subscript;
    }
    
    private ListNode parseList() throws CompilationException {
        ListNode list = new ListNode();
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("elts=[")) {
                currentLine++;
                list.setElts(parseEltsList());
            } else if (line.contains("ctx=Load()")) {
                list.setCtx("Load");
                currentLine++;
            } else if (line.equals(")") || line.startsWith("),")) {
                currentLine++;
                break;
            } else {
                currentLine++;
            }
        }
        
        return list;
    }
    
    private List<AstNode> parseEltsList() throws CompilationException {
        List<AstNode> elts = new ArrayList<>();
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("]")) {
                currentLine++;
                break;
            }
            
            AstNode elt = parseNode(line);
            if (elt != null) {
                elts.add(elt);
            }
        }
        
        return elts;
    }
    
    private ExprNode parseExpr() throws CompilationException {
        ExprNode expr = new ExprNode();
        currentLine++;
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("value=")) {
                expr.setValue(parseValue(line));
            } else if (line.equals(")") || line.startsWith("),")) {
                currentLine++;
                break;
            } else {
                currentLine++;
            }
        }
        
        return expr;
    }
    
    private ReturnNode parseReturn() throws CompilationException {
        ReturnNode returnNode = new ReturnNode();
        currentLine++;
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("value=")) {
                returnNode.setValue(parseValue(line));
            } else if (line.equals(")") || line.startsWith("),")) {
                currentLine++;
                break;
            } else {
                currentLine++;
            }
        }
        
        return returnNode;
    }
    
    private AugAssignNode parseAugAssign() throws CompilationException {
        AugAssignNode augAssign = new AugAssignNode();
        currentLine++;
        
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            
            if (line.startsWith("target=")) {
                augAssign.setTarget(parseValue(line));
            } else if (line.startsWith("op=")) {
                augAssign.setOp(extractOp(line));
                currentLine++;
            } else if (line.startsWith("value=")) {
                augAssign.setValue(parseValue(line));
            } else if (line.equals(")") || line.startsWith("),")) {
                currentLine++;
                break;
            } else {
                currentLine++;
            }
        }
        
        return augAssign;
    }
}

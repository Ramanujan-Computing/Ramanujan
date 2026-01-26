package in.ramanujan.translation.codeConverter.ast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import java.util.*;

/**
 * Parses AST JSON produced by ast2json into Java AST node objects.
 * Uses Jackson to parse the JSON structure and recursively build AST nodes.
 */
public class JsonAstParser {
    
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Parse AST JSON string to a ModuleNode.
     * @param astJson JSON string as produced by ast2json.convert(ast.parse(code))
     * @return ModuleNode with fully populated AST tree
     * @throws CompilationException on parse errors
     */
    public ModuleNode parseJson(String astJson) throws CompilationException {
        try {
            JsonNode root = mapper.readTree(astJson);
            
            if (!root.has("_type") || !"Module".equals(root.get("_type").asText())) {
                throw new CompilationException(null, null, "Expected Module at root, got: " + 
                    (root.has("_type") ? root.get("_type").asText() : "unknown"));
            }
            
            return parseModule(root);
        } catch (Exception e) {
            throw new CompilationException(null, null, "Failed to parse AST JSON: " + e.getMessage());
        }
    }
    
    private ModuleNode parseModule(JsonNode node) throws CompilationException {
        ModuleNode module = new ModuleNode();
        
        if (node.has("body")) {
            JsonNode bodyArray = node.get("body");
            if (bodyArray.isArray()) {
                List<AstNode> body = new ArrayList<>();
                for (JsonNode stmt : bodyArray) {
                    AstNode astNode = parseNode(stmt);
                    if (astNode != null) {
                        body.add(astNode);
                    }
                }
                module.setBody(body);
            }
        }

        if (node.has("type_ignores")) {
            JsonNode tiArray = node.get("type_ignores");
            if (tiArray.isArray()) {
                List<TypeIgnoreNode> list = new ArrayList<>();
                for (JsonNode ti : tiArray) {
                    TypeIgnoreNode t = parseTypeIgnore(ti);
                    if (t != null) list.add(t);
                }
                module.setTypeIgnores(list);
            }
        }
        
        return module;
    }
    
    /**
     * Routes JSON node to appropriate parser based on _type field.
     */
    private AstNode parseNode(JsonNode node) throws CompilationException {
        if (node == null || !node.has("_type")) {
            return null;
        }
        
        String type = node.get("_type").asText();
        
        switch (type) {
            case "Assign":
                return parseAssign(node);
            case "AugAssign":
                return parseAugAssign(node);
            case "Expr":
                return parseExpr(node);
            case "If":
                return parseIf(node);
            case "While":
                return parseWhile(node);
            case "FunctionDef":
                return parseFunctionDef(node);
            case "Return":
                return parseReturn(node);
            case "Name":
                return parseName(node);
            case "Constant":
                return parseConstant(node);
            case "BinOp":
                return parseBinOp(node);
            case "Compare":
                return parseCompare(node);
            case "Call":
                return parseCall(node);
            case "Subscript":
                return parseSubscript(node);
            case "List":
                return parseList(node);
            case "Tuple":
                return parseTuple(node);
            case "Attribute":
                return parseAttribute(node);
            case "UnaryOp":
                return parseUnaryOp(node);
            case "BoolOp":
                return parseBoolOp(node);
            case "ListComp":
                return parseListComp(node);
            default:
                // Unknown node type - log and skip
                System.err.println("Warning: Unknown AST node type: " + type);
                return null;
        }
    }
    
    private AssignNode parseAssign(JsonNode node) throws CompilationException {
        AssignNode assign = new AssignNode();
        
        // Parse targets
        if (node.has("targets")) {
            JsonNode targetsArray = node.get("targets");
            List<AstNode> targets = new ArrayList<>();
            for (JsonNode target : targetsArray) {
                AstNode targetNode = parseNode(target);
                if (targetNode != null) {
                    targets.add(targetNode);
                }
            }
            assign.setTargets(targets);
        }
        
        // Parse value
        if (node.has("value")) {
            assign.setValue(parseNode(node.get("value")));
        }
        
        setLineInfo(assign, node);
        return assign;
    }
    
    private AugAssignNode parseAugAssign(JsonNode node) throws CompilationException {
        AugAssignNode augAssign = new AugAssignNode();
        
        if (node.has("target")) {
            augAssign.setTarget(parseNode(node.get("target")));
        }
        
        if (node.has("op")) {
            augAssign.setOp(extractOperator(node.get("op")));
        }
        
        if (node.has("value")) {
            augAssign.setValue(parseNode(node.get("value")));
        }
        
        setLineInfo(augAssign, node);
        return augAssign;
    }
    
    private ExprNode parseExpr(JsonNode node) throws CompilationException {
        ExprNode expr = new ExprNode();
        
        if (node.has("value")) {
            expr.setValue(parseNode(node.get("value")));
        }
        
        setLineInfo(expr, node);
        return expr;
    }
    
    private IfNode parseIf(JsonNode node) throws CompilationException {
        IfNode ifNode = new IfNode();
        
        if (node.has("test")) {
            ifNode.setTest(parseNode(node.get("test")));
        }
        
        if (node.has("body")) {
            ifNode.setBody(parseBody(node.get("body")));
        }
        
        if (node.has("orelse")) {
            ifNode.setOrelse(parseBody(node.get("orelse")));
        }
        
        setLineInfo(ifNode, node);
        return ifNode;
    }
    
    private WhileNode parseWhile(JsonNode node) throws CompilationException {
        WhileNode whileNode = new WhileNode();
        
        if (node.has("test")) {
            whileNode.setTest(parseNode(node.get("test")));
        }
        
        if (node.has("body")) {
            whileNode.setBody(parseBody(node.get("body")));
        }
        
        if (node.has("orelse")) {
            whileNode.setOrelse(parseBody(node.get("orelse")));
        }
        
        setLineInfo(whileNode, node);
        return whileNode;
    }
    
    private FunctionDefNode parseFunctionDef(JsonNode node) throws CompilationException {
        FunctionDefNode funcDef = new FunctionDefNode();
        
        if (node.has("name")) {
            funcDef.setName(node.get("name").asText());
        }
        
        if (node.has("args")) {
            funcDef.setArgs(parseArguments(node.get("args")));
        }
        
        if (node.has("body")) {
            funcDef.setBody(parseBody(node.get("body")));
        }
        
        setLineInfo(funcDef, node);
        return funcDef;
    }
    
    private ArgumentsNode parseArguments(JsonNode node) throws CompilationException {
        ArgumentsNode args = new ArgumentsNode();
        
        if (node.has("args")) {
            JsonNode argsArray = node.get("args");
            List<ArgNode> argList = new ArrayList<>();
            for (JsonNode arg : argsArray) {
                ArgNode argNode = parseArg(arg);
                if (argNode != null) {
                    argList.add(argNode);
                }
            }
            args.setArgs(argList);
        }
        
        return args;
    }
    
    private ArgNode parseArg(JsonNode node) throws CompilationException {
        ArgNode arg = new ArgNode();
        
        if (node.has("arg")) {
            arg.setArg(node.get("arg").asText());
        }
        
        // ArgNode doesn't extend AstNode, so skip setLineInfo
        return arg;
    }
    
    private ReturnNode parseReturn(JsonNode node) throws CompilationException {
        ReturnNode returnNode = new ReturnNode();
        
        if (node.has("value") && !node.get("value").isNull()) {
            returnNode.setValue(parseNode(node.get("value")));
        }
        
        setLineInfo(returnNode, node);
        return returnNode;
    }
    
    private NameNode parseName(JsonNode node) throws CompilationException {
        NameNode name = new NameNode();
        
        if (node.has("id")) {
            name.setId(node.get("id").asText());
        }
        
        if (node.has("ctx")) {
            name.setCtx(extractContext(node.get("ctx")));
        }
        
        setLineInfo(name, node);
        return name;
    }
    
    private ConstantNode parseConstant(JsonNode node) throws CompilationException {
        ConstantNode constant = new ConstantNode();
        
        if (node.has("value")) {
            JsonNode valueNode = node.get("value");
            Object value;
            
            if (valueNode.isNull()) {
                value = null;
            } else if (valueNode.isInt()) {
                value = valueNode.asInt();
            } else if (valueNode.isLong()) {
                value = valueNode.asLong();
            } else if (valueNode.isDouble()) {
                value = valueNode.asDouble();
            } else if (valueNode.isBoolean()) {
                value = valueNode.asBoolean();
            } else if (valueNode.isTextual()) {
                value = valueNode.asText();
            } else {
                value = valueNode.toString();
            }
            
            constant.setValue(value);
        }
        
        setLineInfo(constant, node);
        return constant;
    }
    
    private BinOpNode parseBinOp(JsonNode node) throws CompilationException {
        BinOpNode binOp = new BinOpNode();
        
        if (node.has("left")) {
            binOp.setLeft(parseNode(node.get("left")));
        }
        
        if (node.has("op")) {
            binOp.setOp(extractOperator(node.get("op")));
        }
        
        if (node.has("right")) {
            binOp.setRight(parseNode(node.get("right")));
        }
        
        setLineInfo(binOp, node);
        return binOp;
    }
    
    private CompareNode parseCompare(JsonNode node) throws CompilationException {
        CompareNode compare = new CompareNode();
        
        if (node.has("left")) {
            compare.setLeft(parseNode(node.get("left")));
        }
        
        if (node.has("ops")) {
            JsonNode opsArray = node.get("ops");
            List<String> ops = new ArrayList<>();
            for (JsonNode op : opsArray) {
                ops.add(extractOperator(op));
            }
            compare.setOps(ops);
        }
        
        if (node.has("comparators")) {
            JsonNode comparatorsArray = node.get("comparators");
            List<AstNode> comparators = new ArrayList<>();
            for (JsonNode comp : comparatorsArray) {
                AstNode compNode = parseNode(comp);
                if (compNode != null) {
                    comparators.add(compNode);
                }
            }
            compare.setComparators(comparators);
        }
        
        setLineInfo(compare, node);
        return compare;
    }
    
    private CallNode parseCall(JsonNode node) throws CompilationException {
        CallNode call = new CallNode();
        
        if (node.has("func")) {
            call.setFunc(parseNode(node.get("func")));
        }
        
        if (node.has("args")) {
            JsonNode argsArray = node.get("args");
            List<AstNode> args = new ArrayList<>();
            for (JsonNode arg : argsArray) {
                AstNode argNode = parseNode(arg);
                if (argNode != null) {
                    args.add(argNode);
                }
            }
            call.setArgs(args);
        }
        
        setLineInfo(call, node);
        return call;
    }
    
    private SubscriptNode parseSubscript(JsonNode node) throws CompilationException {
        SubscriptNode subscript = new SubscriptNode();
        
        if (node.has("value")) {
            subscript.setValue(parseNode(node.get("value")));
        }
        
        if (node.has("slice")) {
            subscript.setSlice(parseNode(node.get("slice")));
        }
        
        if (node.has("ctx")) {
            subscript.setCtx(extractContext(node.get("ctx")));
        }
        
        setLineInfo(subscript, node);
        return subscript;
    }
    
    private ListNode parseList(JsonNode node) throws CompilationException {
        ListNode list = new ListNode();
        
        if (node.has("elts")) {
            JsonNode eltsArray = node.get("elts");
            List<AstNode> elts = new ArrayList<>();
            for (JsonNode elt : eltsArray) {
                AstNode eltNode = parseNode(elt);
                if (eltNode != null) {
                    elts.add(eltNode);
                }
            }
            list.setElts(elts);
        }
        
        if (node.has("ctx")) {
            list.setCtx(extractContext(node.get("ctx")));
        }
        
        setLineInfo(list, node);
        return list;
    }

    private TupleNode parseTuple(JsonNode node) throws CompilationException {
        TupleNode tuple = new TupleNode();
        
        if (node.has("elts")) {
            JsonNode eltsArray = node.get("elts");
            List<AstNode> elts = new ArrayList<>();
            for (JsonNode elt : eltsArray) {
                AstNode eltNode = parseNode(elt);
                if (eltNode != null) {
                    elts.add(eltNode);
                }
            }
            tuple.setElts(elts);
        }
        
        if (node.has("ctx")) {
            tuple.setCtx(extractContext(node.get("ctx")));
        }
        
        setLineInfo(tuple, node);
        return tuple;
    }
    
    private AttributeNode parseAttribute(JsonNode node) throws CompilationException {
        AttributeNode attr = new AttributeNode();
        
        if (node.has("value")) {
            attr.setValue(parseNode(node.get("value")));
        }
        
        if (node.has("attr")) {
            attr.setAttr(node.get("attr").asText());
        }
        
        if (node.has("ctx")) {
            attr.setCtx(extractContext(node.get("ctx")));
        }
        
        setLineInfo(attr, node);
        return attr;
    }
    
    private UnaryOpNode parseUnaryOp(JsonNode node) throws CompilationException {
        UnaryOpNode unaryOp = new UnaryOpNode();
        
        if (node.has("op")) {
            unaryOp.setOp(extractOperator(node.get("op")));
        }
        
        if (node.has("operand")) {
            unaryOp.setOperand(parseNode(node.get("operand")));
        }
        
        setLineInfo(unaryOp, node);
        return unaryOp;
    }
    
    private BoolOpNode parseBoolOp(JsonNode node) throws CompilationException {
        BoolOpNode boolOp = new BoolOpNode();
        
        if (node.has("op")) {
            boolOp.setOp(extractOperator(node.get("op")));
        }
        
        if (node.has("values")) {
            JsonNode valuesArray = node.get("values");
            List<AstNode> values = new ArrayList<>();
            for (JsonNode val : valuesArray) {
                AstNode valNode = parseNode(val);
                if (valNode != null) {
                    values.add(valNode);
                }
            }
            boolOp.setValues(values);
        }
        
        setLineInfo(boolOp, node);
        return boolOp;
    }
    
    private ListCompNode parseListComp(JsonNode node) throws CompilationException {
        ListCompNode listComp = new ListCompNode();
        
        if (node.has("elt")) {
            listComp.setElt(parseNode(node.get("elt")));
        }
        
        if (node.has("generators")) {
            JsonNode generatorsArray = node.get("generators");
            List<ComprehensionNode> generators = new ArrayList<>();
            for (JsonNode gen : generatorsArray) {
                ComprehensionNode genNode = parseComprehension(gen);
                if (genNode != null) {
                    generators.add(genNode);
                }
            }
            listComp.setGenerators(generators);
        }
        
        setLineInfo(listComp, node);
        return listComp;
    }
    
    private ComprehensionNode parseComprehension(JsonNode node) throws CompilationException {
        ComprehensionNode comp = new ComprehensionNode();
        
        if (node.has("target")) {
            comp.setTarget(parseNode(node.get("target")));
        }
        
        if (node.has("iter")) {
            comp.setIter(parseNode(node.get("iter")));
        }
        
        if (node.has("ifs")) {
            JsonNode ifsArray = node.get("ifs");
            List<AstNode> ifs = new ArrayList<>();
            for (JsonNode ifNode : ifsArray) {
                AstNode ifAst = parseNode(ifNode);
                if (ifAst != null) {
                    ifs.add(ifAst);
                }
            }
            comp.setIfs(ifs);
        }
        
        return comp;
    }
    
    /**
     * Parse a body array (list of statements).
     */
    private List<AstNode> parseBody(JsonNode bodyArray) throws CompilationException {
        List<AstNode> body = new ArrayList<>();
        if (bodyArray != null && bodyArray.isArray()) {
            for (JsonNode stmt : bodyArray) {
                AstNode node = parseNode(stmt);
                if (node != null) {
                    body.add(node);
                }
            }
        }
        return body;
    }

    private TypeIgnoreNode parseTypeIgnore(JsonNode node) {
        if (node == null || !node.has("_type") || !"TypeIgnore".equals(node.get("_type").asText())) {
            return null;
        }
        TypeIgnoreNode ti = new TypeIgnoreNode();
        if (node.has("lineno")) {
            ti.setLineno(node.get("lineno").asInt());
        }
        if (node.has("tag") && !node.get("tag").isNull()) {
            ti.setTag(node.get("tag").asText());
        }
        return ti;
    }
    
    /**
     * Extract operator name from operator node.
     * Operator nodes have _type field like "Add", "Sub", "Mult", etc.
     */
    private String extractOperator(JsonNode opNode) {
        if (opNode != null && opNode.has("_type")) {
            return opNode.get("_type").asText();
        }
        return "Unknown";
    }
    
    /**
     * Extract context from context node.
     * Context nodes have _type field like "Load", "Store", "Del".
     */
    private String extractContext(JsonNode ctxNode) {
        if (ctxNode != null && ctxNode.has("_type")) {
            return ctxNode.get("_type").asText();
        }
        return "Load";
    }
    
    /**
     * Set line number and column offset if available.
     */
    private void setLineInfo(AstNode node, JsonNode jsonNode) {
        if (jsonNode.has("lineno")) {
            node.setLineno(jsonNode.get("lineno").asInt());
        }
        if (jsonNode.has("col_offset")) {
            node.setColOffset(jsonNode.get("col_offset").asInt());
        }
    }
}

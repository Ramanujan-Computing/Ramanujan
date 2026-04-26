package in.ramanujan.developer.console;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Console {
    public static void main(String[] args) throws IOException {
        // If no arguments, do nothing
        if(args.length == 0) {
            return;
        }
        // 'server' keyword: start persistent JVM server mode (must be checked FIRST)
        if ("server".equalsIgnoreCase(args[0])) {
            OperationType.execute_inline_server.getImplementation().execute(Arrays.asList(args));
            return;
        }
        // 'homelab' keyword: start as homelab orchestration server
        if ("homelab".equalsIgnoreCase(args[0])) {
            OperationType.execute_inline_homelab_server.getImplementation().execute(Arrays.asList(args));
            return;
        }
        // If one argument is given, treat it as a path and use execute_inline
        if(args.length == 1) {
            OperationType.execute_inline.getImplementation().execute(Arrays.asList(args));
            return;
        }
        // If the first argument looks like a file path (e.g. ends with .py or contains
        // a path separator), treat ALL arguments as execute_inline args:
        //   args[0] = script file, args[1..N] = CSV data files
        if(args[0].contains(".") || args[0].contains("/") || args[0].contains("\\")) {
            OperationType.execute_inline.getImplementation().execute(Arrays.asList(args));
            return;
        }
        // Otherwise, use the first as operation type
        OperationType operationType = OperationType.getOperation(args[0]);
        if(operationType == null) {
            List<String> possibleOperators = new ArrayList<>();
            for(OperationType type : OperationType.values()) {
                possibleOperators.add(type.getType());
            }
            System.out.println("Wrong execType. Possible types: " + possibleOperators);
            return;
        }
        operationType.getImplementation().execute(Arrays.asList(args).subList(1, args.length));
    }
}

package in.ramanujan.developer.console;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Console {
    public static void main(String[] args) throws IOException {
        if(args.length == 0) {
            System.out.println("No arguments given");
            return;
        }
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

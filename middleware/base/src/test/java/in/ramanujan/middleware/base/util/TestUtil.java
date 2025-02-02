package in.ramanujan.middleware.base.util;

import in.ramanujan.middleware.base.pojo.IndexWrapper;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.HashSet;


public class TestUtil {

    private static Logger logger = LoggerFactory.getLogger(TestUtil.class);

    @Data
    @AllArgsConstructor
    public static class Report {
        private Boolean testResult;
        private String code;
    };
    public static void createCombinations(String code, int index, IndexWrapper indexWrapper, TestHeuristic testHeuristic) {
        if(index >= code.length()) {
            triggerAssertion(code, indexWrapper, testHeuristic);
            return;
        }
        HashSet<Character> characterList = new HashSet<Character>(){{add(' ');add('[');
        add(']');add('{');add('}');add(',');add(';');add('(');add(')');}};

        for(int i = index; i < code.length(); i++) {
            if(characterList.contains(code.charAt(i)) && (code.indexOf(code.charAt(i)) == i || Math.random() > 0.9)) {
                createCombinations(code.substring(0, i) + " " + code.substring(i), i+2, indexWrapper, testHeuristic);
                if(i < (code.length() -1)) {
                    createCombinations(code.substring(0, i + 1) + " " + code.substring(i+1), i+2, indexWrapper, testHeuristic);
                }
                createCombinations(code, i+1, indexWrapper, testHeuristic);
                break;
            }
        }
        triggerAssertion(code, indexWrapper, testHeuristic);
    }

    private static void triggerAssertion(String code, IndexWrapper indexWrapper, TestHeuristic testHeuristic) {
        int count = indexWrapper.getIndex();
        logger.info(code);
        logger.info(count);
        indexWrapper.setIndex(count + 1);
        testHeuristic.test(Collections.singletonList(code));
    }
}

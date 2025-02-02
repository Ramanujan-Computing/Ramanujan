package in.ramanujan.middleware.base.pojo.grammar.debugLevelCodeCreatorImpl;

import in.ramanujan.middleware.base.pojo.grammar.DebugLevelCodeCreator;

public final class ActualDebugCodeCreator implements DebugLevelCodeCreator {
    private int indentation = 0;

    private int line = 0;

    final StringBuilder stringWrapper = new StringBuilder("");

    public ActualDebugCodeCreator(final String originalString, Integer line) {
        stringWrapper.append(originalString);
        if(line != null) {
            this.line = line;
        }
    }

    @Override
    public void nextLine() {
        line++;
        stringWrapper.append("\n");
    }

    @Override
    public void concat(String stringToBeAdded) {
        for(int i=0;i<indentation;i++) {
            stringWrapper.append("\t");
        }
        stringWrapper.append(stringToBeAdded);
        if(stringToBeAdded.endsWith(";")) {
            nextLine();
        }
    }

    @Override
    public void addIndentation() {
        indentation++;
    }

    @Override
    public void decrementIndentation() {
        indentation--;
    }

    @Override
    public int getLine() {
        return line;
    }

    public String getDebugCode() {
        return stringWrapper.toString();
    }
}

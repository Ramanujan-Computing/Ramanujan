package in.robinhood.ramanujan.middleware.base.pojo.grammar.debugLevelCodeCreatorImpl;

import in.robinhood.ramanujan.middleware.base.pojo.grammar.DebugLevelCodeCreator;

public final class NoConcatImpl implements DebugLevelCodeCreator {
    @Override
    public void concat(String stringToBeAdded) {

    }

    @Override
    public void addIndentation() {

    }

    @Override
    public void decrementIndentation() {

    }

    @Override
    public void nextLine() {

    }

    @Override
    public int getLine() {
        //return -1 since we want a non-usable index.
        return -1;
    }
}

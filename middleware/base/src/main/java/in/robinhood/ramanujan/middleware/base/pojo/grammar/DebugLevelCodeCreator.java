package in.robinhood.ramanujan.middleware.base.pojo.grammar;

public interface DebugLevelCodeCreator {
    void concat(final String stringToBeAdded);
    void addIndentation();
    void decrementIndentation();
    void nextLine();

    int getLine();
}

package in.ramanujan.translation.codeConverter.grammar;

public interface DebugLevelCodeCreator {
    void concat(final String stringToBeAdded);
    void addIndentation();
    void decrementIndentation();
    void nextLine();

    int getLine();
}

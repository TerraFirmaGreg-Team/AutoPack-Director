package team.terrafirmagreg.autopack.manage;

public interface ProgressCallback {
    ProgressCallback NO_OP = new ProgressCallback() {};

    default void setSteps(int steps) {}

    default void reportProgress(long current, long max) {}

    default void message(String message) {}

    default void step() {}

    default void done() {}

    default void title(String newTitle) {}

    default void indeterminate(boolean isIndeterminate) {}
}

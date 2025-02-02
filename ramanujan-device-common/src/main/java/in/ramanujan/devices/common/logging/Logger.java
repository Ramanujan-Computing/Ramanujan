package in.ramanujan.devices.common.logging;

public interface Logger {
    public void info(Object obj);
    public void error(Object obj, Throwable throwable);
}

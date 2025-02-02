package in.ramanujan.rule.engine.debugger;

import in.ramanujan.debugger.UserReadableDebugPoint;

import java.io.IOException;
import java.util.List;

public interface IDebugPushClient {
    public void call(List<UserReadableDebugPoint> debugPointList) throws IOException;
}

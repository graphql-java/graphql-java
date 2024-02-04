package graphql.agent.result;

import graphql.execution.ResultPath;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExecutionTrackingResult {
    public static final String EXECUTION_TRACKING_KEY = "__GJ_AGENT_EXECUTION_TRACKING";
    private final Map<ResultPath, Long> timePerPath = new LinkedHashMap<>();

    public void start(ResultPath path, long startTime) {
        timePerPath.put(path, startTime);
    }

    public void end(ResultPath path, long endTime) {
        timePerPath.put(path, endTime - timePerPath.get(path));
    }

    public int dataFetcherCount() {
        return timePerPath.size();
    }

    public long getTime(ResultPath path) {
        return timePerPath.get(path);
    }

    public long getTime(String path) {
        return timePerPath.get(ResultPath.parse(path));
    }

    @Override
    public String toString() {
        return "ExecutionTrackingResult{" +
                "timePerPath=" + timePerPath +
                '}';
    }
}

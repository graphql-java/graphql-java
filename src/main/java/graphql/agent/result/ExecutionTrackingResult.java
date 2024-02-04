package graphql.agent.result;

import graphql.execution.ResultPath;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExecutionTrackingResult {

    public enum DFResultType {
        DONE_OK,
        DONE_EXCEPTIONALLY,
        DONE_CANCELLED,
        PENDING,
    }

    public static final String EXECUTION_TRACKING_KEY = "__GJ_AGENT_EXECUTION_TRACKING";
    private final Map<ResultPath, Long> timePerPath = new ConcurrentHashMap<>();
    private final Map<ResultPath, DFResultType> dfResultTypes = new ConcurrentHashMap<>();

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

    public void setDfResultTypes(ResultPath resultPath, DFResultType resultTypes) {
        dfResultTypes.put(resultPath, resultTypes);
    }

    public DFResultType getDfResultTypes(ResultPath resultPath) {
        return dfResultTypes.get(resultPath);
    }

    public DFResultType getDfResultTypes(String resultPath) {
        return dfResultTypes.get(ResultPath.parse(resultPath));
    }

    @Override
    public String toString() {
        return "ExecutionTrackingResult{" +
                "timePerPath=" + timePerPath +
                '}';
    }
}

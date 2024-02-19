package graphql.agent.result;

import graphql.PublicApi;
import graphql.execution.ResultPath;
import org.dataloader.DataLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static graphql.agent.result.ExecutionTrackingResult.DFResultType.PENDING;

/**
 * This is the result of the agent tracking an execution.
 * It can be found inside the GraphQLContext after the execution with the key {@link ExecutionTrackingResult#EXECUTION_TRACKING_KEY}
 *
 * Note: While this is public API, the main goal is temporary debugging to understand an execution better with minimal overhead.
 * Therefore this will evolve over time if needed to be performant and reflect the overall execution.
 * It is not recommended to have the agent on always or to rely on this class during normal execution
 */
@PublicApi
public class ExecutionTrackingResult {

    public static final String EXECUTION_TRACKING_KEY = "__GJ_AGENT_EXECUTION_TRACKING";
    public final AtomicReference<String> startThread = new AtomicReference<>();
    public final AtomicReference<String> endThread = new AtomicReference<>();
    public final AtomicLong startExecutionTime = new AtomicLong();
    public final AtomicLong endExecutionTime = new AtomicLong();
    public final Map<ResultPath, String> resultPathToDataLoaderUsed = new ConcurrentHashMap<>();
    public final Map<DataLoader, String> dataLoaderToName = new ConcurrentHashMap<>();

    public final Map<ResultPath, Long> timePerPath = new ConcurrentHashMap<>();
    public final Map<ResultPath, Long> finishedTimePerPath = new ConcurrentHashMap<>();
    public final Map<ResultPath, String> finishedThreadPerPath = new ConcurrentHashMap<>();
    public final Map<ResultPath, String> startInvocationThreadPerPath = new ConcurrentHashMap<>();
    private final Map<ResultPath, DFResultType> dfResultTypes = new ConcurrentHashMap<>();
    public final Map<String, List<BatchLoadingCall>> dataLoaderNameToBatchCall = new ConcurrentHashMap<>();

    public static class BatchLoadingCall {
        public BatchLoadingCall(int keyCount, String threadName) {
            this.keyCount = keyCount;
            this.threadName = threadName;
        }

        public final int keyCount;
        public final String threadName;

    }


    public String print(String executionId) {
        StringBuilder s = new StringBuilder();
        s.append("==========================").append("\n");
        s.append("Summary for execution with id ").append(executionId).append("\n");
        s.append("==========================").append("\n");
        s.append("Execution time in ms:").append((endExecutionTime.get() - startExecutionTime.get()) / 1_000_000L).append("\n");
        s.append("Fields count: ").append(timePerPath.keySet().size()).append("\n");
        s.append("Blocking fields count: ").append(dfResultTypes.values().stream().filter(dfResultType -> dfResultType != PENDING).count()).append("\n");
        s.append("Nonblocking fields count: ").append(dfResultTypes.values().stream().filter(dfResultType -> dfResultType == PENDING).count()).append("\n");
        s.append("DataLoaders used: ").append(dataLoaderToName.size()).append("\n");
        s.append("DataLoader names: ").append(dataLoaderToName.values()).append("\n");
        s.append("start execution thread: '").append(startThread.get()).append("'\n");
        s.append("end execution  thread: '").append(endThread.get()).append("'\n");
        s.append("BatchLoader calls details: ").append("\n");
        s.append("==========================").append("\n");
        for (String dataLoaderName : dataLoaderNameToBatchCall.keySet()) {
            s.append("Batch call: '").append(dataLoaderName).append("' made ").append(dataLoaderNameToBatchCall.get(dataLoaderName).size()).append(" times, ").append("\n");
            for (BatchLoadingCall batchLoadingCall : dataLoaderNameToBatchCall.get(dataLoaderName)) {
                s.append("Batch call with ").append(batchLoadingCall.keyCount).append(" keys ").append(" in thread ").append(batchLoadingCall.threadName).append("\n");
            }
            List<ResultPath> resultPathUsed = new ArrayList<>();
            for (ResultPath resultPath : resultPathToDataLoaderUsed.keySet()) {
                if (resultPathToDataLoaderUsed.get(resultPath).equals(dataLoaderName)) {
                    resultPathUsed.add(resultPath);
                }
            }
            s.append("DataLoader: '").append(dataLoaderName).append("' used in fields: ").append(resultPathUsed).append("\n");
        }
        s.append("Field details:").append("\n");
        s.append("===============").append("\n");
        for (ResultPath path : timePerPath.keySet()) {
            s.append("Field: '").append(path).append("'\n");
            s.append("invocation time: ").append(timePerPath.get(path)).append(" nano seconds, ").append("\n");
            s.append("completion time: ").append(finishedTimePerPath.get(path)).append(" nano seconds, ").append("\n");
            s.append("Result type: ").append(dfResultTypes.get(path)).append("\n");
            s.append("invoked in thread: ").append(startInvocationThreadPerPath.get(path)).append("\n");
            s.append("finished in thread: ").append(finishedThreadPerPath.get(path)).append("\n");
            s.append("-------------\n");
        }
        s.append("==========================").append("\n");
        s.append("==========================").append("\n");
        return s.toString();

    }

    @Override
    public String toString() {
        return "ExecutionData{" +
                "resultPathToDataLoaderUsed=" + resultPathToDataLoaderUsed +
                ", dataLoaderNames=" + dataLoaderToName.values() +
                ", timePerPath=" + timePerPath +
                ", dfResultTypes=" + dfResultTypes +
                '}';
    }

    public enum DFResultType {
        DONE_OK,
        DONE_EXCEPTIONALLY,
        DONE_CANCELLED,
        PENDING,
    }

    public List<String> getDataLoaderNames() {
        return new ArrayList<>(dataLoaderToName.values());
    }


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


}

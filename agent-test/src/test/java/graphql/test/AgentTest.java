package graphql.test;

import graphql.agent.result.ExecutionTrackingResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentTest {

    @BeforeAll
    static void init() {
        LoadAgent.loadIntoCurrentJVM();
    }

    @AfterAll
    static void cleanup() {
    }

    @Test
    void test() {
        ExecutionTrackingResult executionTrackingResult = TestQuery.executeQuery();
        assertThat(executionTrackingResult.dataFetcherCount()).isEqualTo(5);
        assertThat(executionTrackingResult.getTime("/issues")).isGreaterThan(100);
        assertThat(executionTrackingResult.getDfResultTypes("/issues"))
                .isEqualTo(ExecutionTrackingResult.DFResultType.DONE_OK);

        verifyAgentDataIsEmpty();

    }

    @Test
    void testBatchLoader() {
        ExecutionTrackingResult executionTrackingResult = TestQuery.executeBatchedQuery();
        assertThat(executionTrackingResult.dataFetcherCount()).isEqualTo(9);
        assertThat(executionTrackingResult.getTime("/issues")).isGreaterThan(100);
        assertThat(executionTrackingResult.getDfResultTypes("/issues[0]/author"))
                .isEqualTo(ExecutionTrackingResult.DFResultType.PENDING);
        assertThat(executionTrackingResult.getDfResultTypes("/issues[1]/author"))
                .isEqualTo(ExecutionTrackingResult.DFResultType.PENDING);

        assertThat(executionTrackingResult.getDataLoaderNames()).isEqualTo(Collections.singletonList("userLoader"));

        assertThat(executionTrackingResult.dataLoaderNameToBatchCall).hasSize(1);
        List<ExecutionTrackingResult.BatchLoadingCall> userLoaderCalls = executionTrackingResult.dataLoaderNameToBatchCall.get("userLoader");
        assertThat(userLoaderCalls).hasSize(1);
        ExecutionTrackingResult.BatchLoadingCall batchLoadingCall = userLoaderCalls.get(0);

        assertThat(batchLoadingCall.keyCount).isEqualTo(2);

        verifyAgentDataIsEmpty();
    }

    private void verifyAgentDataIsEmpty() {
        try {
            Class<?> agent = Class.forName("graphql.agent.GraphQLJavaAgent");
            Map executionIdToData = (Map) agent.getField("executionIdToData").get(null);
            Map dataLoaderToExecutionId = (Map) agent.getField("dataLoaderToExecutionId").get(null);
            assertThat(executionIdToData).isEmpty();
            assertThat(dataLoaderToExecutionId).isEmpty();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}

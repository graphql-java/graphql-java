package graphql.test;

import graphql.agent.result.ExecutionTrackingResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentTest {

    @BeforeAll
    static void init() {
        LoadAgent.load();
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
    }
}

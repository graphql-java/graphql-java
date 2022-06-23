package benchmark;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionContextBuilder;
import graphql.execution.ExecutionId;
import graphql.execution.ResultPath;
import graphql.schema.idl.errors.SchemaMissingError;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Collections;

@State(Scope.Benchmark)
public class AddError {

    private ExecutionContext context = new ExecutionContextBuilder()
            .executionId(ExecutionId.generate())
            .build();

    private volatile int x = 0;

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 1, batchSize = 50000)
    @Measurement(iterations = 1, batchSize = 5000)
    public ExecutionContext benchMarkAddError() throws InterruptedException {
        context.addError(
                new SchemaMissingError(),
                ResultPath.fromList(Collections.singletonList(x++))
        );
        return context;
    }
}

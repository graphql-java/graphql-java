package benchmark;

import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 3, time = 5)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class NumericLexerBenchmark {

    @Param({"small", "manySmall", "longInteger", "longMalformedExponent"})
    public String inputShape;

    private String query;

    @Setup
    public void setup() {
        switch (inputShape) {
            case "small":
                query = "{ f(arg: 12345) }";
                return;
            case "manySmall":
                query = "{ f(arg: [" + "12345,".repeat(1000) + "]) }";
                return;
            case "longInteger":
                query = "{ f(arg: " + "9".repeat(900_000) + ") }";
                return;
            case "longMalformedExponent":
                query = "{ f(arg: 1e" + "9".repeat(900_000) + "x) }";
                return;
            default:
                throw new IllegalArgumentException(inputShape);
        }
    }

    @Benchmark
    public void parse(Blackhole blackhole) {
        try {
            blackhole.consume(Parser.parse(query));
        } catch (InvalidSyntaxException expected) {
            blackhole.consume(expected);
        }
    }
}

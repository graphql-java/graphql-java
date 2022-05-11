package benchmark;

import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.TimeUnit;

import static benchmark.BenchmarkUtils.asRTE;

/**
 * This benchmarks {@link graphql.schema.idl.TypeDefinitionRegistry} parsing and serialisation
 * <p>
 * See https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/ for more samples
 * on what you can do with JMH
 * <p>
 * You MUST have the JMH plugin for IDEA in place for this to work :  https://github.com/artyushov/idea-jmh-plugin
 * <p>
 * Install it and then just hit "Run" on a certain benchmark method
 */
@Warmup(iterations = 2, time = 5, batchSize = 3)
@Measurement(iterations = 3, time = 10, batchSize = 4)
public class TypeDefinitionParserVersusSerializeBenchMark {

    static SchemaParser schemaParser = new SchemaParser();
    static String SDL = BenchmarkUtils.loadResource("large-schema-2.graphqls");
    static TypeDefinitionRegistry registryOut = schemaParser.parse(SDL);
    static ByteArrayOutputStream baOS = serialisedRegistryStream(registryOut);

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkParsing(Blackhole blackhole) {
        blackhole.consume(schemaParser.parse(SDL));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkSerializing(Blackhole blackhole) {
        blackhole.consume(serialise());
    }

    static TypeDefinitionRegistry serialise() {
        return asRTE(() -> {

            ByteArrayInputStream baIS = new ByteArrayInputStream(baOS.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(baIS);

            return (TypeDefinitionRegistry) ois.readObject();
        });
    }

    private static ByteArrayOutputStream serialisedRegistryStream(TypeDefinitionRegistry registryOut) {
        return asRTE(() -> {
            ByteArrayOutputStream baOS = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baOS);

            oos.writeObject(registryOut);
            return baOS;
        });
    }
}

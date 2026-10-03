package benchmark;

import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.SchemaTraverser;
import graphql.schema.idl.FastSchemaGenerator;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.validation.InputObjectHasUnbreakableCycle;
import graphql.schema.validation.SchemaValidationError;
import graphql.schema.validation.SchemaValidationErrorCollector;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Measures full-schema traversal with only the input object cycle validator. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(2)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class InputObjectHasUnbreakableCycleBenchmark {

    private GraphQLSchema twoTypeMixedInputCycle;
    private GraphQLSchema tenTypeMixedInputCycle;
    private GraphQLSchema tenTypeTenFieldsInputCycle;
    private GraphQLSchema finiteValuePropagation;
    private SchemaTraverser schemaTraverser;
    private List<GraphQLTypeVisitor> cycleValidators;
    private SchemaValidationErrorCollector errorCollector;
    private Map<Class<?>, Object> rootVariables;

    @Setup(Level.Trial)
    public void setUpSchemas() {
        twoTypeMixedInputCycle = loadSchema(
                "InputObjectHasUnbreakableCycleBenchmark/twoTypeMixedInputCycle.graphqls"
        );
        tenTypeMixedInputCycle = loadSchema(
                "InputObjectHasUnbreakableCycleBenchmark/tenTypeMixedInputCycle.graphqls"
        );
        tenTypeTenFieldsInputCycle = loadSchema(
                "InputObjectHasUnbreakableCycleBenchmark/tenTypeTenFieldsInputCycle.graphqls"
        );
        finiteValuePropagation = loadSchema(
                "InputObjectHasUnbreakableCycleBenchmark/finiteValuePropagation.graphqls"
        );
        schemaTraverser = new SchemaTraverser();
    }

    @Setup(Level.Invocation)
    public void setUpInvocation() {
        cycleValidators = Collections.singletonList(new InputObjectHasUnbreakableCycle());
        errorCollector = new SchemaValidationErrorCollector();
        rootVariables = new LinkedHashMap<>();
        rootVariables.put(SchemaValidationErrorCollector.class, errorCollector);
    }

    @Benchmark
    public void twoTypeMixedInputCycle(Blackhole blackhole) {
        validate(twoTypeMixedInputCycle, blackhole);
    }

    @Benchmark
    public void tenTypeMixedInputCycle(Blackhole blackhole) {
        validate(tenTypeMixedInputCycle, blackhole);
    }

    @Benchmark
    public void tenTypeTenFieldsInputCycle(Blackhole blackhole) {
        validate(tenTypeTenFieldsInputCycle, blackhole);
    }

    @Benchmark
    public void finiteValuePropagation(Blackhole blackhole) {
        validate(finiteValuePropagation, blackhole);
    }

    private void validate(GraphQLSchema schema, Blackhole blackhole) {
        schemaTraverser.depthFirstFullSchema(cycleValidators, schema, rootVariables);
        for (SchemaValidationError error : errorCollector.getErrors()) {
            blackhole.consume(error);
        }
    }

    private GraphQLSchema loadSchema(String schemaPath) {
        String schemaString = BenchmarkUtils.loadResource(schemaPath);
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(schemaString);
        return new FastSchemaGenerator().makeExecutableSchema(
                SchemaGenerator.Options.defaultOptions().withValidation(false),
                typeRegistry,
                RuntimeWiring.MOCKED_WIRING
        );
    }
}

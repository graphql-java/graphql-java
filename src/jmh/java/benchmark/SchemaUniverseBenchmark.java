package benchmark;

import graphql.Scalars;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.schema.InputValueWithState;
import graphql.schema.SchemaTransformer;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.universe.SUAppliedDirective;
import graphql.schema.universe.SUAppliedDirectiveArgument;
import graphql.schema.universe.SUField;
import graphql.schema.universe.SUObjectType;
import graphql.schema.universe.SUScalarType;
import graphql.schema.universe.SUSchema;
import graphql.schema.universe.SchemaUniverse;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jol.info.GraphLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Compares 100 related large-schema-5 versions represented by GraphQLSchema and SchemaUniverse.
 *
 * <p>Every version replaces a versioned applied directive on 1,000 object types distributed across
 * the source schema. Each five-step cycle additionally adds a detached object type, adds and removes
 * a field, adds a replacement field, and removes the type. The {@code retain100...} methods print
 * exact JOL object-graph footprints. Run the {@code profileRetained100...} methods separately with
 * async-profiler's sampled live-allocation mode to attribute surviving allocations; use the JOL
 * values, not the sampled profile totals, for exact retained byte counts:</p>
 *
 * <pre>
 * ./gradlew jmh \
 *   -PjmhInclude='SchemaUniverseBenchmark.profileRetained100(GraphQLSchema|SUSchema)Versions' \
 *   -PjmhProfilers='async:event=alloc;rawCommand=live;output=text;dir=build/reports/jmh/schema-universe'
 * </pre>
 */
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Timeout(time = 30, timeUnit = TimeUnit.MINUTES)
public class SchemaUniverseBenchmark {

    private static final int CYCLE_COUNT = 20;
    private static final int VERSION_COUNT = CYCLE_COUNT * EditKind.values().length;
    private static final int DIRECTIVE_TARGET_COUNT = 1_000;
    private static final double BYTES_PER_MIB = 1024.0 * 1024.0;
    private static final String BASE_SCHEMA_NAME = "large_schema_5_base";
    private static final String SYNTHETIC_PREFIX = "BenchmarkVersion";
    private static final String BENCHMARK_DIRECTIVE_NAME = "benchmarkEdit";
    private static final String BENCHMARK_DIRECTIVE_VERSION_ARGUMENT = "version";

    /**
     * Runs the expensive pairwise correctness check independently of JMH measurements. Pass a
     * version count to verify a smaller prefix of the mutation plan.
     */
    public static void main(String[] args) {
        GraphQLSchema baseSchema = loadBaseSchema();
        MutationPlan plan = MutationPlan.create(baseSchema)
                .firstVersions(verificationVersionCount(args));
        GraphQLVersionSet graphQLVersions =
                createGraphQLSchemaVersions(baseSchema, plan);

        SchemaUniverse universe = new SchemaUniverse();
        SUSchema suBase = universe.importSchema(BASE_SCHEMA_NAME, baseSchema);
        createSUSchemaVersions(universe, suBase, plan);

        verifyEquivalentVersions(graphQLVersions, universe.getSchemas());
    }

    private static int verificationVersionCount(String[] args) {
        if (args.length == 0) {
            return VERSION_COUNT;
        }
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected at most one version-count argument");
        }
        int versionCount = Integer.parseInt(args[0]);
        if (versionCount < 1 || versionCount > VERSION_COUNT) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Version count must be between 1 and %d",
                    VERSION_COUNT));
        }
        return versionCount;
    }

    @State(Scope.Benchmark)
    public static class GraphQLTimingState {
        GraphQLSchema baseSchema;
        MutationPlan plan;

        @Setup(Level.Trial)
        public void setup() {
            baseSchema = loadBaseSchema();
            plan = MutationPlan.create(baseSchema);
        }
    }

    @State(Scope.Benchmark)
    public static class SUTimingState {
        GraphQLSchema sourceSchema;
        MutationPlan plan;
        SchemaUniverse universe;
        SUSchema baseSchema;

        @Setup(Level.Trial)
        public void setupTrial() {
            sourceSchema = loadBaseSchema();
            plan = MutationPlan.create(sourceSchema);
        }

        @Setup(Level.Iteration)
        public void setupIteration() {
            universe = new SchemaUniverse();
            baseSchema = universe.importSchema(BASE_SCHEMA_NAME, sourceSchema);
        }
    }

    @State(Scope.Benchmark)
    public static class GraphQLMemoryState {
        GraphQLSchema baseSchema;
        MutationPlan plan;
        long baseRetainedBytes;
        GraphQLVersionSet retainedVersions;

        @Setup(Level.Trial)
        public void setup() {
            baseSchema = loadBaseSchema();
            plan = MutationPlan.create(baseSchema);
            baseRetainedBytes = retainedSize(baseSchema);
        }

        @TearDown(Level.Trial)
        public void reportRetainedMemory() {
            requireNonNull(retainedVersions, "retained GraphQLSchema versions");
            reportFootprint(
                    "GraphQLSchema",
                    baseRetainedBytes,
                    retainedSize(retainedVersions));
        }
    }

    @State(Scope.Benchmark)
    public static class SUMemoryState {
        MutationPlan plan;
        SchemaUniverse universe;
        SUSchema baseSchema;
        long baseRetainedBytes;
        SchemaUniverse retainedUniverse;

        @Setup(Level.Trial)
        public void setup() {
            GraphQLSchema sourceSchema = loadBaseSchema();
            plan = MutationPlan.create(sourceSchema);
            universe = new SchemaUniverse();
            baseSchema = universe.importSchema(BASE_SCHEMA_NAME, sourceSchema);
            baseRetainedBytes = retainedSize(universe);
        }

        @TearDown(Level.Trial)
        public void reportRetainedMemory() {
            requireNonNull(retainedUniverse, "retained schema universe");
            reportFootprint(
                    "SUSchema",
                    baseRetainedBytes,
                    retainedSize(retainedUniverse));
        }
    }

    @State(Scope.Benchmark)
    public static class GraphQLProfileState {
        GraphQLSchema baseSchema;
        MutationPlan plan;
        GraphQLVersionSet retainedVersions;

        @Setup(Level.Trial)
        public void setup() {
            baseSchema = loadBaseSchema();
            plan = MutationPlan.create(baseSchema);
        }
    }

    @State(Scope.Benchmark)
    public static class SUProfileState {
        MutationPlan plan;
        SchemaUniverse universe;
        SUSchema baseSchema;
        SchemaUniverse retainedUniverse;

        @Setup(Level.Trial)
        public void setup() {
            GraphQLSchema sourceSchema = loadBaseSchema();
            plan = MutationPlan.create(sourceSchema);
            universe = new SchemaUniverse();
            baseSchema = universe.importSchema(BASE_SCHEMA_NAME, sourceSchema);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    @Fork(
            value = 1,
            jvmArgsAppend = {
                    "-Xms8g",
                    "-Xmx30g",
                    "-Djdk.attach.allowAttachSelf=true",
                    "-Djol.magicFieldOffset=true",
                    "-XX:+EnableDynamicAgentLoading"
            })
    public GraphQLVersionSet create100GraphQLSchemaVersions(GraphQLTimingState state) {
        return createGraphQLSchemaVersions(state.baseSchema, state.plan);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    @Fork(
            value = 1,
            jvmArgsAppend = {
                    "-Xms8g",
                    "-Xmx30g",
                    "-Djdk.attach.allowAttachSelf=true",
                    "-Djol.magicFieldOffset=true",
                    "-XX:+EnableDynamicAgentLoading"
            })
    public SchemaUniverse create100SUSchemaVersions(SUTimingState state) {
        createSUSchemaVersions(state.universe, state.baseSchema, state.plan);
        return state.universe;
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    @Fork(
            value = 1,
            jvmArgsAppend = {
                    "-Xms8g",
                    "-Xmx30g",
                    "-Djdk.attach.allowAttachSelf=true",
                    "-Djol.magicFieldOffset=true",
                    "-XX:+EnableDynamicAgentLoading"
            })
    public GraphQLVersionSet retain100GraphQLSchemaVersions(GraphQLMemoryState state) {
        state.retainedVersions = createGraphQLSchemaVersions(state.baseSchema, state.plan);
        System.gc();
        return state.retainedVersions;
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    @Fork(
            value = 1,
            jvmArgsAppend = {
                    "-Xms8g",
                    "-Xmx30g",
                    "-Djdk.attach.allowAttachSelf=true",
                    "-Djol.magicFieldOffset=true",
                    "-XX:+EnableDynamicAgentLoading"
            })
    public SchemaUniverse retain100SUSchemaVersions(SUMemoryState state) {
        createSUSchemaVersions(state.universe, state.baseSchema, state.plan);
        state.retainedUniverse = state.universe;
        System.gc();
        return state.retainedUniverse;
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    @Fork(
            value = 1,
            jvmArgsAppend = {
                    "-Xms8g",
                    "-Xmx30g",
                    "-Djdk.attach.allowAttachSelf=true",
                    "-Djol.magicFieldOffset=true",
                    "-XX:+EnableDynamicAgentLoading"
            })
    public GraphQLVersionSet profileRetained100GraphQLSchemaVersions(
            GraphQLProfileState state) {
        state.retainedVersions =
                createGraphQLSchemaVersions(state.baseSchema, state.plan);
        System.gc();
        return state.retainedVersions;
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    @Fork(
            value = 1,
            jvmArgsAppend = {
                    "-Xms8g",
                    "-Xmx30g",
                    "-Djdk.attach.allowAttachSelf=true",
                    "-Djol.magicFieldOffset=true",
                    "-XX:+EnableDynamicAgentLoading"
            })
    public SchemaUniverse profileRetained100SUSchemaVersions(
            SUProfileState state) {
        createSUSchemaVersions(state.universe, state.baseSchema, state.plan);
        state.retainedUniverse = state.universe;
        System.gc();
        return state.retainedUniverse;
    }

    private static GraphQLVersionSet createGraphQLSchemaVersions(
            GraphQLSchema baseSchema,
            MutationPlan plan) {
        List<GraphQLSchema> schemas = new ArrayList<>(VERSION_COUNT + 1);
        schemas.add(baseSchema);
        GraphQLSchema current = baseSchema;
        for (int i = 0; i < plan.mutations.size(); i++) {
            Mutation mutation = plan.mutations.get(i);
            current = applyGraphQLMutation(current, mutation, plan, i + 1);
            schemas.add(current);
        }
        verifyFinalGraphQLSchema(baseSchema, current, schemas, plan);
        return new GraphQLVersionSet(schemas);
    }

    private static GraphQLSchema applyGraphQLMutation(
            GraphQLSchema current,
            Mutation mutation,
            MutationPlan plan,
            int version) {
        int expectedExistingDirectives = version == 1 ? 0 : 1;
        int[] touchedTargetCount = {0};
        GraphQLTypeVisitorStub visitor = new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLObjectType(
                    GraphQLObjectType objectType,
                    TraverserContext<GraphQLSchemaElement> context) {
                GraphQLObjectType changedObject = objectType;
                boolean changed = false;
                if (plan.directiveTargetTypeNameSet.contains(objectType.getName())) {
                    List<GraphQLAppliedDirective> retainedDirectives = new ArrayList<>();
                    int existingDirectiveCount = 0;
                    for (GraphQLAppliedDirective directive : objectType.getAppliedDirectives()) {
                        if (BENCHMARK_DIRECTIVE_NAME.equals(directive.getName())) {
                            existingDirectiveCount++;
                        } else {
                            retainedDirectives.add(directive);
                        }
                    }
                    if (existingDirectiveCount != expectedExistingDirectives) {
                        throw new IllegalStateException(String.format(
                                Locale.ROOT,
                                "Expected %d @%s directives on %s before version %d, found %d",
                                expectedExistingDirectives,
                                BENCHMARK_DIRECTIVE_NAME,
                                objectType.getName(),
                                version,
                                existingDirectiveCount));
                    }
                    retainedDirectives.add(newBenchmarkAppliedDirective(version));
                    changedObject = changedObject.transform(builder ->
                            builder.replaceAppliedDirectives(retainedDirectives));
                    touchedTargetCount[0]++;
                    changed = true;
                }

                if (mutation.syntheticTypeName.equals(objectType.getName())) {
                    switch (mutation.kind) {
                        case ADD_SYNTHETIC_FIELD:
                            changedObject = withAddedField(
                                    changedObject,
                                    newStringField(current, mutation.syntheticFieldName));
                            changed = true;
                            break;
                        case REMOVE_SYNTHETIC_FIELD:
                            changedObject = withRemovedField(
                                    changedObject,
                                    mutation.syntheticFieldName);
                            changed = true;
                            break;
                        case ADD_REPLACEMENT_FIELD:
                            changedObject = withAddedField(
                                    changedObject,
                                    newStringField(current, mutation.replacementFieldName));
                            changed = true;
                            break;
                        case REMOVE_SYNTHETIC_TYPE:
                            return deleteNode(context);
                        default:
                            break;
                    }
                }
                return changed
                        ? changeNode(context, changedObject)
                        : TraversalControl.CONTINUE;
            }
        };

        GraphQLSchema transformed;
        if (mutation.kind == EditKind.ADD_SYNTHETIC_TYPE) {
            GraphQLObjectType syntheticType = GraphQLObjectType.newObject()
                    .name(mutation.syntheticTypeName)
                    .field(GraphQLFieldDefinition.newFieldDefinition()
                            .name("value")
                            .type((GraphQLOutputType) requireNonNull(current.getType("String"))))
                    .build();
            transformed = SchemaTransformer.transformSchema(
                    current,
                    visitor,
                    builder -> builder.additionalType(syntheticType));
        } else if (mutation.kind == EditKind.REMOVE_SYNTHETIC_TYPE) {
            transformed = SchemaTransformer.transformSchemaWithDeletes(current, visitor);
        } else {
            transformed = SchemaTransformer.transformSchema(current, visitor);
        }
        assertTouchedDirectiveTargets("GraphQLSchema", version, touchedTargetCount[0], plan);
        return transformed;
    }

    private static GraphQLAppliedDirective newBenchmarkAppliedDirective(int version) {
        return GraphQLAppliedDirective.newDirective()
                .name(BENCHMARK_DIRECTIVE_NAME)
                .argument(GraphQLAppliedDirectiveArgument.newArgument()
                        .name(BENCHMARK_DIRECTIVE_VERSION_ARGUMENT)
                        .type(Scalars.GraphQLInt)
                        .valueProgrammatic(version))
                .build();
    }

    private static GraphQLFieldDefinition newStringField(
            GraphQLSchema schema,
            String name) {
        GraphQLOutputType stringType =
                (GraphQLOutputType) requireNonNull(schema.getType("String"));
        return GraphQLFieldDefinition.newFieldDefinition()
                .name(name)
                .type(stringType)
                .build();
    }

    private static GraphQLObjectType withAddedField(
            GraphQLObjectType objectType,
            GraphQLFieldDefinition field) {
        if (objectType.getFieldDefinition(field.getName()) != null) {
            throw new IllegalStateException("Field already exists: " + field.getName());
        }
        return objectType.transform(builder -> builder.field(field));
    }

    private static GraphQLObjectType withRemovedField(
            GraphQLObjectType objectType,
            String fieldName) {
        List<GraphQLFieldDefinition> fields =
                new ArrayList<>(objectType.getFieldDefinitions());
        boolean removed = fields.removeIf(field -> fieldName.equals(field.getName()));
        if (!removed) {
            throw new IllegalStateException("Field does not exist: " + fieldName);
        }
        return objectType.transform(builder -> builder.replaceFields(fields));
    }

    private static void createSUSchemaVersions(
            SchemaUniverse universe,
            SUSchema baseSchema,
            MutationPlan plan) {
        SUSchema current = baseSchema;
        SUScalarType stringType =
                requireNonNull(baseSchema.getScalarType("String"));
        SUScalarType intType =
                requireNonNull(baseSchema.getScalarType("Int"));
        for (int i = 0; i < plan.mutations.size(); i++) {
            Mutation mutation = plan.mutations.get(i);
            current = applySUMutation(
                    universe,
                    current,
                    stringType,
                    intType,
                    mutation,
                    plan,
                    i + 1);
        }
        verifyFinalSUSchema(universe, baseSchema, current, plan);
    }

    private static SUSchema applySUMutation(
            SchemaUniverse universe,
            SUSchema current,
            SUScalarType stringType,
            SUScalarType intType,
            Mutation mutation,
            MutationPlan plan,
            int version) {
        String versionName = String.format(Locale.ROOT, "version_%03d", version);
        int expectedExistingDirectives = version == 1 ? 0 : 1;
        return current.transform(versionName, builder -> {
            switch (mutation.kind) {
                case ADD_SYNTHETIC_TYPE:
                    SUObjectType syntheticType =
                            universe.newObjectType(mutation.syntheticTypeName);
                    SUField valueField = universe.newField("value");
                    builder.addAdditionalType(syntheticType)
                            .addField(syntheticType, valueField)
                            .setFieldType(valueField, stringType);
                    break;
                case ADD_SYNTHETIC_FIELD:
                    SUObjectType typeForAddition =
                            requireNonNull(current.getObjectType(mutation.syntheticTypeName));
                    SUField syntheticField =
                            universe.newField(mutation.syntheticFieldName);
                    builder.addField(typeForAddition, syntheticField)
                            .setFieldType(syntheticField, stringType);
                    break;
                case REMOVE_SYNTHETIC_FIELD:
                    SUObjectType typeForRemoval =
                            requireNonNull(current.getObjectType(mutation.syntheticTypeName));
                    builder.removeField(typeForRemoval, mutation.syntheticFieldName);
                    break;
                case ADD_REPLACEMENT_FIELD:
                    SUObjectType typeForReplacement =
                            requireNonNull(current.getObjectType(mutation.syntheticTypeName));
                    SUField replacementField =
                            universe.newField(mutation.replacementFieldName);
                    builder.addField(typeForReplacement, replacementField)
                            .setFieldType(replacementField, stringType);
                    break;
                case REMOVE_SYNTHETIC_TYPE:
                    builder.removeAdditionalType(mutation.syntheticTypeName);
                    break;
                default:
                    throw new IllegalStateException("Unhandled mutation " + mutation.kind);
            }

            int touchedTargetCount = 0;
            for (String targetTypeName : plan.directiveTargetTypeNames) {
                SUObjectType target =
                        requireNonNull(current.getObjectType(targetTypeName));
                List<SUAppliedDirective> existingDirectives =
                        current.getAppliedDirectives(target, BENCHMARK_DIRECTIVE_NAME);
                if (existingDirectives.size() != expectedExistingDirectives) {
                    throw new IllegalStateException(String.format(
                            Locale.ROOT,
                            "Expected %d @%s directives on %s before version %d, found %d",
                            expectedExistingDirectives,
                            BENCHMARK_DIRECTIVE_NAME,
                            targetTypeName,
                            version,
                            existingDirectives.size()));
                }
                for (SUAppliedDirective existingDirective : existingDirectives) {
                    builder.removeAppliedDirective(target, existingDirective);
                }

                SUAppliedDirectiveArgument versionArgument =
                        universe.newAppliedDirectiveArgument(
                                BENCHMARK_DIRECTIVE_VERSION_ARGUMENT,
                                intType,
                                InputValueWithState.newExternalValue(version));
                SUAppliedDirective appliedDirective =
                        universe.newAppliedDirective(
                                BENCHMARK_DIRECTIVE_NAME,
                                List.of(versionArgument));
                builder.addAppliedDirective(target, appliedDirective);
                touchedTargetCount++;
            }
            assertTouchedDirectiveTargets("SUSchema", version, touchedTargetCount, plan);
        });
    }

    private static void verifyFinalGraphQLSchema(
            GraphQLSchema baseSchema,
            GraphQLSchema finalSchema,
            List<GraphQLSchema> schemas,
            MutationPlan plan) {
        if (schemas.size() != plan.mutations.size() + 1) {
            throw new IllegalStateException(String.format(
                    Locale.ROOT,
                    "Expected %d modified GraphQLSchema versions",
                    plan.mutations.size()));
        }
        if (!fieldNames(baseSchema.getQueryType())
                .equals(fieldNames(finalSchema.getQueryType()))) {
            throw new IllegalStateException("Final GraphQLSchema query differs from the base");
        }
        verifyFinalSyntheticType(finalSchema, plan);
        verifyFinalGraphQLDirectives(finalSchema, plan);
    }

    private static void verifyFinalSUSchema(
            SchemaUniverse universe,
            SUSchema baseSchema,
            SUSchema finalSchema,
            MutationPlan plan) {
        if (universe.getSchemas().size() != plan.mutations.size() + 1) {
            throw new IllegalStateException(String.format(
                    Locale.ROOT,
                    "Expected %d modified SUSchema versions",
                    plan.mutations.size()));
        }
        if (!fieldNames(baseSchema).equals(fieldNames(finalSchema))) {
            throw new IllegalStateException("Final SUSchema query differs from the base");
        }
        verifyFinalSyntheticType(finalSchema, plan);
        verifyFinalSUDirectives(finalSchema, plan);
    }

    private static void verifyFinalSyntheticType(
            GraphQLSchema schema,
            MutationPlan plan) {
        int mutationCount = plan.mutations.size();
        int finalCycle = (mutationCount - 1) / EditKind.values().length;
        boolean shouldBePresent = mutationCount % EditKind.values().length != 0;
        boolean isPresent = schema.getType(SYNTHETIC_PREFIX + "Type" + finalCycle) != null;
        if (isPresent != shouldBePresent) {
            throw new IllegalStateException("Final synthetic GraphQLSchema type state differs");
        }
    }

    private static void verifyFinalSyntheticType(
            SUSchema schema,
            MutationPlan plan) {
        int mutationCount = plan.mutations.size();
        int finalCycle = (mutationCount - 1) / EditKind.values().length;
        boolean shouldBePresent = mutationCount % EditKind.values().length != 0;
        boolean isPresent = schema.getType(SYNTHETIC_PREFIX + "Type" + finalCycle) != null;
        if (isPresent != shouldBePresent) {
            throw new IllegalStateException("Final synthetic SUSchema type state differs");
        }
    }

    private static void verifyFinalGraphQLDirectives(
            GraphQLSchema schema,
            MutationPlan plan) {
        int expectedVersion = plan.mutations.size();
        for (String targetTypeName : plan.directiveTargetTypeNames) {
            GraphQLObjectType target = requireNonNull(schema.getObjectType(targetTypeName));
            List<GraphQLAppliedDirective> directives =
                    target.getAppliedDirectives(BENCHMARK_DIRECTIVE_NAME);
            if (directives.size() != 1
                    || !Integer.valueOf(expectedVersion).equals(requireNonNull(
                            directives.get(0).getArgument(BENCHMARK_DIRECTIVE_VERSION_ARGUMENT))
                            .getValue())) {
                throw new IllegalStateException(
                        "Final GraphQLSchema directive differs on " + targetTypeName);
            }
        }
    }

    private static void verifyFinalSUDirectives(
            SUSchema schema,
            MutationPlan plan) {
        int expectedVersion = plan.mutations.size();
        for (String targetTypeName : plan.directiveTargetTypeNames) {
            SUObjectType target = requireNonNull(schema.getObjectType(targetTypeName));
            List<SUAppliedDirective> directives =
                    schema.getAppliedDirectives(target, BENCHMARK_DIRECTIVE_NAME);
            if (directives.size() != 1) {
                throw new IllegalStateException(
                        "Final SUSchema directive differs on " + targetTypeName);
            }
            SUAppliedDirectiveArgument argument = requireNonNull(schema.getArgument(
                    directives.get(0),
                    BENCHMARK_DIRECTIVE_VERSION_ARGUMENT));
            if (!Integer.valueOf(expectedVersion).equals(argument.getArgumentValue().getValue())) {
                throw new IllegalStateException(
                        "Final SUSchema directive argument differs on " + targetTypeName);
            }
        }
    }

    private static void assertTouchedDirectiveTargets(
            String model,
            int version,
            int touchedTargetCount,
            MutationPlan plan) {
        if (touchedTargetCount != plan.directiveTargetTypeNames.size()
                || touchedTargetCount < DIRECTIVE_TARGET_COUNT) {
            throw new IllegalStateException(String.format(
                    Locale.ROOT,
                    "%s version %d touched %d directive targets; expected at least %d",
                    model,
                    version,
                    touchedTargetCount,
                    DIRECTIVE_TARGET_COUNT));
        }
    }

    private static Set<String> fieldNames(GraphQLObjectType query) {
        Set<String> result = new LinkedHashSet<>();
        for (GraphQLFieldDefinition field : query.getFieldDefinitions()) {
            result.add(field.getName());
        }
        return result;
    }

    private static Set<String> fieldNames(SUSchema schema) {
        Set<String> result = new LinkedHashSet<>();
        for (SUField field : schema.getFields(schema.getQueryType())) {
            result.add(requireNonNull(field.getName()));
        }
        return result;
    }

    private static void verifyEquivalentVersions(
            GraphQLVersionSet graphQLVersions,
            List<SUSchema> suVersions) {
        List<GraphQLSchema> expectedVersions = graphQLVersions.getSchemas();
        if (expectedVersions.size() != suVersions.size()) {
            throw new IllegalStateException(String.format(
                    Locale.ROOT,
                    "Version count differs: GraphQLSchema=%d SUSchema=%d",
                    expectedVersions.size(),
                    suVersions.size()));
        }

        SchemaPrinter.Options options = SchemaPrinter.Options.defaultOptions()
                .includeSchemaDefinition(true)
                .includeScalarTypes(true)
                .setComparators(GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY);
        SchemaPrinter printer = new SchemaPrinter(options);
        for (int i = 0; i < expectedVersions.size(); i++) {
            String expected = printer.print(expectedVersions.get(i));
            String actual = printer.print(suVersions.get(i).toGraphQLSchema());
            if (!expected.equals(actual)) {
                throw new IllegalStateException(schemaDifference(i, expected, actual));
            }
            System.out.printf(
                    Locale.ROOT,
                    "SCHEMA_EQUIVALENCE version=%d/%d matched%n",
                    i,
                    expectedVersions.size() - 1);
        }
        System.out.printf(
                Locale.ROOT,
                "SCHEMA_EQUIVALENCE allVersions=%d matched%n",
                expectedVersions.size());
    }

    private static String schemaDifference(
            int version,
            String expected,
            String actual) {
        int commonLength = Math.min(expected.length(), actual.length());
        int offset = 0;
        while (offset < commonLength
                && expected.charAt(offset) == actual.charAt(offset)) {
            offset++;
        }
        int start = Math.max(0, offset - 80);
        int expectedEnd = Math.min(expected.length(), offset + 80);
        int actualEnd = Math.min(actual.length(), offset + 80);
        return String.format(
                Locale.ROOT,
                "Schema version %d differs at character %d:%nexpected: %s%nactual:   %s",
                version,
                offset,
                printable(expected.substring(start, expectedEnd)),
                printable(actual.substring(start, actualEnd)));
    }

    private static String printable(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    private static GraphQLSchema loadBaseSchema() {
        String schema = BenchmarkUtils.loadResource("large-schema-5.graphqls.part1")
                + BenchmarkUtils.loadResource("large-schema-5.graphqls.part2");
        GraphQLSchema generatedSchema = SchemaGenerator.createdMockedSchema(schema);
        if (generatedSchema.getDirective(BENCHMARK_DIRECTIVE_NAME) != null) {
            throw new IllegalStateException(
                    "Benchmark directive already exists: " + BENCHMARK_DIRECTIVE_NAME);
        }
        GraphQLDirective benchmarkDirective = GraphQLDirective.newDirective()
                .name(BENCHMARK_DIRECTIVE_NAME)
                .argument(GraphQLArgument.newArgument()
                        .name(BENCHMARK_DIRECTIVE_VERSION_ARGUMENT)
                        .type(Scalars.GraphQLInt))
                .validLocation(DirectiveLocation.OBJECT)
                .build();
        return generatedSchema.transform(builder ->
                builder.additionalDirective(benchmarkDirective));
    }

    private static long retainedSize(Object root) {
        return GraphLayout.parseInstance(root).totalSize();
    }

    private static void reportFootprint(
            String model,
            long baseBytes,
            long totalBytes) {
        long incrementalBytes = totalBytes - baseBytes;
        System.out.printf(
                Locale.ROOT,
                "RETAINED_MEMORY model=%s modifiedVersions=%d baseBytes=%d "
                        + "totalBytes=%d incrementalBytes=%d incrementalBytesPerVersion=%.2f "
                        + "baseMiB=%.2f totalMiB=%.2f incrementalMiB=%.2f%n",
                model,
                VERSION_COUNT,
                baseBytes,
                totalBytes,
                incrementalBytes,
                incrementalBytes / (double) VERSION_COUNT,
                baseBytes / BYTES_PER_MIB,
                totalBytes / BYTES_PER_MIB,
                incrementalBytes / BYTES_PER_MIB);
    }

    public static final class GraphQLVersionSet {
        private final List<GraphQLSchema> schemas;

        GraphQLVersionSet(List<GraphQLSchema> schemas) {
            this.schemas = List.copyOf(schemas);
        }

        public List<GraphQLSchema> getSchemas() {
            return schemas;
        }
    }

    private enum EditKind {
        ADD_SYNTHETIC_TYPE,
        ADD_SYNTHETIC_FIELD,
        REMOVE_SYNTHETIC_FIELD,
        ADD_REPLACEMENT_FIELD,
        REMOVE_SYNTHETIC_TYPE
    }

    private static final class Mutation {
        private final EditKind kind;
        private final String syntheticFieldName;
        private final String replacementFieldName;
        private final String syntheticTypeName;

        private Mutation(
                EditKind kind,
                String syntheticFieldName,
                String replacementFieldName,
                String syntheticTypeName) {
            this.kind = kind;
            this.syntheticFieldName = syntheticFieldName;
            this.replacementFieldName = replacementFieldName;
            this.syntheticTypeName = syntheticTypeName;
        }
    }

    private static final class MutationPlan {
        private final List<Mutation> mutations;
        private final List<String> directiveTargetTypeNames;
        private final Set<String> directiveTargetTypeNameSet;

        private MutationPlan(
                List<Mutation> mutations,
                List<String> directiveTargetTypeNames) {
            this.mutations = List.copyOf(mutations);
            this.directiveTargetTypeNames = List.copyOf(directiveTargetTypeNames);
            this.directiveTargetTypeNameSet = Set.copyOf(directiveTargetTypeNames);
        }

        private static MutationPlan create(GraphQLSchema schema) {
            List<Mutation> mutations = new ArrayList<>(VERSION_COUNT);
            for (int cycle = 0; cycle < CYCLE_COUNT; cycle++) {
                String syntheticFieldName =
                        SYNTHETIC_PREFIX + "Field" + cycle;
                String replacementFieldName =
                        SYNTHETIC_PREFIX + "Replacement" + cycle;
                String syntheticTypeName =
                        SYNTHETIC_PREFIX + "Type" + cycle;
                if (schema.getType(syntheticTypeName) != null) {
                    throw new IllegalStateException(
                            "Synthetic benchmark type already exists: " + syntheticTypeName);
                }
                for (EditKind kind : EditKind.values()) {
                    mutations.add(new Mutation(
                            kind,
                            syntheticFieldName,
                            replacementFieldName,
                            syntheticTypeName));
                }
            }
            List<String> directiveTargetTypeNames =
                    selectDirectiveTargetTypeNames(schema);
            return new MutationPlan(mutations, directiveTargetTypeNames);
        }

        private MutationPlan firstVersions(int versionCount) {
            if (versionCount == mutations.size()) {
                return this;
            }
            return new MutationPlan(
                    mutations.subList(0, versionCount),
                    directiveTargetTypeNames);
        }

        private static List<String> selectDirectiveTargetTypeNames(GraphQLSchema schema) {
            List<String> objectTypeNames = new ArrayList<>();
            for (GraphQLNamedType type : schema.getAllTypesAsList()) {
                if (type instanceof GraphQLObjectType
                        && !type.getName().startsWith("__")) {
                    objectTypeNames.add(type.getName());
                }
            }
            objectTypeNames.sort(String::compareTo);
            if (objectTypeNames.size() < DIRECTIVE_TARGET_COUNT) {
                throw new IllegalStateException(String.format(
                        Locale.ROOT,
                        "Schema has only %d eligible directive targets; expected at least %d",
                        objectTypeNames.size(),
                        DIRECTIVE_TARGET_COUNT));
            }

            List<String> selected = new ArrayList<>(DIRECTIVE_TARGET_COUNT);
            for (int i = 0; i < DIRECTIVE_TARGET_COUNT; i++) {
                int index = (int) ((long) i * objectTypeNames.size()
                        / DIRECTIVE_TARGET_COUNT);
                selected.add(objectTypeNames.get(index));
            }
            if (new LinkedHashSet<>(selected).size() != DIRECTIVE_TARGET_COUNT) {
                throw new IllegalStateException("Directive target selection contains duplicates");
            }
            return selected;
        }
    }
}

package benchmark;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys;
import graphql.execution.preparsed.persisted.InMemoryPersistedQueryCache;
import graphql.execution.preparsed.persisted.PersistedQueryCache;
import graphql.execution.preparsed.persisted.PersistedQuerySupport;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static graphql.Scalars.GraphQLString;

/**
 * Measures the graphql-java engine's core execution overhead (field resolution,
 * type checking, result building) with a balanced, realistic workload while
 * minimising data-fetching work.
 * <p>
 * Schema: 20 object types across 4 depth levels (5 types per level).
 * Query shape: ~530 queried fields, ~2000 result scalar values.
 * Width (~7 fields per selection set) ≈ Depth (5 levels).
 * <p>
 * Two variants: baseline (PropertyDataFetcher with embedded Maps) and
 * DataLoader (child fields resolved via batched DataLoader calls).
 */
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(2)
public class ExecutionBenchmark {

    // 4 levels of object types below Query → total query depth = 5
    private static final int LEVELS = 4;
    // 5 types per level = 20 types total
    private static final int TYPES_PER_LEVEL = 5;
    // Intermediate types: 5 scalar fields + child_a + child_b = 7 selections
    private static final int SCALAR_FIELDS = 5;
    // Leaf types: 7 scalar fields
    private static final int LEAF_SCALAR_FIELDS = 7;
    // Query: 5 top-level fields (2 single + 3 list)
    private static final int QUERY_FIELDS = 5;
    private static final int QUERY_SINGLE_COUNT = 2;
    // List fields return 2 items each
    private static final int LIST_SIZE = 2;

    // Schema types shared by both variants: types[0] = L4 (leaf), types[LEVELS-1] = L1
    private static final GraphQLObjectType[][] schemaTypes = buildSchemaTypes();
    private static final GraphQLObjectType queryType = buildQueryType();
    static final String query = mkQuery();
    private static final String queryId = "exec-benchmark-query";

    // ---- Baseline variant (PropertyDataFetcher with embedded Maps) ----
    static final GraphQL graphQL = buildGraphQL();

    // ---- DataLoader variant ----
    // levelStores[i] holds all DTOs at schema level i+1 (index 0 = L1, 3 = L4)
    @SuppressWarnings("unchecked")
    private static final Map<String, Map<String, Object>>[] levelStores = new Map[LEVELS];
    static {
        for (int i = 0; i < LEVELS; i++) {
            levelStores[i] = new HashMap<>();
        }
    }
    static final GraphQL graphQLWithDL = buildGraphQLWithDataLoader();
    private static final ExecutorService batchLoadExecutor = Executors.newCachedThreadPool();
    private static final BatchLoader<String, Map<String, Object>> batchLoaderL2 =
            keys -> CompletableFuture.supplyAsync(
                    () -> keys.stream().map(k -> levelStores[1].get(k)).collect(Collectors.toList()),
                    batchLoadExecutor);
    private static final BatchLoader<String, Map<String, Object>> batchLoaderL3 =
            keys -> CompletableFuture.supplyAsync(
                    () -> keys.stream().map(k -> levelStores[2].get(k)).collect(Collectors.toList()),
                    batchLoadExecutor);
    private static final BatchLoader<String, Map<String, Object>> batchLoaderL4 =
            keys -> CompletableFuture.supplyAsync(
                    () -> keys.stream().map(k -> levelStores[3].get(k)).collect(Collectors.toList()),
                    batchLoadExecutor);

    // ================ Benchmark methods ================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public ExecutionResult benchmarkThroughput() {
        return execute();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public ExecutionResult benchmarkAvgTime() {
        return execute();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public ExecutionResult benchmarkDataLoaderThroughput() {
        return executeWithDataLoader();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public ExecutionResult benchmarkDataLoaderAvgTime() {
        return executeWithDataLoader();
    }

    private static ExecutionResult execute() {
        return graphQL.execute(query);
    }

    private static ExecutionResult executeWithDataLoader() {
        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register("dl_2", DataLoaderFactory.newDataLoader(batchLoaderL2))
                .register("dl_3", DataLoaderFactory.newDataLoader(batchLoaderL3))
                .register("dl_4", DataLoaderFactory.newDataLoader(batchLoaderL4))
                .build();
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query(query)
                .dataLoaderRegistry(registry)
                .build();
        input.getGraphQLContext().put(
                DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING, true);
        return graphQLWithDL.execute(input);
    }

    // ================ Query generation ================

    static String mkQuery() {
        StringBuilder sb = new StringBuilder();
        sb.append("{ ");
        for (int i = 1; i <= QUERY_FIELDS; i++) {
            sb.append("field_").append(i).append(" ");
            appendSelection(sb, 1);
            sb.append(" ");
        }
        sb.append("}");
        return sb.toString();
    }

    private static void appendSelection(StringBuilder sb, int level) {
        sb.append("{ ");
        if (level < LEVELS) {
            for (int f = 1; f <= SCALAR_FIELDS; f++) {
                sb.append("s").append(f).append(" ");
            }
            sb.append("child_a ");
            appendSelection(sb, level + 1);
            sb.append(" child_b ");
            appendSelection(sb, level + 1);
        } else {
            // leaf level
            for (int f = 1; f <= LEAF_SCALAR_FIELDS; f++) {
                sb.append("s").append(f).append(" ");
            }
        }
        sb.append("}");
    }

    // ================ Schema types (shared) ================

    private static GraphQLObjectType[][] buildSchemaTypes() {
        GraphQLObjectType[][] types = new GraphQLObjectType[LEVELS][TYPES_PER_LEVEL];

        // Leaf types (level 4): 7 scalar fields each
        for (int i = 0; i < TYPES_PER_LEVEL; i++) {
            List<GraphQLFieldDefinition> fields = new ArrayList<>();
            for (int f = 1; f <= LEAF_SCALAR_FIELDS; f++) {
                fields.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name("s" + f).type(GraphQLString).build());
            }
            types[0][i] = GraphQLObjectType.newObject()
                    .name("Type_L4_" + (i + 1)).fields(fields).build();
        }

        // Intermediate types (levels 3 down to 1)
        for (int lvlIdx = 1; lvlIdx < LEVELS; lvlIdx++) {
            GraphQLObjectType[] childLevel = types[lvlIdx - 1];
            int schemaLevel = LEVELS - lvlIdx; // naming: L3, L2, L1
            for (int i = 0; i < TYPES_PER_LEVEL; i++) {
                List<GraphQLFieldDefinition> fields = new ArrayList<>();
                for (int f = 1; f <= SCALAR_FIELDS; f++) {
                    fields.add(GraphQLFieldDefinition.newFieldDefinition()
                            .name("s" + f).type(GraphQLString).build());
                }
                fields.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name("child_a").type(childLevel[i]).build());
                fields.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name("child_b")
                        .type(GraphQLList.list(childLevel[(i + 1) % TYPES_PER_LEVEL]))
                        .build());
                types[lvlIdx][i] = GraphQLObjectType.newObject()
                        .name("Type_L" + schemaLevel + "_" + (i + 1)).fields(fields).build();
            }
        }
        return types;
    }

    private static GraphQLObjectType buildQueryType() {
        GraphQLObjectType[] l1Types = schemaTypes[LEVELS - 1];
        List<GraphQLFieldDefinition> queryFields = new ArrayList<>();
        for (int i = 0; i < QUERY_FIELDS; i++) {
            if (i < QUERY_SINGLE_COUNT) {
                queryFields.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name("field_" + (i + 1)).type(l1Types[i]).build());
            } else {
                queryFields.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name("field_" + (i + 1))
                        .type(GraphQLList.list(l1Types[i])).build());
            }
        }
        return GraphQLObjectType.newObject().name("Query").fields(queryFields).build();
    }

    // ================ Baseline variant ================

    private static GraphQL buildGraphQL() {
        GraphQLCodeRegistry.Builder codeRegistry = GraphQLCodeRegistry.newCodeRegistry();
        for (int i = 0; i < QUERY_FIELDS; i++) {
            final Object data;
            if (i < QUERY_SINGLE_COUNT) {
                data = buildEmbeddedDto(1, i);
            } else {
                List<Map<String, Object>> list = new ArrayList<>(LIST_SIZE);
                for (int l = 0; l < LIST_SIZE; l++) {
                    list.add(buildEmbeddedDto(1, i));
                }
                data = list;
            }
            DataFetcher<?> fetcher = env -> data;
            codeRegistry.dataFetcher(
                    FieldCoordinates.coordinates("Query", "field_" + (i + 1)), fetcher);
        }

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .codeRegistry(codeRegistry.build())
                .build();
        return GraphQL.newGraphQL(schema)
                .preparsedDocumentProvider(newPersistedQueryProvider())
                .build();
    }

    /**
     * Recursively builds a nested Map DTO with children embedded directly.
     * Sub-fields resolved by the default {@code PropertyDataFetcher}.
     */
    private static Map<String, Object> buildEmbeddedDto(int level, int typeIndex) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (level == LEVELS) {
            for (int f = 1; f <= LEAF_SCALAR_FIELDS; f++) {
                dto.put("s" + f, "L" + level + "_" + (typeIndex + 1) + "_s" + f);
            }
        } else {
            for (int f = 1; f <= SCALAR_FIELDS; f++) {
                dto.put("s" + f, "L" + level + "_" + (typeIndex + 1) + "_s" + f);
            }
            dto.put("child_a", buildEmbeddedDto(level + 1, typeIndex));
            int listTypeIdx = (typeIndex + 1) % TYPES_PER_LEVEL;
            List<Map<String, Object>> list = new ArrayList<>(LIST_SIZE);
            for (int l = 0; l < LIST_SIZE; l++) {
                list.add(buildEmbeddedDto(level + 1, listTypeIdx));
            }
            dto.put("child_b", list);
        }
        return dto;
    }

    // ================ DataLoader variant ================

    private static int dlIdCounter = 0;

    private static GraphQL buildGraphQLWithDataLoader() {
        GraphQLCodeRegistry.Builder codeRegistry = GraphQLCodeRegistry.newCodeRegistry();

        // Query-level fetchers: return pre-built L1 DTOs directly
        for (int i = 0; i < QUERY_FIELDS; i++) {
            final Object data;
            if (i < QUERY_SINGLE_COUNT) {
                String id = buildDtoForDL(1, i);
                data = levelStores[0].get(id);
            } else {
                List<Map<String, Object>> list = new ArrayList<>(LIST_SIZE);
                for (int l = 0; l < LIST_SIZE; l++) {
                    String id = buildDtoForDL(1, i);
                    list.add(levelStores[0].get(id));
                }
                data = list;
            }
            DataFetcher<?> fetcher = env -> data;
            codeRegistry.dataFetcher(
                    FieldCoordinates.coordinates("Query", "field_" + (i + 1)), fetcher);
        }

        // child_a / child_b fetchers on intermediate types → resolve via DataLoader
        for (int lvlIdx = 1; lvlIdx < LEVELS; lvlIdx++) {
            int schemaLevel = LEVELS - lvlIdx;      // L3, L2, L1
            int childSchemaLevel = schemaLevel + 1;  // L4, L3, L2
            final String dlName = "dl_" + childSchemaLevel;

            for (int i = 0; i < TYPES_PER_LEVEL; i++) {
                String typeName = schemaTypes[lvlIdx][i].getName();

                codeRegistry.dataFetcher(
                        FieldCoordinates.coordinates(typeName, "child_a"),
                        (DataFetcher<?>) env -> {
                            Map<String, Object> source = env.getSource();
                            String childId = (String) source.get("child_a_id");
                            return env.<String, Map<String, Object>>getDataLoader(dlName)
                                    .load(childId);
                        });

                codeRegistry.dataFetcher(
                        FieldCoordinates.coordinates(typeName, "child_b"),
                        (DataFetcher<?>) env -> {
                            Map<String, Object> source = env.getSource();
                            @SuppressWarnings("unchecked")
                            List<String> childIds = (List<String>) source.get("child_b_ids");
                            return env.<String, Map<String, Object>>getDataLoader(dlName)
                                    .loadMany(childIds);
                        });
            }
        }

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .codeRegistry(codeRegistry.build())
                .build();
        return GraphQL.newGraphQL(schema)
                .preparsedDocumentProvider(newPersistedQueryProvider())
                .build();
    }

    /**
     * Recursively builds a DTO with child references stored as IDs.
     * Each DTO is stored in its level's store. Returns the assigned ID.
     */
    private static String buildDtoForDL(int level, int typeIndex) {
        String id = "n_" + (dlIdCounter++);
        Map<String, Object> dto = new LinkedHashMap<>();

        if (level == LEVELS) {
            // leaf: scalar fields only
            for (int f = 1; f <= LEAF_SCALAR_FIELDS; f++) {
                dto.put("s" + f, "L" + level + "_" + (typeIndex + 1) + "_s" + f);
            }
        } else {
            // intermediate: scalar fields + child IDs
            for (int f = 1; f <= SCALAR_FIELDS; f++) {
                dto.put("s" + f, "L" + level + "_" + (typeIndex + 1) + "_s" + f);
            }
            dto.put("child_a_id", buildDtoForDL(level + 1, typeIndex));
            int listTypeIdx = (typeIndex + 1) % TYPES_PER_LEVEL;
            List<String> childBIds = new ArrayList<>(LIST_SIZE);
            for (int l = 0; l < LIST_SIZE; l++) {
                childBIds.add(buildDtoForDL(level + 1, listTypeIdx));
            }
            dto.put("child_b_ids", childBIds);
        }

        levelStores[level - 1].put(id, dto);
        return id;
    }

    // ================ Persisted query cache ================

    private static PersistedQuery newPersistedQueryProvider() {
        return new PersistedQuery(
                InMemoryPersistedQueryCache
                        .newInMemoryPersistedQueryCache()
                        .addQuery(queryId, query)
                        .build()
        );
    }

    static class PersistedQuery extends PersistedQuerySupport {
        public PersistedQuery(PersistedQueryCache persistedQueryCache) {
            super(persistedQueryCache);
        }

        @Override
        protected Optional<Object> getPersistedQueryId(ExecutionInput executionInput) {
            return Optional.of(queryId);
        }
    }

    // ================ Main ================

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include("benchmark.ExecutionBenchmark")
                .build();
        new Runner(opt).run();
    }
}

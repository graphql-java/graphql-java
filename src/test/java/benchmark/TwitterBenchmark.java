package benchmark;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.github.javafaker.Code;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.instrumentation.tracing.TracingInstrumentation;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.execution.preparsed.persisted.InMemoryPersistedQueryCache;
import graphql.execution.preparsed.persisted.PersistedQueryCache;
import graphql.execution.preparsed.persisted.PersistedQuerySupport;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactory;
import graphql.schema.DataFetcherFactoryEnvironment;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaGeneratorHelper;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

@Warmup(iterations = 8, time = 10)
@Measurement(iterations = 25, time = 10)
@Fork(1)
@Threads(1)
public class TwitterBenchmark {
  private static final int BREADTH = 150;
  private static final int DEPTH = 150;

  static String query = mkQuery();
  static Object queryId = "QUERY_ID";
  static GraphQL graphQL = buildGraphQL();

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void execute(Blackhole bh) {
    bh.consume(execute());
  }

  private static ExecutionResult execute() {
    return graphQL.execute(query);
  }

  public static String mkQuery() {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    for (int d=1; d <= DEPTH; d++) {
      for (int b=1; b <= BREADTH; b++) {
        sb.append("leaf_");
        sb.append(b);
        sb.append(" ");
      }
      if (d < DEPTH) {
        sb.append("branch { ");
      }
    }
    for (int d=1; d <= DEPTH; d++) {
      sb.append("}");
    }
    return sb.toString();
  }

  private static GraphQL buildGraphQL() {
    List<GraphQLFieldDefinition> leafFields = new ArrayList<>(BREADTH);
    for (int i = 1; i <= BREADTH; i++) {
      leafFields.add(
        GraphQLFieldDefinition.newFieldDefinition()
        .name("leaf_" + i)
        .type(GraphQLString)
        .build()
      );
    }

    GraphQLObjectType branchType = GraphQLObjectType.newObject()
      .name("Branch")
      .fields(leafFields)
      .field(GraphQLFieldDefinition.newFieldDefinition()
        .name("branch")
        .type(GraphQLTypeReference.typeRef("Branch")))
      .build();


    DataFetcher<Object> simpleFetcher = env -> env.getField().getName();
    GraphQLCodeRegistry codeReg = GraphQLCodeRegistry.newCodeRegistry()
        .defaultDataFetcher(
          new DataFetcherFactory<Object>() {
            @Override
            public DataFetcher<Object> get(DataFetcherFactoryEnvironment environment) {
              return simpleFetcher;
            }
          }
        )
        .build();

    GraphQLSchema graphQLSchema = GraphQLSchema.newSchema()
        .query(branchType)
        .codeRegistry(codeReg)
        .build();

    return GraphQL
        .newGraphQL(graphQLSchema)
        .preparsedDocumentProvider(
            new PersistedQuery(
              InMemoryPersistedQueryCache
                .newInMemoryPersistedQueryCache()
                .addQuery(queryId, query)
                .build()
            )
        )
        .build();
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

  public static void main(String[] args) {
    ExecutionResult result = execute();
    int i = 0;
  }
}

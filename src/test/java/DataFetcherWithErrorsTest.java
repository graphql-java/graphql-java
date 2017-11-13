import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import graphql.language.SourceLocation;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class DataFetcherWithErrorsTest {

    @Test
    public void testDataFetcherWithErrors() {
        GraphQLError error = new GraphQLError() {

            @Override
            public String getMessage() {
                return "badField is bad";
            }

            @Override
            public List<SourceLocation> getLocations() {
                return singletonList(new SourceLocation(2, 10));
            }

            @Override
            public ErrorType getErrorType() {
                return ErrorType.DataFetchingException;
            }

            @Override
            public List<Object> getPath() {
                return Arrays.asList("child", "badField");
            }
        };
        DataFetcher dataFetcher = environment -> new DataFetcherResult<Map>(
                singletonMap("child",
                        new HashMap<String, Object>() {{
                            put("goodField", "good");
                            put("badField", null);
                        }}
                ),
                singletonList(error)
        );

        String schema = "type Query{root: Root}\n" +
                "type Root{ parent: Parent}\n" +
                "type Parent{ child: Child}\n" +
                "type Child{\n" +
                "  goodField: String,\n" +
                "  badField: String\n" +
                "}";
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
        RuntimeWiring runtimeWiring = newRuntimeWiring()
                .type("Root", builder -> builder.dataFetcher("parent", dataFetcher))
                .type("Query", builder -> builder.dataFetcher("root", new StaticDataFetcher(emptyMap())))
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        GraphQL build = GraphQL.newGraphQL(graphQLSchema).build();
        ExecutionResult executionResult = build.execute("{root{parent{child{goodField, badField}}}}");
        System.out.println(executionResult.getData().toString());

        assertEquals(1, executionResult.getErrors().size());
        GraphQLError firstError = executionResult.getErrors().get(0);
        assertEquals(Arrays.asList("root", "parent", "child", "badField"), firstError.getPath());
        assertEquals("badField is bad", firstError.getMessage());
        assertEquals(singletonList(new SourceLocation(3, 17)), firstError.getLocations());

        Map<String, String> child = ((Map<String, Map<String, Map<String, Map<String, String>>>>) executionResult.getData()).get("root").get("parent").get("child");
        assertEquals("good", child.get("goodField"));
        assertEquals(null, child.get("badField"));

    }
}

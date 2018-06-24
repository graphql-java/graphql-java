package readme;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactories;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"Convert2Lambda", "unused", "ClassCanBeStatic"})
public class DirectivesExamples {

    static class AuthorisationCtx {
        boolean hasRole(String roleName) {
            return true;
        }

        static AuthorisationCtx obtain() {
            return null;
        }
    }

    class AuthorisationDirective implements SchemaDirectiveWiring {

        @Override
        public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> schemaDirectiveWiringEnv) {
            String targetAuthRole = (String) schemaDirectiveWiringEnv.getDirective().getArgument("role").getValue();

            GraphQLFieldDefinition field = schemaDirectiveWiringEnv.getElement();
            //
            // build a data fetcher that first checks authorisation roles before then calling the original data fetcher
            //
            DataFetcher originalDataFetcher = field.getDataFetcher();
            DataFetcher authDataFetcher = new DataFetcher() {
                @Override
                public Object get(DataFetchingEnvironment dataFetchingEnvironment) {
                    Map<String, Object> contextMap = dataFetchingEnvironment.getContext();
                    AuthorisationCtx authContext = (AuthorisationCtx) contextMap.get("authContext");

                    if (authContext.hasRole(targetAuthRole)) {
                        return originalDataFetcher.get(dataFetchingEnvironment);
                    } else {
                        return null;
                    }
                }
            };
            //
            // now change the field definition to have the new authorising data fetcher
            return field.transform(builder -> builder.dataFetcher(authDataFetcher));
        }
    }

    void authWiring() {

        //
        // we wire this into the runtime by directive name
        //
        RuntimeWiring.newRuntimeWiring()
                .directive("auth", new AuthorisationDirective())
                .build();

    }

    String query = "";

    void contextWiring() {

        AuthorisationCtx authCtx = AuthorisationCtx.obtain();

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .context(authCtx)
                .build();
    }


    public static class DateFormatting implements SchemaDirectiveWiring {
        @Override
        public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            GraphQLFieldDefinition field = environment.getElement();
            //
            // DataFetcherFactories.wrapDataFetcher is a helper to wrap data fetchers so that CompletionStage is handled correctly
            // along with POJOs
            //
            DataFetcher dataFetcher = DataFetcherFactories.wrapDataFetcher(field.getDataFetcher(), ((dataFetchingEnvironment, value) -> {
                DateTimeFormatter dateTimeFormatter = buildFormatter(dataFetchingEnvironment.getArgument("format"));
                if (value instanceof LocalDateTime) {
                    return dateTimeFormatter.format((LocalDateTime) value);
                }
                return value;
            }));

            //
            // This will extend the field by adding a new "format" argument to it for the date formatting
            // which allows clients to opt into that as well as wrapping the base data fetcher so it
            // performs the formatting over top of the base values.
            //
            return field.transform(builder -> builder
                    .argument(GraphQLArgument
                            .newArgument()
                            .name("format")
                            .type(Scalars.GraphQLString)
                            .defaultValue("dd-MM-YYYY")
                    )
                    .dataFetcher(dataFetcher)
            );
        }

        private DateTimeFormatter buildFormatter(String format) {
            String dtFormat = format != null ? format : "dd-MM-YYYY";
            return DateTimeFormatter.ofPattern(dtFormat);
        }
    }


    static GraphQLSchema buildSchema() {

        String sdlSpec = "" +
                "type Query {\n" +
                "    dateField : String @dateFormat \n" +
                "}";

        TypeDefinitionRegistry registry = new SchemaParser().parse(sdlSpec);

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .directive("dateFormat", new DateFormatting())
                .build();

        return new SchemaGenerator().makeExecutableSchema(registry, runtimeWiring);
    }

    public static void main(String[] args) {
        GraphQLSchema schema = buildSchema();
        GraphQL graphql = GraphQL.newGraphQL(schema).build();

        Map<String, Object> root = new HashMap<>();
        root.put("dateField", LocalDateTime.of(1969, 10, 8, 0, 0));

        String query = "" +
                "query {\n" +
                "    default : dateField \n" +
                "    usa : dateField(format : \"MM-dd-YYYY\") \n" +
                "}";

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .root(root)
                .query(query)
                .build();

        ExecutionResult executionResult = graphql.execute(executionInput);
        Map<String, Object> data = executionResult.getData();

        // data['default'] == '08-10-1969'
        // data['usa'] == '10-08-1969'
    }
}

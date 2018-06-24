package example.http;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.StarWarsData;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.instrumentation.tracing.TracingInstrumentation;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.ExecutionInput.newExecutionInput;
import static graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions.newOptions;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static java.util.Arrays.asList;

/**
 * An very simple example of serving a qraphql schema over http.
 * <p>
 * More info can be found here : http://graphql.org/learn/serving-over-http/
 */
@SuppressWarnings("unchecked")
public class HttpMain extends AbstractHandler {

    static final int PORT = 3000;
    static GraphQLSchema starWarsSchema = null;

    public static void main(String[] args) throws Exception {
        //
        // This example uses Jetty as an embedded HTTP server
        Server server = new Server(PORT);
        //
        // In Jetty, handlers are how your get called backed on a request
        HttpMain main_handler = new HttpMain();

        ResourceHandler resource_handler = new ResourceHandler();
        resource_handler.setDirectoriesListed(false);
        resource_handler.setWelcomeFiles(new String[]{"index.html"});

        resource_handler.setResourceBase("./src/test/resources/httpmain");

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{resource_handler, main_handler});
        server.setHandler(handlers);

        server.start();

        server.join();
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        boolean handled = false;
        if ("/graphql".equals(target)) {
            handleStarWars(request, response);
            handled = true;
        } else if (target.startsWith("/executionresult")) {
            new ExecutionResultJSONTesting(target, response);
            handled = true;
        }
        if (handled) {
            baseRequest.setHandled(true);
        }
    }

    private void handleStarWars(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
        //
        // this builds out the parameters we need like the graphql query from the http request
        QueryParameters parameters = QueryParameters.from(httpRequest);
        if (parameters.getQuery() == null) {
            //
            // how to handle nonsensical requests is up to your application
            httpResponse.setStatus(400);
            return;
        }

        ExecutionInput.Builder executionInput = newExecutionInput()
                .query(parameters.getQuery())
                .operationName(parameters.getOperationName())
                .variables(parameters.getVariables());

        //
        // This example uses the DataLoader technique to ensure that the most efficient
        // loading of data (in this case StarWars characters) happens.  We pass that to data
        // fetchers via the graphql context object.
        //
        DataLoaderRegistry dataLoaderRegistry = buildDataLoaderRegistry();


        //
        // the context object is something that means something to down stream code.  It is instructions
        // from yourself to your other code such as DataFetchers.  The engine passes this on unchanged and
        // makes it available to inner code
        //
        // the graphql guidance says  :
        //
        //  - GraphQL should be placed after all authentication middleware, so that you
        //  - have access to the same session and user information you would in your
        //  - HTTP endpoint handlers.
        //
        Map<String, Object> context = new HashMap<>();
        context.put("YouAppSecurityClearanceLevel", "CodeRed");
        context.put("YouAppExecutingUser", "Dr Nefarious");
        context.put("dataloaderRegistry", dataLoaderRegistry);
        executionInput.context(context);

        //
        // you need a schema in order to execute queries
        GraphQLSchema schema = buildStarWarsSchema();

        DataLoaderDispatcherInstrumentation dlInstrumentation =
                new DataLoaderDispatcherInstrumentation(dataLoaderRegistry, newOptions().includeStatistics(true));

        Instrumentation instrumentation = new ChainedInstrumentation(
                asList(new TracingInstrumentation(), dlInstrumentation)
        );

        // finally you build a runtime graphql object and execute the query
        GraphQL graphQL = GraphQL
                .newGraphQL(schema)
                // instrumentation is pluggable
                .instrumentation(instrumentation)
                .build();
        ExecutionResult executionResult = graphQL.execute(executionInput.build());

        returnAsJson(httpResponse, executionResult);
    }


    private void returnAsJson(HttpServletResponse response, ExecutionResult executionResult) throws IOException {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        JsonKit.toJson(response, executionResult.toSpecification());
    }

    private DataLoaderRegistry buildDataLoaderRegistry() {
        BatchLoader<String, Object> friendsBatchLoader = keys ->
                //
                // we are using multi threading here.  Imagine if loadCharactersViaHTTP was
                // actually a HTTP call - its not be it could be done asynchronously as
                // a batch API call
                //
                CompletableFuture.supplyAsync(() ->
                        loadCharactersViaHTTP(keys));

        DataLoader<String, Object> friendsDataLoader = new DataLoader<>(friendsBatchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        //
        // we make sure our dataloader is in the registry
        dataLoaderRegistry.register("friends", friendsDataLoader);

        return dataLoaderRegistry;
    }

    private GraphQLSchema buildStarWarsSchema() {
        //
        // using lazy loading here ensure we can debug the schema generation
        // and potentially get "wired" components that cant be accessed
        // statically.
        //
        // A full application would use a dependency injection framework (like Spring)
        // to manage that lifecycle.
        //
        if (starWarsSchema == null) {

            //
            //
            // the fetcher of friends uses java-dataloader to make the circular friends fetching
            // more efficient by batching and caching the calls to load Character friends
            //
            DataFetcher friendsFetcher = environment -> {
                DataLoaderRegistry dataloaderRegistry = asMapGet(environment.getContext(), "dataloaderRegistry");
                DataLoader friendsDataLoader = dataloaderRegistry.getDataLoader("friends");

                List<String> friendIds = asMapGet(environment.getSource(), "friends");
                return friendsDataLoader.loadMany(friendIds);
            };


            //
            // reads a file that provides the schema types
            //
            Reader streamReader = loadSchemaFile("starWarsSchemaAnnotated.graphqls");
            TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(streamReader);

            //
            // the runtime wiring is used to provide the code that backs the
            // logical schema
            //
            TypeResolver characterTypeResolver = env -> {
                Map<String, Object> obj = (Map<String, Object>) env.getObject();
                String id = (String) obj.get("id");
                GraphQLSchema schema = env.getSchema();
                if (StarWarsData.isHuman(id)) {
                    return (GraphQLObjectType) schema.getType("Human");
                } else {
                    return (GraphQLObjectType) schema.getType("Droid");
                }
            };
            RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                    .type(newTypeWiring("Query")
                            .dataFetcher("hero", StarWarsData.getHeroDataFetcher())
                            .dataFetcher("human", StarWarsData.getHumanDataFetcher())
                            .dataFetcher("droid", StarWarsData.getDroidDataFetcher())
                    )
                    .type(newTypeWiring("Human")
                            .dataFetcher("friends", friendsFetcher)
                    )
                    .type(newTypeWiring("Droid")
                            .dataFetcher("friends", friendsFetcher)
                    )

                    .type(newTypeWiring("Character")
                            .typeResolver(characterTypeResolver)
                    )
                    .type(newTypeWiring("Episode")
                            .enumValues(StarWarsData.getEpisodeResolver())
                    )
                    .build();

            // finally combine the logical schema with the physical runtime
            starWarsSchema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring);
        }
        return starWarsSchema;
    }

    private List<Object> loadCharactersViaHTTP(List<String> keys) {
        List<Object> values = new ArrayList<>();
        for (String key : keys) {
            Object character = StarWarsData.getCharacter(key);
            values.add(character);
        }
        return values;
    }

    @SuppressWarnings("SameParameterValue")
    private Reader loadSchemaFile(String name) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        return new InputStreamReader(stream, Charset.defaultCharset());
    }

    // Lots of the data happens to be maps of objects and this allows us to get back into type safety land
    // with less boiler plat and casts
    //
    @SuppressWarnings("TypeParameterUnusedInFormals")
    private <T> T asMapGet(Object mapObj, Object mapKey) {
        Map<Object, ?> map = (Map<Object, ?>) mapObj;
        return (T) map.get(mapKey);
    }
}

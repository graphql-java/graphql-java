package example.http;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.StarWarsData;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import static graphql.ExecutionInput.newExecutionInput;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

/**
 * An very simple example of serving a qraphql schema over http.
 *
 * More info can be found here : http://graphql.org/learn/serving-over-http/
 */
public class HttpMain extends AbstractHandler {

    static final int PORT = 3000;
    static GraphQLSchema starWarsSchema = null;

    public static void main(String[] args) throws Exception {
        //
        // This example uses Jetty as an embedded HTTP server
        Server server = new Server(PORT);
        //
        // In Jetty, handlers are how your get called backed on a request
        server.setHandler(new HttpMain());
        server.start();

        server.join();
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if ("/graphql".equals(target) || "/".equals(target)) {
            handleStarWars(request, response);
        }
        baseRequest.setHandled(true);
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
                .requestString(parameters.getQuery())
                .operationName(parameters.getOperationName())
                .arguments(parameters.getVariables());

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
        executionInput.context(context);

        //
        // you need a schema in order to execute queries
        GraphQLSchema schema = buildStarWarsSchema();

        // finally you build a runtime graphql object and execute the query
        GraphQL graphQL = GraphQL.newGraphQL(schema).build();
        ExecutionResult executionResult = graphQL.execute(executionInput.build());

        returnAsJson(httpResponse, executionResult);
    }


    private void returnAsJson(HttpServletResponse response, ExecutionResult executionResult) throws IOException {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        JsonKit.toJson(response, executionResult);
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
            // reads a file that provides the schema types
            //
            Reader streamReader = loadSchemaFile("starWarsSchemaAnnotated.graphqls");
            TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(streamReader);

            //
            // the runtime wiring is used to provide the code that backs the
            // logical schema
            //
            RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                    .type(newTypeWiring("Query")
                            .dataFetcher("hero", StarWarsData.getHeroDataFetcher())
                            .dataFetcher("human", StarWarsData.getHumanDataFetcher())
                            .dataFetcher("droid", StarWarsData.getDroidDataFetcher())
                    )
                    .type(newTypeWiring("Human")
                            .dataFetcher("friends", StarWarsData.getFriendsDataFetcher())
                    )
                    .type(newTypeWiring("Droid")
                            .dataFetcher("friends", StarWarsData.getFriendsDataFetcher())
                    )

                    .type(newTypeWiring("Character")
                            .typeResolver(StarWarsData.getCharacterTypeResolver())
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

    private Reader loadSchemaFile(String name) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        return new InputStreamReader(stream);
    }
}

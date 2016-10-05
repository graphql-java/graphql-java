package graphql;

import java.util.HashSet;
import java.util.Set;

import graphql.language.SourceLocation;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class SetAsListTest {
    private void printErrors(ExecutionResult result) {
        for(GraphQLError error : result.getErrors()) {
            System.out.println(error.getErrorType() +": "+error.getMessage());
            for(SourceLocation location : error.getLocations()) {
                System.out.println("on line " + location.getLine() + ", column "+ location.getColumn());
            }
        }
    }

    @Test
    public void test() {

        GraphQLObjectType queryType = GraphQLObjectType.newObject()
                .name("QueryType")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                    .name("set")
                    .type(new GraphQLList(Scalars.GraphQLString))
                        .dataFetcher(new DataFetcher() {
                            @Override
                            public Object get(DataFetchingEnvironment environment) {
                                Set<String> set = new HashSet<String>();
                                set.add("One");
                                set.add("Two");
                                return set;
                            }
                        }))
                .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();
        ExecutionResult result = new GraphQL(schema).execute(
                "query { set }");
        printErrors(result);
        assertEquals(result.getData().toString(), "{set=[One, Two]}");
    }
}

package graphql;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static org.junit.Assert.assertEquals;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ErrorPathTest {
    @Test
    public void testErrorPath() {
        GraphQLObjectType queryType = newObject()
            .name("errorPathQuery")
            .field(newFieldDefinition()
                .name("f1")
                .type(GraphQLString)
                .dataFetcher(new DataFetcher(){
                    @Override
                    public Object get(DataFetchingEnvironment environment) {
                        throw new RuntimeException("error.");
                    }
                })
                .build())
            .field(newFieldDefinition()
                .name("f2")
                .type(new GraphQLList(newObject()
                    .name("Test")
                    .field(newFieldDefinition()
                        .name("sub1")
                        .type(GraphQLString)
                        .staticValue("test")
                        .build()
                    )
                    .field(newFieldDefinition()
                        .name("sub2")
                        .type(GraphQLString)
                        .dataFetcher(new DataFetcher(){
                            @Override
                            public Object get(DataFetchingEnvironment environment) {
                                boolean willThrow = (Boolean) environment.getSource();
                                if (willThrow) {
                                    throw new RuntimeException("error.");
                                }
                                return "no error";
                            }
                        })
                        .build()
                    )
                    .build()
                ))
                .dataFetcher(new DataFetcher() {
                    @Override
                    public Object get(DataFetchingEnvironment environment) {
                        return new Boolean[] { false, true, false};
                    }
                })
                .build()
            )
            .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
            .query(queryType)
            .build();

        GraphQL graphQL = GraphQL.newGraphQL(schema).build();

        List<GraphQLError> errors = graphQL.execute("{f1 f2{sub1 sub2}}").getErrors();

        ExceptionWhileDataFetching error = (ExceptionWhileDataFetching) errors.get(0);
        assertEquals("f1", error.getPath());

        error = (ExceptionWhileDataFetching) errors.get(1);
        assertEquals("f2[1]/sub2", error.getPath());
    }
}

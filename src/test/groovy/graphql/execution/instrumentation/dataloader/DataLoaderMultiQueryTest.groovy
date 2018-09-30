package graphql.execution.instrumentation.dataloader

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition

/**
 * A test for the reported problem in https://github.com/graphql-java/graphql-java/issues/831
 */
class DataLoaderMultiQueryTest extends Specification {

    def dataMap = [1 : "Name 1",
                   2 : "Name 2",
                   3 : "Name 3",
                   4 : "Name 4",
                   5 : "Name 5"]

    DataFetcher<Data> queryDataFetcher = new DataFetcher<Data>() {
        @Override
        Data get(DataFetchingEnvironment environment) throws Exception {
            def input = environment.getArgument("id");
            return new Data(Integer.valueOf(input), null);
        }
    }

    class Data {

        public final int id
        public final String name;

        private Data(int id, String name) {
            this.id = id
            this.name = name
        }

        @Override
        boolean equals(Object o) {
            if (!(o instanceof Data)) {
                return false
            }
            Data node = (Data) o
            return id == node.id
        }

        @Override
        int hashCode() {
            return id
        }

        @Override
        String toString() {
            return String.valueOf(id)
        }

    }

    def "multi Query"() {

        def expectedBatchedKeys = [new Data(Integer.valueOf("1"),null),
                                   new Data(Integer.valueOf("2"),null),
                                   new Data(Integer.valueOf("3"),null),
                                   new Data(Integer.valueOf("4"),null),
                                   new Data(Integer.valueOf("5"),null)]
        def actualBatchedKeys

        DataLoader<Data, List<String>> loader = new DataLoader<>({ keys ->
            actualBatchedKeys = keys;
            List<String> dataList = new ArrayList<>()
            for (Data key : keys) {
                dataList.add(dataMap.get(key.id))
            }
            System.out.println("BatchLoader called for " + keys + " -> got " + dataList)
            return CompletableFuture.completedFuture(dataList)
        })

        GraphQLObjectType nodeType = GraphQLObjectType.newObject()
                .name("Data")
                .field(newFieldDefinition()
                .name("id")
                .type(GraphQLInt)
                .build())
                .field(newFieldDefinition()
                .name("name")
                .type(GraphQLString)
                .dataFetcher({ environment -> loader.load(environment.getSource()) })
                .build())
                .build()

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(GraphQLObjectType.newObject()
                .name("Query")
                .field(newFieldDefinition()
                .name("query")
                .argument(newArgument().name("id").type(GraphQLInt))
                .type(nodeType)
                .dataFetcher(queryDataFetcher)
                .build())
                .build())
                .build()

        DataLoaderRegistry registry = new DataLoaderRegistry().register("dataLoader", loader)

        ExecutionResult result = GraphQL.newGraphQL(schema)
                .instrumentation(new DataLoaderDispatcherInstrumentation(registry))
                .build()
                .execute('''
                        query Q1 { 
                            test1: query(id: 1) { 
                                id, 
                                name
                            },
                            test2: query(id: 2) { 
                                id, 
                                name
                            },
                            test3: query(id: 3) { 
                                id, 
                                name
                            },
                            test4: query(id: 4) { 
                                id, 
                                name
                            },
                            test5: query(id: 5) { 
                                id, 
                                name
                            }
                        }
                    ''')

        expect:
        result != null

        result.data == [test1: [id: 1, name: "Name 1"],
                        test2: [id: 2, name: "Name 2"],
                        test3: [id: 3, name: "Name 3"],
                        test4: [id: 4, name: "Name 4"],
                        test5: [id: 5, name: "Name 5"]
        ]

        expectedBatchedKeys.equals(actualBatchedKeys)
    }
}

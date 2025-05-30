package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.StaticDataFetcher
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.Scalars.GraphQLInt
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLTypeReference.typeRef

/**
 * A test for the reported problem in https://github.com/graphql-java/graphql-java/issues/831
 */
class DataLoaderNodeTest extends Specification {

    private final Node root = new Node(1,
            new Node(2,
                    new Node(4),
                    new Node(5)
            ),
            new Node(3,
                    new Node(6),
                    new Node(7)
            )
    )

    class Node {

        public final int id
        public final List<Node> childNodes

        private Node(int id, Node... childNodes) {
            this.id = id
            this.childNodes = Arrays.asList(childNodes)
        }

        @Override
        boolean equals(Object o) {
            if (!(o instanceof Node)) {
                return false
            }
            Node node = (Node) o
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

    class NodeDataFetcher implements DataFetcher {
        String name

        NodeDataFetcher(String name) {
            this.name = name
        }

        @Override
        Object get(DataFetchingEnvironment environment) throws Exception {
            return environment.getDataLoader(name).load(environment.getSource())
        }
    }

    def "levels of loading"() {

        List<List<Node>> nodeLoads = []


        def batchLoadFunction = { keys ->
            nodeLoads.add(keys)
            List<List<Node>> childNodes = new ArrayList<>()
            for (Node key : keys) {
                childNodes.add(key.childNodes)
            }
            System.out.println("BatchLoader called for " + keys + " -> got " + childNodes)
            return CompletableFuture.completedFuture(childNodes)
        }
        DataLoader<Node, List<Node>> loader = DataLoaderFactory.newDataLoader(batchLoadFunction)

        def nodeTypeName = "Node"
        def childNodesFieldName = "childNodes"
        def queryTypeName = "Query"
        def rootFieldName = "root"

        DataFetcher<?> nodeDataFetcher = new NodeDataFetcher(childNodesFieldName)
        DataLoaderRegistry registry = new DataLoaderRegistry().register(childNodesFieldName, loader)

        GraphQLObjectType nodeType = GraphQLObjectType
                .newObject()
                .name(nodeTypeName)
                .field(newFieldDefinition()
                    .name("id")
                    .type(GraphQLInt)
                    .build())
                .field(newFieldDefinition()
                    .name(childNodesFieldName)
                    .type(list(typeRef(nodeTypeName)))
                    .build())
                .build()

        def childNodesCoordinates = FieldCoordinates.coordinates(nodeTypeName, childNodesFieldName)
        def rootCoordinates = FieldCoordinates.coordinates(queryTypeName, rootFieldName)
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(childNodesCoordinates, nodeDataFetcher)
                .dataFetcher(rootCoordinates, new StaticDataFetcher(root))
                .build()
        GraphQLSchema schema = GraphQLSchema.newSchema()
                .codeRegistry(codeRegistry)
                .query(GraphQLObjectType.newObject()
                        .name(queryTypeName)
                        .field(newFieldDefinition()
                            .name(rootFieldName)
                            .type(nodeType)
                            .build())
                        .build())
                .build()

        ExecutionResult result = GraphQL.newGraphQL(schema)
                .build()
                .execute(ExecutionInput.newExecutionInput()
                        .graphQLContext([(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING): enableDataLoaderChaining])
                        .dataLoaderRegistry(registry).query(
                '''
                        query Q { 
                            root { 
                                id 
                                childNodes { 
                                    id  
                                    childNodes {  
                                        id  
                                        childNodes { 
                                            id 
                                        }
                                    }
                                }
                            }
                        }
                    ''').build())

        expect:
        result != null

        result.data == [root: [id: 1, childNodes: [
                [id: 2, childNodes: [
                        [id: 4, childNodes: []],
                        [id: 5, childNodes: []]
                ]],
                [id: 3, childNodes: [
                        [id: 6, childNodes: []],
                        [id: 7, childNodes: []]
                ]]
        ]]
        ]

        //
        // we want this
        //nodeLoads.size() == 3
        //
        // but currently is this
        nodeLoads.size() == 3 // WOOT!

        where:
        enableDataLoaderChaining << [true, false]


    }
}

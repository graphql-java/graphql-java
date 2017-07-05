package graphql.execution

import graphql.GraphQL
import graphql.schema.DataFetcher
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject

class AsynchronousExecutionStrategyTest extends Specification {

    def "Example usage of AsynchronousExecutionStrategy."() {
        given:

        GraphQLObjectType queryType = newObject()
                .name("data")
                .field(
                    newFieldDefinition().type(GraphQLString).name("key1").dataFetcher({env -> CompletableFuture.completedFuture("value1")}))
                .field(
                    newFieldDefinition().type(GraphQLString).name("key2").staticValue("value2"))
                .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();

        def expected = [key1:"value1",key2:"value2"]

        when:
        GraphQL graphQL = GraphQL.newGraphQL(schema)
                .queryExecutionStrategy(new AsynchronousExecutionStrategy())
                .build();

        Map<String,Object> result = ((CompletionStage<Object>) graphQL.execute("{key1,key2}").data).toCompletableFuture().get();

        then:
        assert expected == result;
    }

    def "Ensure the execution order." () {
        given:
        Timer timer = new Timer();

        DataFetcher<CompletionStage<String>> grandFetcher = {
            env ->
                CompletableFuture<Object> future = new CompletableFuture<>()
                timer.schedule({_-> future.complete([field:"grandValue"]) },50)
                return future
        }

        DataFetcher<CompletionStage<String>> parentFetcher = {
            env ->
                CompletableFuture<Object> future = new CompletableFuture<>()
                timer.schedule({_-> future.complete([field:"parentValue"]) },20)
                return future
        }

        DataFetcher<CompletionStage<String>> childFetcher = {
            env ->
                CompletableFuture<Object> future = new CompletableFuture<>()
                timer.schedule({_-> future.complete([field:"childValue"]) },10)
                return future
        }

        GraphQLObjectType childObjectType = newObject().name("ChildObject").
                field(newFieldDefinition().name("field").type(GraphQLString)).
                build();

        GraphQLObjectType parentObjectType = newObject().name("ParentObject").
                field(newFieldDefinition().name("field").type(GraphQLString)).
                field(newFieldDefinition().name("child").type(childObjectType).dataFetcher(childFetcher)).
                build();

        GraphQLObjectType grandObjectType = newObject().name("GrandObject").
                field(newFieldDefinition().name("field").type(GraphQLString)).
                field(newFieldDefinition().name("parent").type(parentObjectType).dataFetcher(parentFetcher)).
                build();

        GraphQLObjectType rootObjectType = newObject().name("Root").
                field(
                    newFieldDefinition().name("grand").type(grandObjectType).dataFetcher(grandFetcher)
                ).build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(rootObjectType)
                .build();
        when:

        GraphQL graphQL = GraphQL.newGraphQL(schema)
                .queryExecutionStrategy(new AsynchronousExecutionStrategy())
                .build();

        String queryString =
                """
                {
                    grand {
                        field
                        parent {
                            field
                            child {
                                field
                            }
                        }
                    }
                }
                """
        Map<String,Object> result = ((CompletionStage<Object>) graphQL.execute(queryString).data).toCompletableFuture().get();

        def expected = [
                grand:[
                        field: "grandValue",
                        parent:[
                                field:"parentValue",
                                child: [
                                        field: "childValue"
                                ]
                        ]
                ]
        ]

        then:
        assert  result == expected
    }
}

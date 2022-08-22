package graphql.cachecontrol

import graphql.ExecutionInput
import graphql.ExecutionResultImpl
import graphql.GraphQLContext
import graphql.TestUtil
import graphql.execution.CoercedVariables
import graphql.execution.ExecutionContextBuilder
import graphql.execution.ExecutionId
import graphql.execution.ExecutionStrategy
import graphql.execution.ResultPath
import graphql.execution.instrumentation.Instrumentation
import graphql.language.Document
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.schema.DataFetcher
import graphql.schema.GraphQLSchema
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

class CacheControlTest extends Specification {
    // All tests in this file will be deleted when CacheControl code is removed.

    def "can build up hints when there is no extensions present"() {
        def cc = CacheControl.newCacheControl()
        cc.hint(ResultPath.parse("/hint/99"), 99)
        cc.hint(ResultPath.parse("/hint/66"), 66)
        cc.hint(ResultPath.parse("/hint/33/private"), 33, CacheControl.Scope.PRIVATE)
        cc.hint(ResultPath.parse("/hint/private"), CacheControl.Scope.PRIVATE)

        def er = ExecutionResultImpl.newExecutionResult().data("data").build()

        when:
        def newER = cc.addTo(er)
        then:
        newER.data == "data" // left alone
        newER.extensions == [
                cacheControl: [
                        version: 1,
                        hints  : [
                                [path: ["hint", "99"], maxAge: 99, scope: "PUBLIC"],
                                [path: ["hint", "66"], maxAge: 66, scope: "PUBLIC"],
                                [path: ["hint", "33", "private"], maxAge: 33, scope: "PRIVATE"],
                                [path: ["hint", "private"], scope: "PRIVATE"],
                        ]
                ]
        ]

    }

    def "can build up hints when extensions are present"() {
        def cc = CacheControl.newCacheControl()
        cc.hint(ResultPath.parse("/hint/99"), 99)
        cc.hint(ResultPath.parse("/hint/66"), 66)

        def startingExtensions = ["someExistingExt": "data"]

        def er = ExecutionResultImpl.newExecutionResult().data("data").extensions(startingExtensions).build()

        when:
        def newER = cc.addTo(er)
        then:
        newER.data == "data" // left alone
        newER.extensions.size() == 2
        newER.extensions["someExistingExt"] == "data"
        newER.extensions["cacheControl"] == [
                version: 1,
                hints  : [
                        [path: ["hint", "99"], maxAge: 99, scope: "PUBLIC"],
                        [path: ["hint", "66"], maxAge: 66, scope: "PUBLIC"],
                ]
        ]
    }

    def "integration test of cache control"() {
        def sdl = '''
            type Query {
                levelA : LevelB
            }
            
            type LevelB {
                levelB : LevelC
            }
            
            type LevelC {
                levelC : String
            }
        '''

        DataFetcher dfA = { env ->
            CacheControl cc = env.getGraphQlContext().get("cacheControl")
            cc.hint(env, 100)
        } as DataFetcher
        DataFetcher dfB = { env ->
            CacheControl cc = env.getGraphQlContext().get("cacheControl")
            cc.hint(env, 999)
        } as DataFetcher

        DataFetcher dfC = { env ->
            CacheControl cc = env.getGraphQlContext().get("cacheControl")
            cc.hint(env, CacheControl.Scope.PRIVATE)
        } as DataFetcher

        def graphQL = TestUtil.graphQL(sdl, [
                Query : [levelA: dfA,],
                LevelB: [levelB: dfB],
                LevelC: [levelC: dfC]
        ]).build()

        def cacheControl = CacheControl.newCacheControl()
        when:
        ExecutionInput ei = ExecutionInput.newExecutionInput(' { levelA { levelB { levelC } } }')
                .graphQLContext(["cacheControl": cacheControl])
                .build()
        def er = graphQL.execute(ei)
        er = cacheControl.addTo(er)
        then:
        er.errors.isEmpty()
        er.extensions == [
                cacheControl: [
                        version: 1,
                        hints  : [
                                [path: ["levelA"], maxAge: 100, scope: "PUBLIC"],
                                [path: ["levelA", "levelB"], maxAge: 999, scope: "PUBLIC"],
                                [path: ["levelA", "levelB", "levelC"], scope: "PRIVATE"],
                        ]
                ]
        ]
    }

    def "transform works and copies values with cache control"() {
        // Retain this ExecutionContext CacheControl test for coverage.
        given:
        def cacheControl = CacheControl.newCacheControl()
        def oldCoercedVariables = CoercedVariables.emptyVariables()
        Instrumentation instrumentation = Mock(Instrumentation)
        ExecutionStrategy queryStrategy = Mock(ExecutionStrategy)
        ExecutionStrategy mutationStrategy = Mock(ExecutionStrategy)
        ExecutionStrategy subscriptionStrategy = Mock(ExecutionStrategy)
        GraphQLSchema schema = Mock(GraphQLSchema)
        def executionId = ExecutionId.generate()
        def graphQLContext = GraphQLContext.newContext().build()
        def root = "root"
        Document document = new Parser().parseDocument("query myQuery(\$var: String){...MyFragment} fragment MyFragment on Query{foo}")
        def operation = document.definitions[0] as OperationDefinition
        def fragment = document.definitions[1] as FragmentDefinition
        def dataLoaderRegistry = new DataLoaderRegistry()

        def executionContextOld = new ExecutionContextBuilder()
                .cacheControl(cacheControl)
                .executionId(executionId)
                .instrumentation(instrumentation)
                .graphQLSchema(schema)
                .queryStrategy(queryStrategy)
                .mutationStrategy(mutationStrategy)
                .subscriptionStrategy(subscriptionStrategy)
                .root(root)
                .graphQLContext(graphQLContext)
                .coercedVariables(oldCoercedVariables)
                .fragmentsByName([MyFragment: fragment])
                .operationDefinition(operation)
                .dataLoaderRegistry(dataLoaderRegistry)
                .build()

        when:
        def coercedVariables = CoercedVariables.of([var: 'value'])
        def executionContext = executionContextOld.transform(builder -> builder
                .coercedVariables(coercedVariables))

        then:
        executionContext.cacheControl == cacheControl
        executionContext.executionId == executionId
        executionContext.instrumentation == instrumentation
        executionContext.graphQLSchema == schema
        executionContext.queryStrategy == queryStrategy
        executionContext.mutationStrategy == mutationStrategy
        executionContext.subscriptionStrategy == subscriptionStrategy
        executionContext.root == root
        executionContext.graphQLContext == graphQLContext
        executionContext.coercedVariables == coercedVariables
        executionContext.getFragmentsByName() == [MyFragment: fragment]
        executionContext.operationDefinition == operation
        executionContext.dataLoaderRegistry == dataLoaderRegistry
    }
}

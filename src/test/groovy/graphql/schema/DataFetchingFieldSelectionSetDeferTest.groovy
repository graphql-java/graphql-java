package graphql.schema

import graphql.ExecutionInput
import graphql.ExperimentalApi
import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class DataFetchingFieldSelectionSetDeferTest extends Specification {

    def sdl = '''
            type Query {
                post : Post
            }

            type Post {
                id: ID!
                title: String
                body: String
                comments: [Comment]
                author: Author
            }

            type Comment {
                text: String
                author: Author
            }

            type Author {
                name: String
                avatar: String
            }
        '''

    DataFetchingFieldSelectionSet capturedSelectionSet = null

    DataFetcher postDF = { DataFetchingEnvironment env ->
        capturedSelectionSet = env.getSelectionSet()
        return [id: "1", title: "Hello", body: "World"]
    }

    def graphQL = GraphQL.newGraphQL(TestUtil.schema(sdl, newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("post", postDF))
            .build()))
            .build()

    private def execute(String query) {
        def ei = ExecutionInput.newExecutionInput(query)
                .graphQLContext([(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT): true])
                .build()
        return graphQL.execute(ei)
    }

    def "isDeferred returns true for fields inside @defer fragments"() {
        when:
        def er = execute('''
        {
            post {
                id
                title
                ... @defer {
                    body
                    comments {
                        text
                    }
                }
            }
        }
        ''')

        then:
        er.errors.isEmpty()

        def immediateFields = capturedSelectionSet.getImmediateFields()
        def nonDeferred = immediateFields.findAll { !it.isDeferred() }
        def deferred = immediateFields.findAll { it.isDeferred() }

        nonDeferred.collect { it.name }.sort() == ["id", "title"]
        deferred.collect { it.name }.sort() == ["body", "comments"]
    }

    def "getDeferredFields returns only deferred fields"() {
        when:
        def er = execute('''
        {
            post {
                id
                title
                ... @defer {
                    body
                    comments {
                        text
                    }
                }
            }
        }
        ''')

        then:
        er.errors.isEmpty()

        def deferredFields = capturedSelectionSet.getImmediateDeferredFields()
        deferredFields.collect { it.name }.sort() == ["body", "comments"]
    }

    def "getDeferredFields at all levels returns deferred fields across the tree"() {
        when:
        def er = execute('''
        {
            post {
                id
                ... @defer {
                    body
                    comments {
                        text
                    }
                }
            }
        }
        ''')

        then:
        er.errors.isEmpty()

        def allDeferredFields = capturedSelectionSet.getDeferredFields()
        allDeferredFields.collect { it.qualifiedName }.sort() == ["body", "comments"]
    }

    def "no deferred fields when @defer is not used"() {
        when:
        def er = execute('''
        {
            post {
                id
                title
                body
            }
        }
        ''')

        then:
        er.errors.isEmpty()

        capturedSelectionSet.getImmediateDeferredFields().isEmpty()
        capturedSelectionSet.getDeferredFields().isEmpty()
        capturedSelectionSet.getImmediateFields().every { !it.isDeferred() }
    }

    def "isDeferred is false when incremental support is not enabled"() {
        when:
        def ei = ExecutionInput.newExecutionInput('''
        {
            post {
                id
                ... @defer {
                    body
                }
            }
        }
        ''').build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()

        capturedSelectionSet.getImmediateFields().every { !it.isDeferred() }
        capturedSelectionSet.getImmediateDeferredFields().isEmpty()
    }

    def "deferred fields with @defer(if: false) are not marked as deferred"() {
        when:
        def er = execute('''
        {
            post {
                id
                ... @defer(if: false) {
                    body
                }
            }
        }
        ''')

        then:
        er.errors.isEmpty()

        capturedSelectionSet.getImmediateFields().every { !it.isDeferred() }
        capturedSelectionSet.getImmediateDeferredFields().isEmpty()
    }

    def "named defer labels work with isDeferred"() {
        when:
        def er = execute('''
        {
            post {
                id
                ... @defer(label: "postBody") {
                    body
                }
                ... @defer(label: "postComments") {
                    comments {
                        text
                    }
                }
            }
        }
        ''')

        then:
        er.errors.isEmpty()

        def deferred = capturedSelectionSet.getImmediateDeferredFields()
        deferred.collect { it.name }.sort() == ["body", "comments"]
    }
}

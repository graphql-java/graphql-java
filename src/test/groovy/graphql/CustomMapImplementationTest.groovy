package graphql

import graphql.execution.ResponseMapFactory
import graphql.schema.idl.RuntimeWiring
import groovy.transform.Immutable
import spock.lang.Specification

class CustomMapImplementationTest extends Specification {

    @Immutable
    static class Person {
        String name
        @SuppressWarnings('unused') // used by graphql-java
        int age
    }

    class CustomResponseMapFactory implements ResponseMapFactory {

        @Override
        Map<String, Object> createInsertionOrdered(List<String> keys, List<Object> values) {
            return Collections.unmodifiableMap(DEFAULT.createInsertionOrdered(keys, values))
        }
    }

    def graphql = TestUtil.graphQL("""
                type Query {
                    people: [Person!]!
                }
                
                type Person {
                    name: String!
                    age: Int!
                }
                
            """,
            RuntimeWiring.newRuntimeWiring()
                .type("Query", {
                    it.dataFetcher("people", { List.of(new Person("Mario", 18), new Person("Luigi", 21))})
                })
                .build())
            .build()

    def "customMapImplementation"() {
        when:
        def input = ExecutionInput.newExecutionInput()
                .query('''
                        query {
                            people {
                                name
                                age
                            }
                        }
                        ''')
                .graphQLContext { it -> GraphQL.unusualConfiguration(it).responseMapFactory().setFactory(new CustomResponseMapFactory())}
                .build()

        def executionResult = graphql.execute(input)

        then:
        executionResult.errors.isEmpty()
        executionResult.data == [ people: [
                [name: "Mario", age: 18],
                [name: "Luigi", age: 21],
        ]]
        executionResult.data.getClass().getSimpleName() == 'UnmodifiableMap'
        executionResult.data['people'].each { it -> it.getClass().getSimpleName() == 'UnmodifiableMap' }
    }

}

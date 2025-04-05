package graphql

import graphql.execution.AsyncExecutionStrategy
import graphql.schema.idl.RuntimeWiring
import groovy.transform.Immutable
import spock.lang.Specification

class CustomMapImplementation extends Specification {

    @Immutable
    static class Person {
        String name
        @SuppressWarnings('unused') // used by graphql-java
        int age
    }

    class CustomMapExecutionStrategy extends  AsyncExecutionStrategy {

        @Override
        protected Map<String, Object> buildFieldValueMap(List<String> fieldNames, List<Object> results) {
            var mutable = super.buildFieldValueMap(fieldNames, results)
            // just change the Map to be immutable
            // real world implementation could use eclipse-collections unified maps that are 50% smaller than JDK maps
            return Collections.unmodifiableMap(mutable)
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
            .queryExecutionStrategy(new CustomMapExecutionStrategy())
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

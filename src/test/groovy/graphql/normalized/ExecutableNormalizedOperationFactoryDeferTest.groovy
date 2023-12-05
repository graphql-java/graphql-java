package graphql.normalized

import graphql.AssertException
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.RawVariables
import graphql.language.Document
import graphql.schema.GraphQLSchema
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserVisitorStub
import spock.lang.Specification

class ExecutableNormalizedOperationFactoryDeferTest extends Specification {
    String schema = """
            directive @defer(if: Boolean, label: String) on FRAGMENT_SPREAD | INLINE_FRAGMENT

            type Query {
                dog: Dog
                animal: Animal
            }
            
            interface Animal {
                name: String
            }

            type Dog implements Animal {
                name: String
                breed: String
                owner: Person
            }
            
            type Person {
                firstname: String
                lastname: String
            }
        """

    GraphQLSchema graphQLSchema = TestUtil.schema(schema)

    def "defer on a single field via inline fragment without type"() {
        given:

        String query = '''
          query q {
            dog {
                name
                ... @defer(label: "breed-defer") {
                    breed
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.dog',
                        'Dog.name',
                        'Dog.breed defer[breed-defer]',
        ]
    }

    def "defer on a single field via inline fragment with type"() {
        given:

        String query = '''
          query q {
            dog {
                name
                ... on Dog @defer(label: "breed-defer") {
                    breed
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.dog',
                        'Dog.name',
                        'Dog.breed defer[breed-defer]',
        ]
    }

    def "defer on 2 fields"() {
        given:

        String query = '''
          query q {
            dog {
                ... @defer(label: "breed-defer") {
                    name
                    breed
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.dog',
                        'Dog.name defer[breed-defer]',
                        'Dog.breed defer[breed-defer]',
        ]
    }

    def "defer on a fragment definition"() {
        given:

        String query = '''
          query q {
            dog {
                ... DogFrag @defer(label: "breed-defer") 
            }
          }
          
          fragment DogFrag on Dog {
            name
            breed
          } 
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.dog',
                        'Dog.name defer[breed-defer]',
                        'Dog.breed defer[breed-defer]',
        ]
    }

    def "multiple defer on same field with different labels"() {
        given:

        String query = '''
          query q {
            dog {
                ... @defer(label: "name-defer") {
                    name 
                }
                
                ... @defer(label: "another-name-defer") {
                    name 
                }
            }
          }
          
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.dog',
                        'Dog.name defer[name-defer,another-name-defer]',
        ]
    }

    def "multiple fields and a single defer"() {
        given:

        String query = '''
          query q {
            dog {
                ... @defer(label: "name-defer") {
                    name 
                }
                
                ... {
                    name 
                }
            }
          }
          
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.dog',
                        'Dog.name defer[name-defer]',
        ]
    }

    def "multiple fields and a single defer - no label"() {
        given:

        String query = '''
          query q {
            dog {
                ... @defer {
                    name 
                }
                
                ... {
                    name 
                }
            }
          }
          
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.dog',
                        'Dog.name defer[null]',
        ]
    }

    def "multiple fields and a multiple defers with same label"() {
        given:

        String query = '''
          query q {
            dog {
                ... @defer(label:"name-defer") {
                    name 
                }
                
                ... @defer(label:"name-defer") {
                    name 
                }
            }
          }
          
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.dog',
                        'Dog.name defer[name-defer]',
        ]
    }

    def "nested defers - no label"() {
        given:

        String query = '''
          query q {
            dog {
                ... @defer {
                    name
                    owner {
                        firstname
                        ... @defer {
                            lastname 
                        }
                    }
                }
            }
          }
          
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.dog',
                        'Dog.name defer[null]',
                        'Dog.owner defer[null]',
                        'Person.firstname',
                        'Person.lastname defer[null]',
        ]
    }

    def "nested defers - with labels"() {
        given:

        String query = '''
          query q {
            dog {
                ... @defer(label:"dog-defer") {
                    name
                    owner {
                        firstname
                        ... @defer(label: "lastname-defer") {
                            lastname 
                        }
                    }
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.dog',
                        'Dog.name defer[dog-defer]',
                        'Dog.owner defer[dog-defer]',
                        'Person.firstname',
                        'Person.lastname defer[lastname-defer]',
        ]
    }

    def "nested defers - with named spreads"() {
        given:

        String query = '''
          query q {
            animal {
                name
                ... on Dog @defer(label:"dog-defer") {
                    owner {
                        firstname
                        ... @defer(label: "lastname-defer") {
                            lastname 
                        }
                    }
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.animal',
                        'Dog.name',
                        'Dog.owner defer[dog-defer]',
                        'Person.firstname',
                        'Person.lastname defer[lastname-defer]',
        ]
    }

    def "nesting defer blocks that would always result in no data are ignored"() {
        given:

        String query = '''
          query q {
            dog {
              ... @defer(label: "one") {
                ... @defer(label: "two") {
                  ... @defer(label: "three") {
                    name      
                  }
                }
              }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.dog',
                        'Dog.name defer[three]',
        ]
    }

    private List<String> executeQueryAndPrintTree(String query, Map<String, Object> variables) {
        assertValidQuery(graphQLSchema, query, variables)
        Document document = TestUtil.parseQuery(query)
        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory()

        def tree = dependencyGraph.createExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, RawVariables.of(variables))
        return printTreeWithIncrementalExecutionDetails(tree)
    }

    private List<String> printTreeWithIncrementalExecutionDetails(ExecutableNormalizedOperation queryExecutionTree) {
        def result = []
        Traverser<ExecutableNormalizedField> traverser = Traverser.depthFirst({ it.getChildren() })
        traverser.traverse(queryExecutionTree.getTopLevelFields(), new TraverserVisitorStub<ExecutableNormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<ExecutableNormalizedField> context) {
                ExecutableNormalizedField queryExecutionField = context.thisNode()
                result << queryExecutionField.printDetails() + printDeferExecutionDetails(queryExecutionField)
                return TraversalControl.CONTINUE
            }

            String printDeferExecutionDetails(ExecutableNormalizedField field) {
                if (field.getDeferExecution() == null) {
                    return ""
                }

                def deferLabels = field.getDeferExecution().labels
                        .collect {it.value}
                        .join(",")

                return " defer[" + deferLabels + "]"
            }
        })

        result
    }

    private void assertValidQuery(GraphQLSchema graphQLSchema, String query, Map variables = [:]) {
        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build()
        def ei = ExecutionInput.newExecutionInput(query).variables(variables).build()
        assert graphQL.execute(ei).errors.size() == 0
    }
}

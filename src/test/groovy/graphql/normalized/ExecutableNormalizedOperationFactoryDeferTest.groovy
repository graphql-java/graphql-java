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
                mammal: Mammal
            }
            
            interface LivingThing {
                age: Int
            }
            
            interface Animal implements LivingThing {
                name: String
                age: Int
            }

            type Dog implements Animal & LivingThing {
                name: String
                age: Int
                breed: String
                owner: Person
            }
            
            type Cat implements Animal & LivingThing  {
                name: String
                age: Int
                breed: String
                color: String
                siblings: [Cat]
            }
            
            type Fish implements Animal & LivingThing  {
                name: String
                age: Int
            }
            
            type Person {
                firstname: String
                lastname: String
                bestFriend: Person
            }
            
            union Mammal = Dog | Cat
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
                        'Dog.breed defer{[label=breed-defer;types=[Dog]]}',
        ]
    }

    def "fragment on interface field with no type"() {
        given:

        String query = '''
          query q {
            animal {
                ... @defer {
                    name
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.animal',
                        "[Cat, Dog, Fish].name defer{[label=null;types=[Cat, Dog, Fish]]}",
        ]
    }

    def "fragments on non-conditional fields"() {
        given:

        String query = '''
          query q {
            animal {
                ... on Cat @defer {
                    name
                }
                ... on Dog @defer {
                    name
                }
                ... on Animal @defer {
                    name
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.animal',
                        "[Cat, Dog, Fish].name defer{[label=null;types=[Cat]],[label=null;types=[Dog]],[label=null;types=[Cat, Dog, Fish]]}",
        ]
    }

    def "fragments on subset of non-conditional fields"() {
        given:

        String query = '''
          query q {
            animal {
                ... on Cat @defer {
                    name
                }
                ... on Dog @defer {
                    name
                }
                ... on Fish {
                    name
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.animal',
                        "[Cat, Dog, Fish].name defer{[label=null;types=[Cat]],[label=null;types=[Dog]]}",
        ]
    }

    def "field on multiple defer declarations is associated with "() {
        given:
        String query = '''
          query q {
            dog {
                ... @defer {
                    name
                    age
                }
                ... @defer {
                    age
                }
            }
        }
        '''
        Map<String, Object> variables = [:]

        when:
        def executableNormalizedOperation = createExecutableNormalizedOperations(query, variables);

        List<String> printedTree = printTreeWithIncrementalExecutionDetails(executableNormalizedOperation)

        then:

        def nameField = findField(executableNormalizedOperation, "Dog", "name")
        def ageField = findField(executableNormalizedOperation, "Dog", "age")

        nameField.deferExecutions.size() == 1
        ageField.deferExecutions.size() == 2

        // age field is associated with 2 defer executions, one of then is shared with "name" the other isn't
        ageField.deferExecutions.any {
            it == nameField.deferExecutions[0]
        }

        ageField.deferExecutions.any {
            it != nameField.deferExecutions[0]
        }

        printedTree == ['Query.dog',
                        "Dog.name defer{[label=null;types=[Dog]]}",
                        "Dog.age defer{[label=null;types=[Dog]],[label=null;types=[Dog]]}",
        ]
    }

    def "fragment on interface"() {
        given:

        String query = '''
          query q {
            animal {
                ... on Animal @defer {
                    name
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.animal',
                        '[Cat, Dog, Fish].name defer{[label=null;types=[Cat, Dog, Fish]]}',
        ]
    }

    def "fragment on distant interface"() {
        given:

        String query = '''
          query q {
            animal {
                ... on LivingThing @defer {
                    age
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.animal',
                        '[Cat, Dog, Fish].age defer{[label=null;types=[Cat, Dog, Fish]]}',
        ]
    }

    def "fragment on union"() {
        given:

        String query = '''
          query q {
            mammal {
                ... on Dog @defer {
                    name
                    breed
                }
                ... on Cat @defer {
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
        printedTree == ['Query.mammal',
                        '[Dog, Cat].name defer{[label=null;types=[Cat]],[label=null;types=[Dog]]}',
                        'Dog.breed defer{[label=null;types=[Dog]]}',
                        'Cat.breed defer{[label=null;types=[Cat]]}',
        ]
    }

    def "fragments on interface"() {
        given:

        String query = '''
          query q {
            animal {
                ... on Animal @defer {
                    name
                }
                ... on Animal @defer {
                    age
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.animal',
                        '[Cat, Dog, Fish].name defer{[label=null;types=[Cat, Dog, Fish]]}',
                        '[Cat, Dog, Fish].age defer{[label=null;types=[Cat, Dog, Fish]]}',
        ]
    }

    def "defer on a subselection of non-conditional fields"() {
        given:

        String query = '''
          query q {
            animal {
                ... on Cat @defer {
                    name
                }
                ... on Dog {
                    name
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.animal',
                        '[Cat, Dog].name defer{[label=null;types=[Cat]]}',
        ]
    }

    def "fragments on conditional fields"() {
        given:

        String query = '''
          query q {
            animal {
                ... on Cat @defer {
                    breed
                }
                ... on Dog @defer {
                    breed
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.animal',
                        'Cat.breed defer{[label=null;types=[Cat]]}',
                        'Dog.breed defer{[label=null;types=[Dog]]}'
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
                        'Dog.breed defer{[label=breed-defer;types=[Dog]]}',
        ]
    }

    def "1 defer on 2 fields"() {
        given:
        String query = '''
          query q {
            animal {
                ... @defer {
                    name
                }
                
                ... on Dog @defer {
                    name 
                    breed
                }
                
                ... on Cat @defer {
                    name 
                    breed
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        def executableNormalizedOperation = createExecutableNormalizedOperations(query, variables);

        List<String> printedTree = printTreeWithIncrementalExecutionDetails(executableNormalizedOperation)

        then: "should result in the same instance of defer block"
        def nameField = findField(executableNormalizedOperation,"[Cat, Dog, Fish]","name")
        def dogBreedField = findField(executableNormalizedOperation, "Dog", "breed")
        def catBreedField = findField(executableNormalizedOperation, "Cat", "breed")

        nameField.deferExecutions.size() == 3
        dogBreedField.deferExecutions.size() == 1
        catBreedField.deferExecutions.size() == 1

        // nameField should share a defer block with each of the other fields
        nameField.deferExecutions.any {
            it == dogBreedField.deferExecutions[0]
        }
        nameField.deferExecutions.any {
            it == catBreedField.deferExecutions[0]
        }
        // also, nameField should have a defer block that is not shared with any other field
        nameField.deferExecutions.any {
            it != dogBreedField.deferExecutions[0] &&
                    it != catBreedField.deferExecutions[0]
        }

        printedTree == ['Query.animal',
                        '[Cat, Dog, Fish].name defer{[label=null;types=[Cat]],[label=null;types=[Dog]],[label=null;types=[Cat, Dog, Fish]]}',
                        'Dog.breed defer{[label=null;types=[Dog]]}',
                        'Cat.breed defer{[label=null;types=[Cat]]}',
        ]
    }

    def "2 defers on 2 fields"() {
        given:

        String query = '''
          query q {
            dog {
                ... @defer{
                    name
                }
                ... @defer{
                    breed
                }
            }
          }
        '''

        Map<String, Object> variables = [:]

        when:
        def executableNormalizedOperation = createExecutableNormalizedOperations(query, variables);

        List<String> printedTree = printTreeWithIncrementalExecutionDetails(executableNormalizedOperation)

        then: "should result in 2 different instances of defer"
        def nameField = findField(executableNormalizedOperation, "Dog", "name")
        def breedField = findField(executableNormalizedOperation, "Dog", "breed")

        nameField.deferExecutions.size() == 1
        breedField.deferExecutions.size() == 1

        // different label instances
        nameField.deferExecutions[0] != breedField.deferExecutions[0]

        printedTree == ['Query.dog',
                        'Dog.name defer{[label=null;types=[Dog]]}',
                        'Dog.breed defer{[label=null;types=[Dog]]}',
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
                        'Dog.name defer{[label=breed-defer;types=[Dog]]}',
                        'Dog.breed defer{[label=breed-defer;types=[Dog]]}',
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
                        'Dog.name defer{[label=another-name-defer;types=[Dog]],[label=name-defer;types=[Dog]]}'
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
                        'Dog.name defer{[label=name-defer;types=[Dog]]}',
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
                        'Dog.name defer{[label=null;types=[Dog]]}',
        ]
    }

    def "multiple fields and multiple defers - no label"() {
        given:

        String query = '''
          query q {
            dog {
                ... @defer {
                    name 
                }
                
                ... @defer {
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
                        'Dog.name defer{[label=null;types=[Dog]],[label=null;types=[Dog]]}',
        ]
    }

    def "multiple fields and a multiple defers with same label are not allowed"() {
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
        executeQueryAndPrintTree(query, variables)

        then:
        def exception = thrown(AssertException)
        exception.message == "Internal error: should never happen: Duplicated @defer labels are not allowed: [name-defer]"
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
                        'Dog.name defer{[label=null;types=[Dog]]}',
                        'Dog.owner defer{[label=null;types=[Dog]]}',
                        'Person.firstname',
                        'Person.lastname defer{[label=null;types=[Person]]}',
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
                        'Dog.name defer{[label=dog-defer;types=[Dog]]}',
                        'Dog.owner defer{[label=dog-defer;types=[Dog]]}',
                        'Person.firstname',
                        'Person.lastname defer{[label=lastname-defer;types=[Person]]}',
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
                        '[Cat, Dog, Fish].name',
                        'Dog.owner defer{[label=dog-defer;types=[Dog]]}',
                        'Person.firstname',
                        'Person.lastname defer{[label=lastname-defer;types=[Person]]}',
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
                        'Dog.name defer{[label=three;types=[Dog]]}',
        ]
    }

    def "'if' argument is respected"() {
        given:

        String query = '''
          query q {
            dog {
                ... @defer(if: false, label: "name-defer") {
                    name 
                }
                
                ... @defer(if: true, label: "another-name-defer") {
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
                        'Dog.name defer{[label=another-name-defer;types=[Dog]]}',
        ]
    }

    def "'if' argument is respected when value is passed through variable"() {
        given:

        String query = '''
          query q($if1: Boolean, $if2: Boolean)  {
            dog {
                ... @defer(if: $if1, label: "name-defer") {
                    name 
                }
                
                ... @defer(if: $if2, label: "another-name-defer") {
                    name 
                }
            }
          }
          
        '''

        Map<String, Object> variables = [if1: false, if2: true]

        when:
        List<String> printedTree = executeQueryAndPrintTree(query, variables)

        then:
        printedTree == ['Query.dog',
                        'Dog.name defer{[label=another-name-defer;types=[Dog]]}',
        ]
    }

    def "'if' argument with different values on same field and same label"() {
        given:

        String query = '''
          query q {
            dog {
                ... @defer(if: false, label: "name-defer") {
                    name 
                }
                
                ... @defer(if: true, label: "name-defer") {
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
                        'Dog.name defer{[label=name-defer;types=[Dog]]}',
        ]
    }

    private ExecutableNormalizedOperation createExecutableNormalizedOperations(String query, Map<String, Object> variables) {
        assertValidQuery(graphQLSchema, query, variables)
        Document document = TestUtil.parseQuery(query)
        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory()

        return dependencyGraph.createExecutableNormalizedOperationWithRawVariables(
                graphQLSchema,
                document,
                null,
                RawVariables.of(variables),
                ExecutableNormalizedOperationFactory.Options.defaultOptions().deferSupport(true),
        )
    }

    private List<String> executeQueryAndPrintTree(String query, Map<String, Object> variables) {
        assertValidQuery(graphQLSchema, query, variables)
        Document document = TestUtil.parseQuery(query)
        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory()

        def tree = dependencyGraph.createExecutableNormalizedOperationWithRawVariables(
                graphQLSchema,
                document,
                null,
                RawVariables.of(variables),
                ExecutableNormalizedOperationFactory.Options.defaultOptions().deferSupport(true),
        )
        return printTreeWithIncrementalExecutionDetails(tree)
    }

    private List<String> printTreeWithIncrementalExecutionDetails(ExecutableNormalizedOperation queryExecutionTree) {
        def result = []
        Traverser traverser = Traverser.depthFirst({ it.getChildren() })

        traverser.traverse(queryExecutionTree.getTopLevelFields(), new TraverserVisitorStub<ExecutableNormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<ExecutableNormalizedField> context) {
                ExecutableNormalizedField queryExecutionField = context.thisNode()
                result << queryExecutionField.printDetails() + printDeferExecutionDetails(queryExecutionField)
                return TraversalControl.CONTINUE
            }

            String printDeferExecutionDetails(ExecutableNormalizedField field) {
                def deferExecutions = field.deferExecutions
                if (deferExecutions == null || deferExecutions.isEmpty()) {
                    return ""
                }

                def deferLabels = new ArrayList<>(deferExecutions)
                        .sort { it.label }
                        .sort { it.possibleTypes.collect {it.name} }
                        .collect { "[label=${it.label};types=${it.possibleTypes.collect{it.name}.sort()}]" }
                        .join(",")

                return " defer{${deferLabels}}"
            }
        })

        result
    }

    private static void assertValidQuery(GraphQLSchema graphQLSchema, String query, Map variables = [:]) {
        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build()
        def ei = ExecutionInput.newExecutionInput(query).variables(variables).build()
        assert graphQL.execute(ei).errors.size() == 0
    }

    private static ExecutableNormalizedField findField(ExecutableNormalizedOperation operation, String objectTypeNames, String fieldName) {
        return operation.normalizedFieldToMergedField
                .collect { it.key }
                .find { it.fieldName == fieldName
                        && it.objectTypeNamesToString() == objectTypeNames}
    }
}

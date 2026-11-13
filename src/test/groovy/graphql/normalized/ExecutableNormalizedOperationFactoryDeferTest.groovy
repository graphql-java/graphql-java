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

        nameField.deferredExecutions.size() == 1
        ageField.deferredExecutions.size() == 2

        // age field is associated with 2 defer executions, one of then is shared with "name" the other isn't
        ageField.deferredExecutions.any {
            it == nameField.deferredExecutions[0]
        }

        ageField.deferredExecutions.any {
            it != nameField.deferredExecutions[0]
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

        nameField.deferredExecutions.size() == 3
        dogBreedField.deferredExecutions.size() == 1
        catBreedField.deferredExecutions.size() == 1

        // nameField should share a defer block with each of the other fields
        nameField.deferredExecutions.any {
            it == dogBreedField.deferredExecutions[0]
        }
        nameField.deferredExecutions.any {
            it == catBreedField.deferredExecutions[0]
        }
        // also, nameField should have a defer block that is not shared with any other field
        nameField.deferredExecutions.any {
            it != dogBreedField.deferredExecutions[0] &&
                    it != catBreedField.deferredExecutions[0]
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

        nameField.deferredExecutions.size() == 1
        breedField.deferredExecutions.size() == 1

        // different label instances
        nameField.deferredExecutions[0] != breedField.deferredExecutions[0]

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

    def "shared deferred selection is collected once through multiple fragments - label #label inline #inlineFragment"() {
        given:
        String directive = label == null ? '@defer' : "@defer(label: \"$label\")"
        String deferredSelection = inlineFragment
                ? "... $directive { firstname lastname }"
                : "...OwnerData $directive"
        String dataFragment = inlineFragment ? '' : '''
            fragment OwnerData on Person {
                firstname
                lastname
            }
        '''
        String query = """
            query q {
                dog {
                    name
                    ...Sidebar
                    ...Details
                }
            }
            fragment Sidebar on Dog {
                ...OwnerField
            }
            fragment Details on Dog {
                ...OwnerField
            }
            fragment OwnerField on Dog {
                owner {
                    $deferredSelection
                }
            }
            $dataFragment
        """

        when:
        def operation = createExecutableNormalizedOperations(query, [:])

        then:
        def firstname = findField(operation, 'Person', 'firstname')
        def lastname = findField(operation, 'Person', 'lastname')
        firstname.deferredExecutions.size() == 1
        firstname.deferredExecutions == lastname.deferredExecutions
        firstname.deferredExecutions.first().label == label
        firstname.deferredExecutions.first().possibleTypes*.name == ['Person']
        printTreeWithIncrementalExecutionDetails(operation) == [
                'Query.dog',
                'Dog.name',
                'Dog.owner',
                "Person.firstname defer{[label=$label;types=[Person]]}",
                "Person.lastname defer{[label=$label;types=[Person]]}",
        ]

        where:
        label                        | inlineFragment
        'OwnerField$defer$OwnerData' | false
        null                         | false
        'owner-data'                 | true
        null                         | true
    }

    def "shared defer is reused when distinct parent AST fields merge"() {
        given:
        String query = '''
            query q {
                dog { owner { ...PersonFields } }
                dog { owner { ...PersonFields } }
            }
            fragment PersonFields on Person {
                ...PersonData @defer(label: "person-data")
            }
            fragment PersonData on Person {
                firstname
                lastname
            }
        '''

        when:
        def operation = createExecutableNormalizedOperations(query, [:])

        then:
        operation.topLevelFields*.fieldName == ['dog']
        def dog = operation.topLevelFields[0]
        dog.children*.fieldName == ['owner']
        def fields = dog.children[0].children
        fields*.fieldName == ['firstname', 'lastname']
        fields[0].deferredExecutions.size() == 1
        fields[0].deferredExecutions == fields[1].deferredExecutions
        fields[0].deferredExecutions.first().label == 'person-data'
        fields[0].deferredExecutions.first().possibleTypes*.name == ['Person']
    }

    def "shared defer has separate execution objects for different root aliases"() {
        given:
        String query = '''
            query q {
                first: dog { ...DogFields }
                second: dog { ...DogFields }
            }
            fragment DogFields on Dog {
                ...DogData @defer(label: "dog-data")
            }
            fragment DogData on Dog {
                name
                age
            }
        '''

        when:
        def operation = createExecutableNormalizedOperations(query, [:])

        then:
        operation.topLevelFields*.resultKey == ['first', 'second']
        def firstFields = operation.topLevelFields[0].children
        def secondFields = operation.topLevelFields[1].children
        firstFields*.fieldName == ['name', 'age']
        secondFields*.fieldName == ['name', 'age']
        firstFields[0].deferredExecutions.size() == 1
        secondFields[0].deferredExecutions.size() == 1
        firstFields[0].deferredExecutions == firstFields[1].deferredExecutions
        secondFields[0].deferredExecutions == secondFields[1].deferredExecutions
        def firstExecution = firstFields[0].deferredExecutions.first()
        def secondExecution = secondFields[0].deferredExecutions.first()
        firstExecution.label == 'dog-data'
        secondExecution.label == 'dog-data'
        !firstExecution.is(secondExecution)
    }

    def "shared defer has separate execution objects at different nesting depths"() {
        given:
        String query = '''
            query q {
                dog {
                    owner {
                        ...PersonFields
                        bestFriend { ...PersonFields }
                    }
                }
            }
            fragment PersonFields on Person {
                ...PersonData @defer(label: "person-data")
            }
            fragment PersonData on Person {
                firstname
                lastname
            }
        '''

        when:
        def operation = createExecutableNormalizedOperations(query, [:])

        then:
        def ownerFields = operation.topLevelFields[0].children[0].children
        ownerFields*.fieldName == ['firstname', 'lastname', 'bestFriend']
        def friendFields = ownerFields[2].children
        friendFields*.fieldName == ['firstname', 'lastname']
        ownerFields[0].deferredExecutions.size() == 1
        friendFields[0].deferredExecutions.size() == 1
        ownerFields[0].deferredExecutions == ownerFields[1].deferredExecutions
        friendFields[0].deferredExecutions == friendFields[1].deferredExecutions
        def ownerExecution = ownerFields[0].deferredExecutions.first()
        def friendExecution = friendFields[0].deferredExecutions.first()
        ownerExecution.label == 'person-data'
        friendExecution.label == 'person-data'
        !ownerExecution.is(friendExecution)
    }

    def "distinct defers selecting the same fragment remain separate - #expectedLabels"() {
        given:
        String query = """
            query q {
                dog {
                    ...DogData $firstDirective
                    ...DogData $secondDirective
                }
            }
            fragment DogData on Dog {
                name
                age
            }
        """

        when:
        def operation = createExecutableNormalizedOperations(query, [:])

        then:
        def nameExecutions = findField(operation, 'Dog', 'name').deferredExecutions.toList()
        def ageExecutions = findField(operation, 'Dog', 'age').deferredExecutions.toList()
        nameExecutions*.label == expectedLabels
        ageExecutions*.label == expectedLabels
        !nameExecutions[0].is(nameExecutions[1])
        nameExecutions[0].is(ageExecutions[0])
        nameExecutions[1].is(ageExecutions[1])

        where:
        firstDirective           | secondDirective           | expectedLabels
        '@defer(label: "first")' | '@defer(label: "second")' | ['first', 'second']
        '@defer'                 | '@defer'                  | [null, null]
    }

    def "shared defer accumulates type coverage across #firstType then #secondType"() {
        given:
        String query = """
            query q {
                animal {
                    ... on $firstType { ...AnimalFields }
                    ... on $secondType { ...AnimalFields }
                }
            }
            fragment AnimalFields on Animal {
                ...AnimalData @defer(label: "animal-data")
            }
            fragment AnimalData on Animal {
                name
                age
            }
        """

        when:
        def operation = createExecutableNormalizedOperations(query, [:])

        then:
        def fields = operation.topLevelFields[0].children
        fields*.fieldName == ['name', 'age']
        fields[0].objectTypeNames.toList().sort() == expectedTypes
        fields[1].objectTypeNames.toList().sort() == expectedTypes
        fields[0].deferredExecutions.size() == 1
        fields[0].deferredExecutions == fields[1].deferredExecutions
        def execution = fields[0].deferredExecutions.first()
        execution.label == 'animal-data'
        execution.possibleTypes*.name.sort() == expectedTypes

        where:
        firstType | secondType | expectedTypes
        'Dog'     | 'Cat'      | ['Cat', 'Dog']
        'Cat'     | 'Dog'      | ['Cat', 'Dog']
        'Dog'     | 'Animal'   | ['Cat', 'Dog', 'Fish']
        'Animal'  | 'Dog'      | ['Cat', 'Dog', 'Fish']
        'Animal'  | 'Mammal'   | ['Cat', 'Dog', 'Fish']
        'Mammal'  | 'Animal'   | ['Cat', 'Dog', 'Fish']
    }

    def "shared fragment merges eager Dog and deferred Cat selections - reversed #reversed"() {
        given:
        String dogSelection = '... on Dog { ...Shared }'
        String catSelection = '... on Cat { ...Shared @defer(label: "cat-data") }'
        String selections = reversed ? "$catSelection $dogSelection" : "$dogSelection $catSelection"
        String query = """
            query q {
                animal {
                    $selections
                }
            }
            fragment Shared on Animal {
                name
            }
        """

        when:
        def operation = createExecutableNormalizedOperations(query, [:])

        then:
        def fields = operation.topLevelFields[0].children
        fields.size() == 1
        fields[0].objectTypeNames.toList().sort() == ['Cat', 'Dog']
        fields[0].deferredExecutions.size() == 1
        fields[0].fieldName == 'name'
        fields[0].deferredExecutions.first().label == 'cat-data'
        fields[0].deferredExecutions.first().possibleTypes*.name == ['Cat']

        where:
        reversed << [false, true]
    }

    def "shared defer respects conditional collection - enabled #enabled skipDog #skipDog"() {
        given:
        String query = '''
            query q($enabled: Boolean!, $skipDog: Boolean!) {
                animal {
                    ... on Dog { ...AnimalFields @skip(if: $skipDog) }
                    ... on Cat { ...AnimalFields }
                }
            }
            fragment AnimalFields on Animal {
                ...AnimalData @defer(if: $enabled, label: "animal-data")
            }
            fragment AnimalData on Animal {
                name
                age
            }
        '''
        def expectedTypes = skipDog ? ['Cat'] : ['Cat', 'Dog']

        when:
        def operation = createExecutableNormalizedOperations(query, [enabled: enabled, skipDog: skipDog])

        then:
        def fields = operation.topLevelFields[0].children
        fields*.fieldName == ['name', 'age']
        fields[0].objectTypeNames.toList().sort() == expectedTypes
        fields[1].objectTypeNames.toList().sort() == expectedTypes
        fields[0].deferredExecutions == fields[1].deferredExecutions
        fields[0].deferredExecutions*.label == (enabled ? ['animal-data'] : [])
        fields[0].deferredExecutions.collect { it.possibleTypes*.name.sort() } == (enabled ? [expectedTypes] : [])

        where:
        enabled | skipDog
        true    | false
        true    | true
        false   | false
        false   | true
    }

    def "field merging preserves shared defer identity across common and type specific fields"() {
        given:
        String query = '''
            query q {
                animal {
                    ...DogFields
                    ...DogFields
                    ...CatFields
                    ...CatFields
                }
            }
            fragment DogFields on Dog {
                ...DogData @defer(label: "dog-data")
            }
            fragment DogData on Dog {
                name
                breed
            }
            fragment CatFields on Cat {
                ...CatData @defer(label: "cat-data")
            }
            fragment CatData on Cat {
                name
                breed
            }
        '''

        when:
        def operation = createExecutableNormalizedOperations(query, [:])

        then:
        operation.topLevelFields[0].children*.fieldName == ['name', 'breed', 'breed']
        def name = operation.topLevelFields[0].children[0]
        name.objectTypeNames.toList().sort() == ['Cat', 'Dog']
        def dogBreed = findField(operation, 'Dog', 'breed')
        def catBreed = findField(operation, 'Cat', 'breed')
        dogBreed.deferredExecutions.size() == 1
        catBreed.deferredExecutions.size() == 1
        def dogExecution = dogBreed.deferredExecutions.first()
        def catExecution = catBreed.deferredExecutions.first()
        dogExecution.label == 'dog-data'
        catExecution.label == 'cat-data'
        dogExecution.possibleTypes*.name == ['Dog']
        catExecution.possibleTypes*.name == ['Cat']
        name.deferredExecutions == ([dogExecution, catExecution] as Set)
        !dogExecution.is(catExecution)
    }

    def "shared defer keeps type coverage separate between parent fields"() {
        given:
        String query = '''
            query q {
                dog { ...AnimalFields }
                animal { ...AnimalFields }
            }
            fragment AnimalFields on Animal {
                ...AnimalData @defer(label: "animal-data")
            }
            fragment AnimalData on Animal {
                name
                age
            }
        '''

        when:
        def operation = createExecutableNormalizedOperations(query, [:])

        then:
        operation.topLevelFields*.fieldName == ['dog', 'animal']
        def dogFields = operation.topLevelFields[0].children
        def animalFields = operation.topLevelFields[1].children
        dogFields*.fieldName == ['name', 'age']
        animalFields*.fieldName == ['name', 'age']
        dogFields[0].deferredExecutions.size() == 1
        animalFields[0].deferredExecutions.size() == 1
        dogFields[0].deferredExecutions == dogFields[1].deferredExecutions
        animalFields[0].deferredExecutions == animalFields[1].deferredExecutions
        def dogExecution = dogFields[0].deferredExecutions.first()
        def animalExecution = animalFields[0].deferredExecutions.first()
        dogExecution.label == 'animal-data'
        animalExecution.label == 'animal-data'
        dogExecution.possibleTypes*.name == ['Dog']
        animalExecution.possibleTypes*.name.sort() == ['Cat', 'Dog', 'Fish']
        !dogExecution.is(animalExecution)
    }

    def "duplicate deferred fragment spreads with the same label are rejected"() {
        given:
        String query = '''
          query q {
            dog {
                ...DogData @defer(label: "DogField$defer$DogData")
                ...DogData @defer(label: "DogField$defer$DogData")
            }
          }

          fragment DogData on Dog {
            name
            breed
          }
        '''

        when:
        createExecutableNormalizedOperations(query, [:])

        then:
        def exception = thrown(AssertException)
        exception.message == 'Internal error: should never happen: Duplicated @defer labels are not allowed: [DogField$defer$DogData]'
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
                def deferExecutions = field.deferredExecutions
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

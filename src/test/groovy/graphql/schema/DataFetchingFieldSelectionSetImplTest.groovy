package graphql.schema

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.StarWarsData
import graphql.TestUtil
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class DataFetchingFieldSelectionSetImplTest extends Specification {

    def starWarsQuery = '''
            {
                human {
                    name
                    appearsIn
                    friends {
                        name
                        appearsIn
                        friends {
                            name
                            appearsIn
                        }   
                    }
                    ...FriendsAndFriendsFragment
                }
            }
            
            fragment FriendsAndFriendsFragment on Character {
                friends {
                    name 
                    friends {
                        name
                    }
                }
            }
        '''

    DataFetchingFieldSelectionSet selectionSet = null
    DataFetcher humanDF = { DataFetchingEnvironment env ->
        selectionSet = env.getSelectionSet()
        return StarWarsData.humanDataFetcher.get(env)
    }

    RuntimeWiring starWarsRuntimeWiring = newRuntimeWiring()
            .type(newTypeWiring("QueryType").dataFetcher("human", humanDF))
            .type(newTypeWiring("Character").typeResolver({ env -> env.getSchema().getType("Human") }))
            .build()

    def starWarsSchema = TestUtil.schemaFile("starWarsSchemaWithArguments.graphqls", starWarsRuntimeWiring)
    def starWarsGraphql = GraphQL.newGraphQL(starWarsSchema).build()


    def "glob pattern matching works"() {

        def ei = ExecutionInput.newExecutionInput(starWarsQuery).build()
        def er = starWarsGraphql.execute(ei)

        expect:
        er.errors.isEmpty()

        !selectionSet.contains(null)
        !selectionSet.contains("")
        !selectionSet.contains("rubbish")
        !selectionSet.contains("rubbish*")
        !selectionSet.contains("rubbish?")

        //
        // glob matching works for single level fields
        //
        selectionSet.contains("appearsIn")
        selectionSet.contains("appearsIn*")
        selectionSet.contains("appearsI?")
        selectionSet.contains("appear?In")
        selectionSet.contains("app*In")

        //
        // glob matching works for multi level fields
        //
        selectionSet.contains("friends/*")
        selectionSet.contains("friends/name")
        selectionSet.contains("friends/name*")
        selectionSet.contains("friends/nam*")
        selectionSet.contains("friends/nam?")

        //
        // we basically have Java glob matching happening here
        // so no need to test all its functions
        //
        selectionSet.contains("friends/friends/name")
        selectionSet.contains("friends/friends/name*")
        selectionSet.contains("friends/friends/*")
        selectionSet.contains("friends/friends/**")

        // PathMatcher matches on segment eg the bit between / and /
        selectionSet.contains("**/friends/name")
        selectionSet.contains("**/name")
        selectionSet.contains("*/name")

        !selectionSet.contains("?/name")
        !selectionSet.contains("friends/friends/rubbish")

        //
        // allOf matching
        selectionSet.containsAllOf("name")
        selectionSet.containsAllOf("name", "appearsIn", "friends")
        !selectionSet.containsAllOf("name", "appearsIn", "friends", "notPresent")

        //
        // anyOf matching
        selectionSet.containsAnyOf("name")
        selectionSet.containsAnyOf("name", "appearsIn", "friends", "notPresent")
        !selectionSet.containsAnyOf("notPresent")
        !selectionSet.containsAnyOf("notPresent", "alsoNotPresent")
    }


    def relayQuery = '''
            {
              things(first: 10) {
                nodes {
                  ... thingInfo @skip(if:false)
                  ... dateInfo @skip(if:true)
                  ...fix
                }
                edges {
                  cursor
                  node {
                    description
                    status {
                      ...status @skip(if:false)
                    }
                  }
                }
                totalCount
              }
            }
            
            fragment thingInfo on Thing {
              key
              summary
              status {
                ...status
              }
            }
            
            fragment fix on Thing {
              stuff  {
                name
              }
            }
            
            fragment status on Status {
              name
            }
            
            fragment dateInfo on Thing {
                createdDate
                lastUpdatedDate
            }
        '''

    DataFetcher thingConnectionDF = { DataFetchingEnvironment env ->
        selectionSet = env.getSelectionSet()
        return [totalCount: 0]
    }

    RuntimeWiring relayRuntimeWiring = newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("things", thingConnectionDF))
            .type(newTypeWiring("Node").typeResolver({ env -> env.getSchema().getType("Thing") }))
            .build()

    def relaySchema = TestUtil.schemaFile("thingRelaySchema.graphqls", relayRuntimeWiring)
    def relayGraphql = GraphQL.newGraphQL(relaySchema).build()

    def "test getting sub selected fields by name"() {

        when:
        def ei = ExecutionInput.newExecutionInput(relayQuery).build()
        def er = relayGraphql.execute(ei)

        then:
        er.getErrors().isEmpty()

        def selectedNodesField = selectionSet.getFields("nodes")[0]
        selectedNodesField.getName() == "nodes"
        GraphQLTypeUtil.simplePrint(selectedNodesField.type) == "[Thing]"
        selectedNodesField.getSelectionSet().contains("key")
        selectedNodesField.getSelectionSet().contains("summary")
        selectedNodesField.getSelectionSet().contains("status")
        selectedNodesField.getSelectionSet().contains("status/name")
        selectedNodesField.getSelectionSet().contains("status*")
        selectedNodesField.getSelectionSet().contains("status/*")

        // directives are respected
        !selectedNodesField.getSelectionSet().contains("createdDate")
        !selectedNodesField.getSelectionSet().contains("lastUpdatedDate")

        when:
        def selectedKeyField = selectedNodesField.getSelectionSet().getFields("key")[0]

        then:
        selectedKeyField.getName() == "key"
        GraphQLTypeUtil.simplePrint(selectedKeyField.type) == "String"

        when:
        def selectedStatusField = selectedNodesField.getSelectionSet().getFields("status")[0]

        then:
        selectedStatusField.getName() == "status"
        GraphQLTypeUtil.simplePrint(selectedStatusField.type) == "Status"
        selectedStatusField.getSelectionSet().contains("name")

        // jump straight to compound fq name (which is 2 down from 'nodes')
        when:
        def selectedStatusNameField = selectedNodesField.getSelectionSet().getFields("status/name")[0]

        then:
        selectedStatusNameField.getName() == "name"
        GraphQLTypeUtil.simplePrint(selectedStatusNameField.type) == "String"

    }

    def "test getting sub selected fields by glob"() {

        when:
        def ei = ExecutionInput.newExecutionInput(relayQuery).build()
        def er = relayGraphql.execute(ei)

        then:
        er.getErrors().isEmpty()
        List<SelectedField> selectedUnderNodesAster = selectionSet.getFields("nodes/*")

        selectedUnderNodesAster.size() == 4
        def sortedSelectedUnderNodesAster = new ArrayList<>(selectedUnderNodesAster).sort({ sf -> sf.name })

        def fieldNames = sortedSelectedUnderNodesAster.collect({ sf -> sf.name })
        fieldNames == ["key", "status", "stuff", "summary"]

        GraphQLTypeUtil.simplePrint(sortedSelectedUnderNodesAster[0].type) == "String"
        GraphQLTypeUtil.simplePrint(sortedSelectedUnderNodesAster[1].type) == "Status"
        GraphQLTypeUtil.simplePrint(sortedSelectedUnderNodesAster[2].type) == "Stuff"
        GraphQLTypeUtil.simplePrint(sortedSelectedUnderNodesAster[3].type) == "String"

        // descend one down from here Status.name which has not further sub selection
        when:
        def statusName = sortedSelectedUnderNodesAster[1].getSelectionSet().getFields("name")[0]

        then:
        statusName.name == "name"
        GraphQLTypeUtil.simplePrint(statusName.type) == "String"
        statusName.getSelectionSet().getFields().isEmpty()
    }

    def "test that aster aster is equal to get all"() {
        when:
        def ei = ExecutionInput.newExecutionInput(relayQuery).build()
        def er = relayGraphql.execute(ei)

        then:
        er.getErrors().isEmpty()
        List<SelectedField> allFieldsViaAsterAster = selectionSet.getFields("**")
        List<SelectedField> allFields = selectionSet.getFields()

        then:

        allFieldsViaAsterAster.size() == 14
        allFields.size() == 14
        def allFieldsViaAsterAsterSorted = new ArrayList<>(allFieldsViaAsterAster).sort({ sf -> sf.qualifiedName })
        def allFieldsSorted = new ArrayList<>(allFields).sort({ sf -> sf.qualifiedName })

        def expectedFieldNames = [
                "edges",
                "edges/cursor",
                "edges/node",
                "edges/node/description",
                "edges/node/status",
                "edges/node/status/name",
                "nodes",
                "nodes/key",
                "nodes/status",
                "nodes/status/name",
                "nodes/stuff",
                "nodes/stuff/name",
                "nodes/summary",
                "totalCount"
        ]
        allFieldsViaAsterAsterSorted.collect({ sf -> sf.qualifiedName }) == expectedFieldNames
        allFieldsSorted.collect({ sf -> sf.qualifiedName }) == expectedFieldNames

        when:
        def expectedFullyQualifiedFieldNames = [
                "ThingConnection.edges",
                "ThingConnection.edges/ThingEdge.cursor",
                "ThingConnection.edges/ThingEdge.node",
                "ThingConnection.edges/ThingEdge.node/Thing.description",
                "ThingConnection.edges/ThingEdge.node/Thing.status",
                "ThingConnection.edges/ThingEdge.node/Thing.status/Status.name",
                "ThingConnection.nodes",
                "ThingConnection.nodes/Thing.key",
                "ThingConnection.nodes/Thing.status",
                "ThingConnection.nodes/Thing.status/Status.name",
                "ThingConnection.nodes/Thing.stuff",
                "ThingConnection.nodes/Thing.stuff/Stuff.name",
                "ThingConnection.nodes/Thing.summary",
                "ThingConnection.totalCount"
        ]
        then:
        allFieldsViaAsterAsterSorted.collect({ sf -> sf.fullyQualifiedName }) == expectedFullyQualifiedFieldNames
        allFieldsSorted.collect({ sf -> sf.fullyQualifiedName }) == expectedFullyQualifiedFieldNames

    }

    def "fields are returned in pre order"() {

        when:
        def ei = ExecutionInput.newExecutionInput(relayQuery).build()
        def er = relayGraphql.execute(ei)

        then:
        er.getErrors().isEmpty()

        List<SelectedField> fieldsGlob = selectionSet.getFields("**")
        List<SelectedField> fields = selectionSet.getFields()

        def expectedFieldName = [
                "nodes",
                "nodes/key",
                "nodes/summary",
                "nodes/status",
                "nodes/status/name",
                "nodes/stuff",
                "nodes/stuff/name",
                "edges",
                "edges/cursor",
                "edges/node",
                "edges/node/description",
                "edges/node/status",
                "edges/node/status/name",
                "totalCount"
        ]

        then:
        fieldsGlob.collect({ field -> field.qualifiedName }) == expectedFieldName
        fields.collect({ field -> field.qualifiedName }) == expectedFieldName
    }

    def petSDL = '''
            type Query {
                petUnion : PetUnion
                pet(qualifier : String) : Pet
                leads : [Lead]
            }
            
            interface Pet {
                name(nameArg : String) : String
                pet(qualifier : String) : Pet
                petUnion : PetUnion
                lead : Lead
            }
            
            type Dog implements Pet {
                name(nameArg : String) : String
                pet(qualifier : String) : Pet
                petUnion : PetUnion
                lead : Lead
                woof : String
            }

            type Bird implements Pet {
                name(nameArg : String) : String
                pet(qualifier : String) : Pet
                petUnion : PetUnion
                lead : Lead
                tweet : String
            }
            
            type Cat implements Pet {
                name(nameArg : String) : String
                pet(qualifier : String) : Pet
                petUnion : PetUnion
                lead : Lead
                meow : String
            }
            
            type Lead {
                material : String
            }
            
            union PetUnion = Dog | Cat
                
        '''

    def lassie = [name: "lassie", lead: [material: "leather"]]
    def grumpyCat = [name: "grumpCat", pet: lassie, petUnion: lassie, lead: [material: "leather"]]
    def fido = [name: "fido", pet: grumpyCat, petUnion: grumpyCat, lead: [material: "leather"]]

    DataFetchingFieldSelectionSet petSelectionSet = null
    DataFetcher petDF = { DataFetchingEnvironment env ->
        petSelectionSet = env.getSelectionSet()
        return fido
    }
    DataFetchingFieldSelectionSet petUnionSelectionSet = null
    DataFetcher petUnionDF = { DataFetchingEnvironment env ->
        petUnionSelectionSet = env.getSelectionSet()
        return fido
    }

    DataFetchingFieldSelectionSet leadsSelectionSet = null
    DataFetcher leadsDF = { DataFetchingEnvironment env ->
        leadsSelectionSet = env.getSelectionSet()
        return [[material: "rope"]]
    }

    DataFetchingFieldSelectionSet leadSelectionSet = null
    DataFetcher leadDF = { DataFetchingEnvironment env ->
        leadSelectionSet = env.getSelectionSet()
        return [material: "plastic"]
    }

    DataFetchingFieldSelectionSet simpleSelectionSet = null
    DataFetcher simpleSF = { DataFetchingEnvironment env ->
        simpleSelectionSet = env.getSelectionSet()
        def pdf = PropertyDataFetcher.fetching(env.getFieldDefinition().getName())
        return pdf.get(env)
    }

    def typeResolver = { e -> e.getSchema().getObjectType("Dog") }
    RuntimeWiring runtimeWiring = newRuntimeWiring()
            .type(newTypeWiring("Query")
                    .dataFetcher("leads", leadsDF)
                    .dataFetcher("pet", petDF)
                    .dataFetcher("petUnion", petUnionDF)
            )
            .type(newTypeWiring("Dog")
                    .dataFetcher("name", simpleSF)
                    .dataFetcher("lead", leadDF)
            )
            .type(newTypeWiring("Pet")
                    .typeResolver(typeResolver)
            )
            .type(newTypeWiring("PetUnion")
                    .typeResolver(typeResolver)
            )
            .build()

    def petSchema = TestUtil.graphQL(petSDL, runtimeWiring).build()

    def "test normalised selection occurs on interfaces"() {

        when:
        def query = '''
        {
            pet(qualifier : "onPet") {
                name(nameArg : "OnPet")
                lead { material }
                ... on Cat {
                    n1: name(nameArg : "OnCatN1")
                    n2: name(nameArg : "OnCatN2")
                } 
            }
 
        }
        '''
        def ei = ExecutionInput.newExecutionInput(query).build()
        def er = petSchema.execute(ei)
        then:
        er.errors.isEmpty()

        petSelectionSet.contains("name")
        petSelectionSet.contains("*Dog*.name")
        petSelectionSet.contains("*Cat*.name")
        petSelectionSet.contains("*Bird*.name")
        petSelectionSet.contains("*Bird*.*")
        petSelectionSet.contains("*name")
        !petSelectionSet.contains("notPresent")

        petSelectionSet.containsAnyOf("name", "lead", "notPresent")
        petSelectionSet.containsAllOf("name", "lead")
        !petSelectionSet.containsAllOf("name", "lead", "notPresent")

        when:
        def selectedFields = petSelectionSet.getFields("name")

        then:
        selectedFields.size() == 3
        assertTheyAreExpected(selectedFields, ["[Bird, Cat, Dog].name", "n1:Cat.name", "n2:Cat.name"])

        def byResultKey = petSelectionSet.getFieldsGroupedByResultKey("name")
        byResultKey.size() == 3
        assertTheyAreExpected(byResultKey["name"], ["[Bird, Cat, Dog].name"])
        assertTheyAreExpected(byResultKey["n1"], ["n1:Cat.name"])
        assertTheyAreExpected(byResultKey["n2"], ["n2:Cat.name"])

        petSelectionSet.contains("lead")
        petSelectionSet.contains("lead/material")

        when:
        selectedFields = petSelectionSet.getFields("*Dog*.name")
        then:
        selectedFields.size() == 1

        when:
        selectedFields = new ArrayList<>(petSelectionSet.getFields("*lead*"))
        selectedFields.sort(byName())

        then:
        selectedFields.size() == 1
        assertTheyAreExpected(selectedFields, ["[Bird, Cat, Dog].lead"])
        // we can work our sub selection from them as well
        selectedFields[0].getSelectionSet().contains("material")
        !selectedFields[0].getSelectionSet().contains("rubbish")


        when:
        selectedFields = new ArrayList<>(petSelectionSet.getFields("lead/material"))
        selectedFields.sort(byName())

        then:
        selectedFields.size() == 1 //one for the Cat and Dog and Bird entries
        assertTheyAreExpected(selectedFields, ["Lead.material"])
        selectedFields[0].getParentField().getFullyQualifiedName() == "[Bird, Cat, Dog].lead"

        // parents can have computed selection sets
        def birdLead = selectedFields[0].getParentField()
        birdLead.getSelectionSet().contains("material")
        !birdLead.getSelectionSet().contains("rubbish")

        when:
        selectedFields = petSelectionSet.getFields("name", "lead**")
        then:
        selectedFields.size() == 5
    }

    def "aliasing returns values as selected fields"() {
        when:
        def query = '''
        {
            pet {
                name
                ... on Dog {
                    aliasedName : name
                }
            }
 
        }
        '''
        def ei = ExecutionInput.newExecutionInput(query).build()
        def er = petSchema.execute(ei)
        then:
        er.errors.isEmpty()

        def selectedFields = petSelectionSet.getFields("name")
        selectedFields.size() == 2
        assertTheyAreExpected(selectedFields, ["[Bird, Cat, Dog].name", "aliasedName:Dog.name"])

        def getFields = petSelectionSet.getFields()
        getFields.size() == 2
        assertTheyAreExpected(getFields, ["[Bird, Cat, Dog].name", "aliasedName:Dog.name"])

        def byResultKey = petSelectionSet.getFieldsGroupedByResultKey()
        byResultKey.size() == 2
        byResultKey.containsKey("name")
        byResultKey.containsKey("aliasedName")
    }

    def "test normalised selection occurs on unions"() {

        when:
        def query = '''
        {
                     
            petUnion {
                ... on Cat {
                    name
                    meow
                    pet(qualifier : "cat") {
                        name
                        ... on Cat {
                            name
                            meow
                        }
                    }
                }
                ... on Dog {
                    name
                    woof
                    pet(qualifier : "dog") {
                        name
                        ... on Dog {
                            name
                            woof
                            lead { material } 
                        }
                    }
                }
            }
        }
        '''
        def ei = ExecutionInput.newExecutionInput(query).build()
        def er = petSchema.execute(ei)
        then:
        er.errors.isEmpty()

        petUnionSelectionSet.contains("name")
        petUnionSelectionSet.contains("meow")
        petUnionSelectionSet.contains("pet")
        petUnionSelectionSet.contains("pet/name")
        petUnionSelectionSet.contains("pet/woof")
        petUnionSelectionSet.contains("pet/meow")
        petUnionSelectionSet.contains("pet/lead")
        petUnionSelectionSet.contains("pet/lead/material")

        when:
        def selectedFields = petUnionSelectionSet.getFields("name")
        then:
        selectedFields.size() == 1
        assertTheyAreExpected(selectedFields, ["[Cat, Dog].name"])
    }

    def "test normalised selection occurs on objects"() {

        when:
        def query = '''
        {
            leads { 
                material 
            }
        }      
        '''
        def ei = ExecutionInput.newExecutionInput(query).build()
        def er = petSchema.execute(ei)
        then:
        er.errors.isEmpty()

        leadsSelectionSet.contains("material")
        leadsSelectionSet.contains("material*")
        leadsSelectionSet.contains("material**")

        when:
        def selectedFields = leadsSelectionSet.getFields("material*")
        then:
        selectedFields.size() == 1
        assertTheyAreExpected(selectedFields, ["Lead.material"])

        def byResultKey = leadsSelectionSet.getFieldsGroupedByResultKey("material")
        byResultKey.size() == 1

    }

    def "different arguments are available for different result keys"() {
        when:
        def query = '''
        {
            pet {
                name(nameArg : "OnPet")
                ... on Cat {
                    n1: name(nameArg : "OnCatN1")
                    n2: name(nameArg : "OnCatN2")
                } 
            }
 
        }
        '''
        def ei = ExecutionInput.newExecutionInput(query).build()
        def er = petSchema.execute(ei)
        then:
        er.errors.isEmpty()

        when:
        def selectedFields = petSelectionSet.getFields("name")

        then:
        selectedFields.size() == 3
        assertTheyAreExpected(selectedFields, ["[Bird, Cat, Dog].name", "n1:Cat.name", "n2:Cat.name"])

        def byResultKey = petSelectionSet.getFieldsGroupedByResultKey("name")
        byResultKey.size() == 3

        byResultKey["name"][0].getArguments()["nameArg"] == "OnPet"

        byResultKey["n1"][0].getArguments()["nameArg"] == "OnCatN1"
        byResultKey["n2"][0].getArguments()["nameArg"] == "OnCatN2"
    }

    def "field definitions and object types are available"() {
        when:
        def query = '''
        {
            pet {
                name(nameArg : "OnPet")
            }
         }
        '''
        def ei = ExecutionInput.newExecutionInput(query).build()
        def er = petSchema.execute(ei)
        then:
        er.errors.isEmpty()

        when:
        def selectedFields = new ArrayList<>(petSelectionSet.getFields("name"))
        selectedFields.sort(byName())

        then:
        selectedFields[0].getObjectTypeNames() == ["Bird", "Cat", "Dog"]
        selectedFields[0].getName() == "name"

    }

    def "fragments inline and defined work as expected"() {
        // this is repeating a lot of the underlying NormalisedField tests - so this is not extensively tested here
        when:
        def query = '''
        {
            pet {
                name
                ... on Dog {
                    name
                }
                ... CatFrag
            }
         }
         
        fragment CatFrag on Cat {
            name
        }
        '''
        def ei = ExecutionInput.newExecutionInput(query).build()
        def er = petSchema.execute(ei)
        then:
        er.errors.isEmpty()

        when:
        def selectedFields = new ArrayList<>(petSelectionSet.getFields("name"))
        selectedFields.sort(byName())

        then:
        selectedFields.size() == 1
        assertTheyAreExpected(selectedFields, ["[Bird, Cat, Dog].name"])

    }

    def "immediate fields can be found at all levels"() {
        when:
        def query = '''
        {
            pet {
                name
                ... on Dog {
                    woof
                }
                ... on Cat {
                    meow
                }
                lead {
                    material
                }
            }
         }
        '''
        def ei = ExecutionInput.newExecutionInput(query).build()
        def er = petSchema.execute(ei)
        then:
        er.errors.isEmpty()
        when:
        def selectedFields = petSelectionSet.getImmediateFields()
        then:
        assertTheyAreExpected(selectedFields, [
                "Cat.meow", "Dog.woof", "[Bird, Cat, Dog].lead", "[Bird, Cat, Dog].name"])

        when:
        selectedFields = petSelectionSet.getFields("lead")
        selectedFields = selectedFields[0].getSelectionSet().getImmediateFields()
        then:
        assertTheyAreExpected(selectedFields, ["Lead.material"])
    }

    def "simple fields do not have selection sets"() {
        when:
        def query = '''
        {
            pet {
                name(nameArg : "OnPet")
            }
         }
        '''
        def ei = ExecutionInput.newExecutionInput(query).build()
        def er = petSchema.execute(ei)
        then:
        er.errors.isEmpty()
        simpleSelectionSet.getFields().isEmpty()
    }

    def "selection supplier grabs the right type down the execution path"() {
        when:
        def query = '''
        {
            pet {
                lead { material }
                ... on Dog {
                    lead { material }
                }
            }
         }
        '''
        def ei = ExecutionInput.newExecutionInput(query).build()
        def er = petSchema.execute(ei)
        then:
        er.errors.isEmpty()

        when:
        def selectedFields = leadSelectionSet.getFields()

        then:
        selectedFields.size() == 1
        assertTheyAreExpected(selectedFields, ["Lead.material"])

        when:
        selectedFields = leadSelectionSet.getFields("material")

        then:
        selectedFields.size() == 1
        assertTheyAreExpected(selectedFields, ["Lead.material"])

    }

    def "issue 2381 - selected fields can work in mutations"() {
        def sdl = '''
            type Query {
                f :String
            }
            
            type Mutation {
                mutate : SomeType
            }
            
            type SomeType {
                selectedFieldNames : [String]
                b : String
                c : String
            }
        '''
        DataFetcher mutationDF = { DataFetchingEnvironment env ->
            def selectedFields = env.getSelectionSet().getImmediateFields()

            def fieldNames = selectedFields.collect { it.getName() }
            return ["selectedFieldNames": fieldNames]
        }
        def schema = TestUtil.schema(sdl, [Mutation: ["mutate": mutationDF]])
        def graphQL = GraphQL.newGraphQL(schema).build()

        when:
        def er = graphQL.execute('''mutation M { 
            mutate {
                selectedFieldNames
                b
                c
            }
        }''')
        then:
        er.errors.isEmpty()
        er.data["mutate"]["selectedFieldNames"] == ["selectedFieldNames", "b", "c"]
    }

    def "issue 2736 - test interface fields with different output types on the implementations - covariance"() {
        def sdl = """
        interface Animal {
            parent: Animal
            name: String
        }
        type Cat implements Animal {
            name: String
            parent: Cat
        }
        type Dog implements Animal {
            name: String
            parent: Dog
            isGoodBoy: Boolean
        }
        type Query {
            animal: Animal
        }
        """

        def query = """
        {
            animal {
                parent {
                    name
                }
            }
        }
        """


        DataFetcher dataFetcher = { DataFetchingEnvironment env ->
            selectionSet = env.getSelectionSet()
        }
        RuntimeWiring runtimeWiring = newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("animal", dataFetcher))
                .type(newTypeWiring("Animal").typeResolver({ env -> env.schema.getObjectType("Dog") }))
                .build()
        def schema = TestUtil.schema(sdl, runtimeWiring)
        def graphQL = GraphQL.newGraphQL(schema).build()

        when:
        def er = graphQL.execute(query)
        then:
        er.errors.isEmpty()

        selectionSet.contains("parent") // short syntax
        selectionSet.contains("parent/name") // short syntax
        selectionSet.contains("*Cat*.parent/*Cat*.name")
        selectionSet.contains("*Dog*.parent/*Dog*.name")

    }

    static Comparator<SelectedField> byName() {
        { o1, o2 -> (o1.getQualifiedName() <=> o2.getQualifiedName()) }
    }

    static void assertTheyAreExpected(List<SelectedField> selectedFields, List<String> expected) {
        def names = selectedFields.collect({ sf -> mkSpecialName(sf) })
        names.sort()
        assert names == expected, "Not the right selected fields"
    }

    static String mkSpecialName(SelectedField selectedField) {
        def names = selectedField.getObjectTypeNames()
        (selectedField.getAlias() == null ? "" : selectedField.getAlias() + ":") + (names.size() > 1 ? names.toString() : names.get(0)) + "." + selectedField.getName()
    }
}

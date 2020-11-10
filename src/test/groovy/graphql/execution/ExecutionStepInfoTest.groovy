package graphql.execution

import graphql.ExecutionInput
import graphql.Scalars
import graphql.TestUtil
import graphql.language.Field
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeUtil
import graphql.schema.TypeResolver
import spock.lang.Specification

import java.util.function.Function

import static ExecutionStepInfo.newExecutionStepInfo
import static graphql.Scalars.GraphQLString
import static graphql.TestUtil.mergedField
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLTypeUtil.simplePrint
import static graphql.schema.GraphQLTypeUtil.unwrapAll
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class ExecutionStepInfoTest extends Specification {

    def field = new Field("someAstField")
    def mergedField = mergedField(field)

    def field1Def = newFieldDefinition().name("field1").type(GraphQLString).build()

    def interfaceType = GraphQLInterfaceType.newInterface().name("Interface")
            .field(field1Def)
            .typeResolver({ env -> null })
            .build()

    def fieldType = GraphQLObjectType.newObject()
            .name("FieldType")
            .field(field1Def)
            .build()

    def rootType = GraphQLObjectType.newObject()
            .name("RootType")
            .field(newFieldDefinition().name("rootField1").type(fieldType))
            .build()


    def "basic hierarchy"() {
        given:
        def rootTypeInfo = newExecutionStepInfo().type(rootType).build()
        def fieldTypeInfo = newExecutionStepInfo().type(fieldType).fieldDefinition(field1Def).field(mergedField).parentInfo(rootTypeInfo).build()
        def nonNullFieldTypeInfo = newExecutionStepInfo().type(nonNull(fieldType)).parentInfo(rootTypeInfo).build()
        def listTypeInfo = newExecutionStepInfo().type(list(fieldType)).parentInfo(rootTypeInfo).build()

        expect:
        rootTypeInfo.getUnwrappedNonNullType() == rootType
        rootTypeInfo.field == null
        rootTypeInfo.fieldDefinition == null
        !rootTypeInfo.hasParent()

        fieldTypeInfo.getUnwrappedNonNullType() == fieldType
        fieldTypeInfo.hasParent()
        fieldTypeInfo.parent.type == rootType
        !fieldTypeInfo.isNonNullType()
        fieldTypeInfo.getFieldDefinition() == field1Def
        fieldTypeInfo.getField() == mergedField

        nonNullFieldTypeInfo.getUnwrappedNonNullType() == fieldType
        nonNullFieldTypeInfo.hasParent()
        nonNullFieldTypeInfo.parent.type == rootType
        nonNullFieldTypeInfo.isNonNullType()

        list(fieldType).isEqualTo(listTypeInfo.getUnwrappedNonNullType())
        listTypeInfo.hasParent()
        listTypeInfo.parent.type == rootType
        listTypeInfo.isListType()
    }

    def "morphing type works"() {
        given:
        def rootTypeInfo = newExecutionStepInfo().type(rootType).build()
        def interfaceTypeInfo = newExecutionStepInfo().type(interfaceType).parentInfo(rootTypeInfo).build()
        def morphedTypeInfo = interfaceTypeInfo.changeTypeWithPreservedNonNull(fieldType)

        expect:

        interfaceTypeInfo.type == interfaceType
        morphedTypeInfo.type == fieldType
    }

    def "unwrapping stack works"() {

        given:
        // [[String!]!]
        GraphQLType wrappedType = list(nonNull(list(nonNull(GraphQLString))))
        def stack = GraphQLTypeUtil.unwrapType(wrappedType)

        expect:
        stack.pop() == GraphQLString
        stack.pop() instanceof GraphQLNonNull
        stack.pop() instanceof GraphQLList
        stack.pop() instanceof GraphQLNonNull
        stack.pop() instanceof GraphQLList
        stack.isEmpty()
    }

    List<ExecutionStepInfo> executionTypeInfos = []

    class ExecutionStepInfoCapturingDF implements DataFetcher {
        Function function

        ExecutionStepInfoCapturingDF(function) {
            this.function = function
        }

        @Override
        Object get(DataFetchingEnvironment environment) {
            executionTypeInfos.add(environment.getExecutionStepInfo())
            def val = function.apply(environment)
            return val
        }
    }

    class User {
        def name
        List<User> friends
        List<User> mates

        User(name, List<User> friends) {
            this.name = name
            this.friends = friends
            this.mates = friends
        }
    }

    def "end to end type hierarchy is maintained during execution"() {
        def spec = '''
            type Query {
                hero(id:String) : User
            }
            
            type User {
                name : String
                friends(closeFriends: Boolean) : [User]
                mates : [User]
            }
        '''

        def bilbo = new User("bilbo", [])
        def gandalf = new User("gandalf", [bilbo])
        def frodo = new User("frodo", [bilbo, gandalf])
        def samwise = new User("samwise", [bilbo, gandalf, frodo])

        DataFetcher samwiseDF = new ExecutionStepInfoCapturingDF({ env -> env.getSource() })
        DataFetcher friendsDF = new ExecutionStepInfoCapturingDF({ env -> (env.getSource() as User).friends })

        def runtimeWiring = newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("hero", samwiseDF))
                .type(newTypeWiring("User").dataFetcher("friends", friendsDF))
                .type(newTypeWiring("User").dataFetcher("mates", friendsDF))
                .build()

        def graphQL = TestUtil.graphQL(spec, runtimeWiring).build()

        def query = ''' 
            {
                hero(id : "1234")  {
                    name
                    friends(closeFriends : true) {
                        name
                        mates {
                            name
                        }
                    }
                } 
            }
            '''
        def executionInput = ExecutionInput.newExecutionInput().root(samwise).query(query).build()
        when:
        def er = graphQL.execute(executionInput)
        then:
        er.errors.isEmpty()

        executionTypeInfos.size() == 5

        executionTypeInfos[0].path.toString() == "/hero"
        (executionTypeInfos[0].type as GraphQLObjectType).name == "User"
        executionTypeInfos[0].field.getName() == "hero"
        executionTypeInfos[0].parent.path == ResultPath.rootPath()
        (executionTypeInfos[0].parent.type as GraphQLObjectType).name == "Query"
        executionTypeInfos[0].arguments == [id: "1234"]
        executionTypeInfos[0].getArgument("id") == "1234"

        executionTypeInfos[1].path.toString() == "/hero/friends"
        executionTypeInfos[1].field.name == "friends"
        (unwrapAll(executionTypeInfos[1].type) as GraphQLObjectType).name == "User"
        executionTypeInfos[1].parent.path.toString() == "/hero"
        executionTypeInfos[1].parent.field.name == "hero"
        (unwrapAll(executionTypeInfos[1].parent.type) as GraphQLObjectType).name == "User"
        executionTypeInfos[1].arguments == [closeFriends: true]
        executionTypeInfos[1].parent.arguments == [id: "1234"]

        // we have 3 list items here
        for (int i = 2; i < 5; i++) {
            assert executionTypeInfos[i].path.toString() == "/hero/friends[" + (i - 2) + "]/mates"
            assert executionTypeInfos[i].field.name == "mates"
            assert (unwrapAll(executionTypeInfos[i].type) as GraphQLObjectType).name == "User"

            assert executionTypeInfos[i].parent.path.toString() == "/hero/friends[" + (i - 2) + "]"
            assert executionTypeInfos[i].parent.field.name == "friends"
            assert (unwrapAll(executionTypeInfos[i].parent.type) as GraphQLObjectType).name == "User"
        }
    }


    def "transform copies fieldContainer"() {
        given:
        ExecutionStepInfo executionStepInfo = newExecutionStepInfo()
                .type(Scalars.GraphQLString)
                .fieldContainer(GraphQLObjectType.newObject().name("foo").build())
                .build()
        when:
        def transformed = executionStepInfo.transform({ builder -> builder })

        then:
        transformed.getObjectType() == executionStepInfo.getObjectType()
    }

    def "step info for list of lists of abstract type"() {
        def spec = '''
            type Query {
                pets: [[Pet]]
            }
            
            interface Pet {
                names : [String]
            }
            type Cat implements Pet {
                names : [String]
            }
            type Dog implements Pet {
                names : [String]
            }
        '''

        def dog = [name: "Dog", __typename: "Dog"]
        def cat = [name: "Cat", __typename: "Cat"]
        def petTypeResolver = { it -> it.schema.getObjectType(it.object.__typename) } as TypeResolver


        ExecutionStepInfo dogStepInfo
        def dogDf = { it ->
            dogStepInfo = it.getExecutionStepInfo()
            return null
        } as DataFetcher


        ExecutionStepInfo catStepInfo
        def catDf = { it ->
            catStepInfo = it.getExecutionStepInfo()
            return null
        } as DataFetcher

        def pets = [[dog], [cat]]
        def runtimeWiring = newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("pets", { it -> pets }))
                .type(newTypeWiring("Pet").typeResolver(petTypeResolver).build())
                .type(newTypeWiring("Cat").dataFetcher("names", catDf).build())
                .type(newTypeWiring("Dog").dataFetcher("names", dogDf).build())
                .build()

        def graphQL = TestUtil.graphQL(spec, runtimeWiring).build()

        def query = ''' 
            {
               pets {names} 
            }
            '''
        def executionInput = ExecutionInput.newExecutionInput().query(query).build()
        when:
        graphQL.execute(executionInput)
        then:
        // dog info
        dogStepInfo.path.toString() == "/pets[0][0]/names"
        simplePrint(dogStepInfo.type) == "[String]"

        dogStepInfo.parent.path.toString() == "/pets[0][0]"
        simplePrint(dogStepInfo.parent.type) == "Dog"

        dogStepInfo.parent.parent.path.toString() == "/pets[0]"
        simplePrint(dogStepInfo.parent.parent.type) == "[Pet]"

        dogStepInfo.parent.parent.parent.path.toString() == "/pets"
        simplePrint(dogStepInfo.parent.parent.parent.type) == "[[Pet]]"

        // cat info
        catStepInfo.path.toString() == "/pets[1][0]/names"
        simplePrint(catStepInfo.type) == "[String]"

        catStepInfo.parent.path.toString() == "/pets[1][0]"
        simplePrint(catStepInfo.parent.type) == "Cat"

        catStepInfo.parent.parent.path.toString() == "/pets[1]"
        simplePrint(catStepInfo.parent.parent.type) == "[Pet]"

        catStepInfo.parent.parent.parent.path.toString() == "/pets"
        simplePrint(catStepInfo.parent.parent.parent.type) == "[[Pet]]"


    }
}

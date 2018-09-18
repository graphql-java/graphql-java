package graphql.execution

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.language.Field
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLType
import spock.lang.Specification

import java.util.function.Function

import static ExecutionTypeInfo.newTypeInfo
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLTypeUtil.unwrapAll
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class ExecutionTypeInfoTest extends Specification {

    def field = new Field("someAstField")

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
        def rootTypeInfo = newTypeInfo().type(rootType).build()
        def fieldTypeInfo = newTypeInfo().type(fieldType).fieldDefinition(field1Def).field(field).parentInfo(rootTypeInfo).build()
        def nonNullFieldTypeInfo = newTypeInfo().type(nonNull(fieldType)).parentInfo(rootTypeInfo).build()
        def listTypeInfo = newTypeInfo().type(list(fieldType)).parentInfo(rootTypeInfo).build()

        expect:
        rootTypeInfo.type == rootType
        rootTypeInfo.field == null
        rootTypeInfo.fieldDefinition == null
        !rootTypeInfo.hasParentType()

        fieldTypeInfo.type == fieldType
        fieldTypeInfo.hasParentType()
        fieldTypeInfo.parentTypeInfo.type == rootType
        !fieldTypeInfo.isNonNullType()
        fieldTypeInfo.getFieldDefinition() == field1Def
        fieldTypeInfo.getField() == field

        nonNullFieldTypeInfo.type == fieldType
        nonNullFieldTypeInfo.hasParentType()
        nonNullFieldTypeInfo.parentTypeInfo.type == rootType
        nonNullFieldTypeInfo.isNonNullType()

        listTypeInfo.type == list(fieldType)
        listTypeInfo.hasParentType()
        listTypeInfo.parentTypeInfo.type == rootType
        listTypeInfo.isListType()
    }

    def "morphing type works"() {
        given:
        def rootTypeInfo = newTypeInfo().type(rootType).build()
        def interfaceTypeInfo = newTypeInfo().type(interfaceType).parentInfo(rootTypeInfo).build()
        def morphedTypeInfo = interfaceTypeInfo.treatAs(fieldType)

        expect:

        interfaceTypeInfo.type == interfaceType
        morphedTypeInfo.type == fieldType
    }

    def "unwrapping stack works"() {

        given:
        // [[String!]!]
        GraphQLType wrappedType = list(nonNull(list(nonNull(GraphQLString))))
        def stack = ExecutionTypeInfo.unwrapType(wrappedType)

        expect:
        stack.pop() == GraphQLString
        stack.pop() instanceof GraphQLNonNull
        stack.pop() instanceof GraphQLList
        stack.pop() instanceof GraphQLNonNull
        stack.pop() instanceof GraphQLList
        stack.isEmpty()
    }

    List<ExecutionTypeInfo> executionTypeInfos = []

    class ExecutionTypeInfoCapturingDF implements DataFetcher {
        Function function

        ExecutionTypeInfoCapturingDF(function) {
            this.function = function
        }

        @Override
        Object get(DataFetchingEnvironment environment) {
            executionTypeInfos.add(environment.getFieldTypeInfo())
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
                hero : User
            }
            
            type User {
                name : String
                friends : [User]
                mates : [User]
            }
        '''

        def bilbo = new User("bilbo", [])
        def gandalf = new User("gandalf", [bilbo])
        def frodo = new User("frodo", [bilbo, gandalf])
        def samwise = new User("samwise", [bilbo, gandalf, frodo])

        DataFetcher samwiseDF = new ExecutionTypeInfoCapturingDF({ env -> env.getSource() })
        DataFetcher friendsDF = new ExecutionTypeInfoCapturingDF({ env -> (env.getSource() as User).friends })

        def runtimeWiring = newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("hero", samwiseDF))
                .type(newTypeWiring("User").dataFetcher("friends", friendsDF))
                .type(newTypeWiring("User").dataFetcher("mates", friendsDF))
                .build()

        def graphQL = TestUtil.graphQL(spec, runtimeWiring).build()

        def query = ''' 
            {
                hero {
                    name
                    friends {
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
        executionTypeInfos[0].parentTypeInfo.path == ExecutionPath.rootPath()
        (executionTypeInfos[0].parentTypeInfo.type as GraphQLObjectType).name == "Query"

        executionTypeInfos[1].path.toString() == "/hero/friends"
        executionTypeInfos[1].field.name == "friends"
        (unwrapAll(executionTypeInfos[1].type) as GraphQLObjectType).name == "User"
        executionTypeInfos[1].parentTypeInfo.path.toString() == "/hero"
        executionTypeInfos[1].parentTypeInfo.field.name == "hero"
        (unwrapAll(executionTypeInfos[1].parentTypeInfo.type) as GraphQLObjectType).name == "User"

        // we have 3 list items here
        for (int i = 2; i < 5; i++) {
            assert executionTypeInfos[i].path.toString() == "/hero/friends[" + (i - 2) + "]/mates"
            assert executionTypeInfos[i].field.name == "mates"
            assert (unwrapAll(executionTypeInfos[i].type) as GraphQLObjectType).name == "User"

            assert executionTypeInfos[i].parentTypeInfo.path.toString() == "/hero/friends[" + (i - 2) + "]"
            assert executionTypeInfos[i].parentTypeInfo.field.name == "friends"
            assert (unwrapAll(executionTypeInfos[i].parentTypeInfo.type) as GraphQLObjectType).name == "User"
        }
    }
}

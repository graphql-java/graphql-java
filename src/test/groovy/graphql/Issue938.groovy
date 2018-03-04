package graphql

import graphql.language.IntValue
import graphql.schema.Coercing
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class Issue938 extends Specification {

    public static GraphQLScalarType GraphQLTimestamp = new GraphQLScalarType("Timestamp", "Timestamp", new Coercing() {
        @Override
        Object serialize(Object input) {
            return input
        }

        @Override
        Object parseValue(Object input) {
            GregorianCalendar calendar = new GregorianCalendar()
            BigInteger value = new BigInteger(String.valueOf(input))
            calendar.setTimeInMillis(value.multiply(new BigInteger("1000")).longValue())
            return calendar
        }

        @Override
        Object parseLiteral(Object input) {
            if (input == null) {
                return null
            }
            GregorianCalendar calendar = new GregorianCalendar()
            calendar.setTimeInMillis(((IntValue) input).getValue().multiply(new BigInteger("1000")).longValue())

            return calendar
        }
    })

    class UserImpl {
        int id
        String username
        String email
    }


    def "938 custom scalar and variables"() {

        def schemaSDL = '''
            
            type User {
                id : Int
                username : String 
                email : String
            }
            
            type Query {
                User(username : String!) : User
            } 
        '''
        def userDataFetcher = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment env) {
                def username = env.getArgument("username")
                return new UserImpl(id: 1, username: username, email: username + '@gmail.com')
            }
        }
        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .scalar(GraphQLTimestamp)
                .type(
                TypeRuntimeWiring.newTypeWiring("Query")
                        .dataFetcher("User", userDataFetcher)
                        .build())
                .build()
        def schema = TestUtil.schema(schemaSDL, runtimeWiring)
        def graphql = GraphQL.newGraphQL(schema).build();

        when:
        def input = ExecutionInput.newExecutionInput()
                .query('''
                        query myTwoBestFriends($friendOne: String!, $friendTwo: String!) {
                            friendOne: User(username: $friendOne) {
                                 id
                                 username
                                 email
                            }
                            friendTwo: User(username: $friendTwo) {
                                 id
                                 username
                                 email
                            }
                        }
                        ''')
                .variables(["friendOne": "joeyt", "friendTwo": "rossg"])
                .build()
        def executionResult = graphql.execute(input)

        then:
        executionResult.errors.isEmpty()
        executionResult.data == [friendOne:
                                         [id: 1, username: 'joeyt', email: 'joeyt@gmail.com'],
                                 friendTwo:
                                         [id: 1, username: 'rossg', email: 'rossg@gmail.com']
        ]

        when:

        input = ExecutionInput.newExecutionInput()
                .query('''
                        query myTwoBestFriends($friendOne: String!, $friendTwo: String!) {
                            friendOne: User(user1: $friendOne) {
                                 id
                                 username
                                 email
                            }
                            friendTwo: User(username: $friendTwo) {
                                 id
                                 username
                                 email
                            }
                        }
                        ''')
                .variables(["friendOne": "joeyt", "friendTwo": "rossg"])
                .build()
        executionResult = graphql.execute(input)

        then:
        executionResult.errors.size() == 2
        (executionResult.errors[0] as ValidationError).validationErrorType == ValidationErrorType.MissingFieldArgument
        (executionResult.errors[1] as ValidationError).validationErrorType == ValidationErrorType.UnknownArgument

    }
}

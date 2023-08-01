package graphql.schema

import graphql.Directives
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.GraphQLContext
import graphql.StarWarsSchema
import graphql.TestUtil
import graphql.language.ObjectValue
import graphql.validation.ValidationUtil
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLNonNull.nonNull

class GraphQLInputObjectTypeTest extends Specification {

    def "duplicate field definition overwrites"() {
        when:
        def inputObjectType = newInputObject().name("TestInputObjectType")
                .field(newInputObjectField().name("NAME").type(GraphQLString))
                .field(newInputObjectField().name("NAME").type(GraphQLInt))
                .build()
        then:
        inputObjectType.getName() == "TestInputObjectType"
        inputObjectType.getFieldDefinition("NAME").getType() == GraphQLInt
    }

    def "builder can change existing object into a new one"() {
        given:
        def inputObjectType = newInputObject().name("StartType")
                .description("StartingDescription")
                .field(newInputObjectField().name("Str").type(GraphQLString))
                .field(newInputObjectField().name("Int").type(GraphQLInt))
                .build()

        when:
        def transformedInputType = inputObjectType.transform({ builder ->
            builder
                    .name("NewObjectName")
                    .description("NewDescription")
                    .field(newInputObjectField().name("AddedInt").type(GraphQLInt)) // add more
                    .field(newInputObjectField().name("Int").type(GraphQLInt)) // override and change
                    .field(newInputObjectField().name("Str").type(GraphQLBoolean)) // override and change
        })
        then:

        inputObjectType.getName() == "StartType"
        inputObjectType.getDescription() == "StartingDescription"
        inputObjectType.getFieldDefinitions().size() == 2
        inputObjectType.getFieldDefinition("Int").getType() == GraphQLInt
        inputObjectType.getFieldDefinition("Str").getType() == GraphQLString

        transformedInputType.getName() == "NewObjectName"
        transformedInputType.getDescription() == "NewDescription"
        transformedInputType.getFieldDefinitions().size() == 3
        transformedInputType.getFieldDefinition("AddedInt").getType() == GraphQLInt
        transformedInputType.getFieldDefinition("Int").getType() == GraphQLInt
        transformedInputType.getFieldDefinition("Str").getType() == GraphQLBoolean
    }

    def "deprecated default value builder works"() {
        given:
        def graphQLContext = GraphQLContext.getDefault()
        def schema = GraphQLSchema.newSchema()
                .query(StarWarsSchema.queryType)
                .codeRegistry(StarWarsSchema.codeRegistry)
                .build()
        def validationUtil = new ValidationUtil()
        def inputObjectType = GraphQLInputObjectType.newInputObject()
                .name("inputObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("hello")
                        .type(nonNull(GraphQLString))
                        .defaultValue("default")) // Retain deprecated builder for test coverage
                .build()
        def objectValue = ObjectValue.newObjectValue()

        expect:
        validationUtil.isValidLiteralValue(objectValue.build(), inputObjectType, schema, graphQLContext, Locale.ENGLISH)
    }

    def "can detect one of support"() {
        when:
        def inputObjectType = newInputObject().name("TestInputObjectType")
                .field(newInputObjectField().name("NAME").type(GraphQLInt))
                .build()
        then:
        !inputObjectType.isOneOf()

        when:
        inputObjectType = newInputObject().name("TestInputObjectType")
                .field(newInputObjectField().name("NAME").type(GraphQLInt))
                .withDirective(Directives.OneOfDirective)
                .build()
        then:
        inputObjectType.isOneOf()

        when:
        inputObjectType = newInputObject().name("TestInputObjectType")
                .field(newInputObjectField().name("NAME").type(GraphQLInt))
                .withAppliedDirective(Directives.OneOfDirective.toAppliedDirective())
                .build()
        then:
        inputObjectType.isOneOf()
    }

    def "e2e test of oneOf support"() {
        def sdl = '''
            type Query {
                f(arg : OneOf) : [KV]
            }
            
            type KV {
                key : String
                value : String
            }
            
            input OneOf @oneOf {
                a : String
                b : Int
            }
        '''

        DataFetcher df = { DataFetchingEnvironment env ->
            Map<String, Object> arg = env.getArgument("arg")

            def l = []
            for (Map.Entry<String, Object> entry : arg.entrySet()) {
                l.add(["key": entry.getKey(), "value": String.valueOf(entry.getValue())])
            }
            return l
        }

        def graphQLSchema = TestUtil.schema(sdl, [Query: [f: df]])
        def graphQL = GraphQL.newGraphQL(graphQLSchema).build()

        when:
        def er = graphQL.execute('query q { f( arg : {a : "abc"}) { key value }}')
        def l = (er.data["f"] as List)
        then:
        er.errors.isEmpty()
        l.size() == 1
        l[0]["key"] == "a"
        l[0]["value"] == "abc"

        when:
        er = graphQL.execute('query q { f( arg : {b : 123}) { key value }}')
        l = (er.data["f"] as List)

        then:
        er.errors.isEmpty()
        l.size() == 1
        l[0]["key"] == "b"
        l[0]["value"] == "123"

        when:
        er = graphQL.execute('query q { f( arg : {a : "abc", b : 123}) { key value }}')
        then:
        !er.errors.isEmpty()
        er.errors[0].message == "Exception while fetching data (/f) : Exactly one key must be specified for OneOf type 'OneOf'."

        when:
        def ei = ExecutionInput.newExecutionInput('query q($var : OneOf)  { f( arg : $var) { key value }}').variables([var: [a: "abc", b: 123]]).build()
        er = graphQL.execute(ei)
        then:
        !er.errors.isEmpty()
        er.errors[0].message == "Exception while fetching data (/f) : Exactly one key must be specified for OneOf type 'OneOf'."

        when:
        ei = ExecutionInput.newExecutionInput('query q($var : OneOf)  { f( arg : $var) { key value }}').variables([var: [a: null]]).build()
        er = graphQL.execute(ei)
        then:
        !er.errors.isEmpty()
        er.errors[0].message == "Exception while fetching data (/f) : OneOf type field 'OneOf.a' must be non-null."

        // lots more covered in unit tests
    }
}

package graphql.schema.universe

import graphql.ExecutionInput
import graphql.ParseAndValidate
import graphql.Scalars
import graphql.TestUtil
import graphql.language.Document
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.universe.view.SUExecutableSchema
import graphql.validation.OperationValidationRule
import graphql.validation.QueryComplexityLimits
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class SUExecutableSchemaValidationTest extends Specification {

    @Shared
    GraphQLSchema graphQLSchema

    @Shared
    SUExecutableSchema universeSchema

    def setupSpec() {
        graphQLSchema = TestUtil.schema(schemaSdl())
        def imported = new SchemaUniverse()
                .importSchema("validation", graphQLSchema)
        universeSchema = SUExecutableSchema
                .fromGraphQLSchema(imported, graphQLSchema)
    }

    @Unroll
    def "GraphQLSchema and universe validation agree for #scenario"() {
        given:
        Document document = TestUtil.parseQuery(query)

        when:
        List<ValidationError> graphQLErrors = ParseAndValidate.validate(
                graphQLSchema,
                document,
                Locale.ENGLISH)
        List<ValidationError> universeErrors = ParseAndValidate.validate(
                universeSchema,
                document,
                Locale.ENGLISH)

        then:
        errorSnapshot(universeErrors) == errorSnapshot(graphQLErrors)
        universeErrors*.validationErrorType == expectedErrors

        where:
        scenario                    | query                                                                                                       || expectedErrors
        "valid composite selections"| 'query($id: ID!, $filter: Filter) { node(id: $id, filter: $filter) { id ... on User { name } } search { ... on Photo { url } } }' || []
        "scalar and enum literals"  | '{ count(value: "wrong") choose(mode: UNKNOWN) }'                                                           || [ValidationErrorType.WrongType, ValidationErrorType.WrongType]
        "required input fields"     | '{ node(id: "1", filter: { mode: FAST }) { id } }'                                                          || [ValidationErrorType.WrongType]
        "extra input fields"        | '{ node(id: "1", filter: { required: true, extra: true }) { id } }'                                         || [ValidationErrorType.WrongType]
        "oneOf input objects"       | '{ chooseOne(input: { name: "Ada", id: "1" }) }'                                                            || [ValidationErrorType.WrongType]
        "field and argument names"  | '{ missing node(id: "1", unknown: true) { missing } }'                                                      || [ValidationErrorType.FieldUndefined, ValidationErrorType.UnknownArgument, ValidationErrorType.FieldUndefined]
        "incompatible fragments"    | '{ node(id: "1") { ... on Query { search { __typename } } } }'                                             || [ValidationErrorType.InvalidFragmentType]
        "overlapping fields"        | '{ same: count(value: 1) same: choose(mode: FAST) }'                                                        || [ValidationErrorType.FieldsConflict]
        "overlapping enum fields"   | '{ same: currentMode same: currentState }'                                                                 || [ValidationErrorType.FieldsConflict]
        "enum subselections"        | '{ currentMode { __typename } }'                                                                           || [ValidationErrorType.SubselectionNotAllowed]
        "variable type matching"    | 'query($id: String) { node(id: $id) { id } }'                                                              || [ValidationErrorType.VariableTypeMismatch]
        "bad variable defaults"     | 'query($value: Int = "wrong") { count(value: $value) }'                                                     || [ValidationErrorType.BadValueForDefaultArg]
        "list variable defaults"    | 'query($values: [Int!] = [1, "wrong"]) { counts(values: $values) }'                                        || [ValidationErrorType.BadValueForDefaultArg]
        "variable input types"      | 'query($value: Query) { __typename }'                                                                      || [ValidationErrorType.NonInputTypeOnVariable, ValidationErrorType.UnusedVariable]
        "argument defaults"         | 'query($value: String) { withDefault(value: $value) }'                                                     || []
        "directives"                | '{ count(value: 1) @mark @unknown }'                                                                       || [ValidationErrorType.MissingDirectiveArgument, ValidationErrorType.UnknownDirective]
        "subscription roots"        | 'subscription { first { id } second { id } }'                                                              || [ValidationErrorType.SubscriptionMultipleRootFields]
        "subscription fragments"    | 'subscription { ... on Subscription { first { id } second { id } } }'                                      || [ValidationErrorType.SubscriptionMultipleRootFields]
        "fields on unions"          | '{ search { id } }'                                                                                        || [ValidationErrorType.FieldUndefined]
        "introspection fields"      | '{ __schema { queryType { name } } __type(name: "User") { name } }'                                        || []
    }

    def "all ExecutableSchema parse and validation entry points accept a universe view"() {
        given:
        Document validDocument = TestUtil.parseQuery(
                '{ count(value: 1) }')
        Document invalidDocument = TestUtil.parseQuery(
                '{ missing }')
        def skipUnknownFields = {
            it != OperationValidationRule.FIELDS_ON_CORRECT_TYPE
        }

        expect:
        ParseAndValidate.validate(
                universeSchema,
                validDocument).isEmpty()
        ParseAndValidate.validate(
                universeSchema,
                invalidDocument,
                skipUnknownFields).isEmpty()
        ParseAndValidate.validate(
                universeSchema,
                invalidDocument,
                skipUnknownFields,
                Locale.ENGLISH).isEmpty()
        ParseAndValidate.validate(
                universeSchema,
                invalidDocument,
                { true },
                Locale.ENGLISH,
                QueryComplexityLimits.NONE)*.validationErrorType ==
                [ValidationErrorType.FieldUndefined]

        and:
        !ParseAndValidate.parseAndValidate(
                universeSchema,
                ExecutionInput.newExecutionInput(
                        '{ count(value: 1) }').build()).isFailure()
        ParseAndValidate.parseAndValidate(
                universeSchema,
                ExecutionInput.newExecutionInput(
                        '{ count(').build()).isFailure()
    }

    def "custom scalar coercing is used through the universe view"() {
        given:
        GraphQLScalarType customScalar = GraphQLScalarType.newScalar()
                .name("Custom")
                .coercing(Scalars.GraphQLInt.coercing)
                .build()
        GraphQLObjectType query = GraphQLObjectType.newObject()
                .name("Query")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("value")
                        .type(Scalars.GraphQLString)
                        .argument(GraphQLArgument.newArgument()
                                .name("input")
                                .type(customScalar)))
                .build()
        GraphQLSchema source = GraphQLSchema.newSchema()
                .query(query)
                .build()
        def imported = new SchemaUniverse()
                .importSchema("custom_scalar_validation", source)
        def universe = SUExecutableSchema.fromGraphQLSchema(
                imported,
                source)
        Document document = TestUtil.parseQuery(
                '{ value(input: "not an int") }')

        expect:
        errorSnapshot(ParseAndValidate.validate(
                universe,
                document,
                Locale.ENGLISH)) ==
                errorSnapshot(ParseAndValidate.validate(
                        source,
                        document,
                        Locale.ENGLISH))
    }

    private static List<Map<String, Object>> errorSnapshot(
            List<ValidationError> errors) {
        return errors.collect {
            [
                    type      : it.validationErrorType,
                    message   : it.message,
                    queryPath : it.queryPath,
                    locations : it.locations,
                    extensions: it.extensions
            ]
        }
    }

    private static String schemaSdl() {
        return '''
            directive @mark(required: Boolean!, mode: Mode)
              repeatable on FIELD | INLINE_FRAGMENT

            interface Node {
              id: ID!
            }

            type User implements Node {
              id: ID!
              name: String
            }

            type Photo implements Node {
              id: ID!
              url: String
            }

            union SearchResult = User | Photo

            enum Mode {
              FAST
              SLOW
            }

            enum State {
              ON
              OFF
            }

            input Filter {
              required: Boolean!
              mode: Mode
            }

            input Choice @oneOf {
              name: String
              id: ID
            }

            type Query {
              node(id: ID!, filter: Filter): Node
              search: [SearchResult!]!
              choose(mode: Mode!): String
              chooseOne(input: Choice): String
              count(value: Int): Int
              counts(values: [Int!]): Int
              currentMode: Mode
              currentState: State
              withDefault(value: String! = "fallback"): String
            }

            type Subscription {
              first: User
              second: Photo
            }
        '''
    }
}

package graphql.validation


import graphql.StarWarsSchema
import graphql.TestUtil
import graphql.i18n.I18n
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import graphql.validation.LanguageTraversal
import graphql.validation.OperationValidationRule
import graphql.validation.OperationValidator
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class VariableTypesMatchTest extends Specification {
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query, GraphQLSchema schema = StarWarsSchema.starWarsSchema) {
        def document = Parser.parse(query)
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        def validationContext = new ValidationContext(schema, document, i18n)
        def languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, new OperationValidator(validationContext, errorCollector,
                { rule -> rule == OperationValidationRule.VARIABLE_TYPES_MATCH }))
    }

    def "valid variables"() {
        given:
        def query = """
            query Q(\$id: String!) {
                human(id: \$id) {
                    __typename
                }
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }

    def "invalid variables"() {
        given:
        def query = """
            query Q(\$id: String) {
                human(id: \$id) {
                    __typename
                }
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.VariableTypeMismatch)
        // #991: describe which types were mismatched in error message
        errorCollector.errors[0].message == "Validation error (VariableTypeMismatch@[human]) : Variable 'id' of type 'String' used in position expecting type 'String!'"
    }

    def "invalid variables in fragment spread"() {
        given:
        def query = """
            fragment QueryType on QueryType {
                human(id: \$xid) {
                  __typename
                }
            }

            query Invalid(\$xid: String) {
                ...QueryType
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.VariableTypeMismatch)
        errorCollector.errors[0].message == "Validation error (VariableTypeMismatch@[QueryType/human]) : Variable 'xid' of type 'String' used in position expecting type 'String!'"
    }

    def "mixed validity operations, valid first"() {
        given:
        def query = """
            fragment QueryType on QueryType {
                human(id: \$id) {
                  __typename
                }
            }

            query Valid(\$id: String!) {
                ... QueryType
            }

            query Invalid(\$id: String) {
                ... QueryType
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.VariableTypeMismatch)
        errorCollector.errors[0].message == "Validation error (VariableTypeMismatch@[QueryType/human]) : Variable 'id' of type 'String' used in position expecting type 'String!'"
    }

    def "mixed validity operations, invalid first"() {
        given:
        def query = """
            fragment QueryType on QueryType {
                human(id: \$id) {
                  __typename
                }
            }

            query Invalid(\$id: String) {
                ... QueryType
            }

            query Valid(\$id: String!) {
                ... QueryType
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.VariableTypeMismatch)
        errorCollector.errors[0].message == "Validation error (VariableTypeMismatch@[QueryType/human]) : Variable 'id' of type 'String' used in position expecting type 'String!'"
    }

    def "multiple invalid operations"() {
        given:
        def query = """
            fragment QueryType on QueryType {
                human(id: \$id) {
                  __typename
                }
            }

            query Invalid1(\$id: String) {
                ... QueryType
            }

            query Invalid2(\$id: Boolean) {
                ... QueryType
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.getErrors().size() == 2
        errorCollector.errors.any {
            it.validationErrorType == ValidationErrorType.VariableTypeMismatch &&
                it.message == "Validation error (VariableTypeMismatch@[QueryType/human]) : Variable 'id' of type 'String' used in position expecting type 'String!'"
        }
        errorCollector.errors.any {
            it.validationErrorType == ValidationErrorType.VariableTypeMismatch &&
                it.message == "Validation error (VariableTypeMismatch@[QueryType/human]) : Variable 'id' of type 'Boolean' used in position expecting type 'String!'"
        }
    }


    def "issue 3276 - invalid variables in object field values with no defaults in location"() {

        def sdl = '''
            type Query {
                items(pagination: Pagination = {limit: 1, offset: 1}): [String]
            }
            input Pagination {
                limit: Int!
                offset: Int!
            }
        '''
        def schema = TestUtil.schema(sdl)
        given:
        def query = '''
            query Items( $limit: Int, $offset: Int) {
                 items(
                    pagination: {limit: $limit, offset: $offset}
                )
            }
        '''

        when:
        traverse(query, schema)

        then:
        errorCollector.containsValidationError(ValidationErrorType.VariableTypeMismatch)
        errorCollector.errors[0].message == "Validation error (VariableTypeMismatch@[items]) : Variable 'limit' of type 'Int' used in position expecting type 'Int!'"
    }

    def "issue 3276 - valid variables because of schema defaults with nullable variable"() {

        def sdl = '''
            type Query {
                items(pagination: Pagination! = {limit: 1, offset: 1}): [String]
            }
            input Pagination {
                limit: Int!
                offset: Int!
            }
        '''
        def schema = TestUtil.schema(sdl)
        given:
        def query = '''
            query Items( $var : Pagination) {
                 items(
                    pagination: $var
                )
            }
        '''

        when:
        traverse(query, schema)

        then:
        errorCollector.errors.isEmpty()
    }

    def "issue 3276 - valid variables because of variable defaults"() {

        def sdl = '''
            type Query {
                items(pagination: Pagination!): [String]
            }
            input Pagination {
                limit: Int!
                offset: Int!
            }
        '''
        def schema = TestUtil.schema(sdl)
        given:
        def query = '''
            query Items( $var : Pagination = {limit: 1, offset: 1}) {
                 items(
                    pagination: $var
                )
            }
        '''

        when:
        traverse(query, schema)

        then:
        errorCollector.errors.isEmpty()
    }
}

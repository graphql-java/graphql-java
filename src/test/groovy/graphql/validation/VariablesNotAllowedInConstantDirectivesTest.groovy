package graphql.validation

import graphql.TestUtil
import graphql.i18n.I18n
import graphql.language.Document
import graphql.parser.Parser
import spock.lang.Specification

class VariablesNotAllowedInConstantDirectivesTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def schema = TestUtil.schema('''
        directive @dir(arg: Int) on VARIABLE_DEFINITION
        directive @strDir(arg: String) on VARIABLE_DEFINITION | FIELD
        type Query {
            field(arg: Int): String
            x: Int
        }
    ''')

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(schema, document, i18n)
        OperationValidator operationValidator = new OperationValidator(validationContext, errorCollector,
                { r -> r == OperationValidationRule.VARIABLES_NOT_ALLOWED_IN_DIRECTIVES_ON_VARIABLE_DEFINITIONS })
        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, operationValidator)
    }

    def "variable reference in directive argument on variable definition is rejected"() {
        given:
        def query = '''
            query ($v: Int @dir(arg: $v)) { x }
        '''

        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.containsValidationError(ValidationErrorType.VariableNotAllowed)
        errorCollector.errors[0].message.contains("Variable 'v' is not allowed in directive arguments on variable definitions")
    }

    def "constant value in directive argument on variable definition is accepted"() {
        given:
        def query = '''
            query ($v: Int @dir(arg: 42)) { x }
        '''

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }

    def "variable reference in field directive argument is accepted"() {
        given:
        def query = '''
            query ($v: Int) { x @strDir(arg: "hello") }
        '''

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }

    def "no directive on variable definition is accepted"() {
        given:
        def query = '''
            query ($v: Int) { field(arg: $v) }
        '''

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }

    def "variable reference from another variable in directive is rejected"() {
        given:
        def query = '''
            query ($v: Int, $w: Int @dir(arg: $v)) { x }
        '''

        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.containsValidationError(ValidationErrorType.VariableNotAllowed)
        errorCollector.errors[0].message.contains("Variable 'v' is not allowed in directive arguments on variable definitions")
    }

    def "full validation rejects variable in directive on variable definition"() {
        given:
        def query = '''
            query ($v: Int @dir(arg: $v)) { x }
        '''
        def document = TestUtil.parseQuery(query)
        def validator = new Validator()

        when:
        def validationErrors = validator.validateDocument(schema, document, Locale.ENGLISH)

        then:
        validationErrors.any { it.validationErrorType == ValidationErrorType.VariableNotAllowed }
    }

    def "full validation accepts constant in directive on variable definition"() {
        given:
        def query = '''
            query ($v: Int @dir(arg: 42)) { x }
        '''
        def document = TestUtil.parseQuery(query)
        def validator = new Validator()

        when:
        def validationErrors = validator.validateDocument(schema, document, Locale.ENGLISH)

        then:
        !validationErrors.any { it.validationErrorType == ValidationErrorType.VariableNotAllowed }
    }
}

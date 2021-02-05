package graphql.execution.directives

import graphql.TestUtil
import graphql.language.Directive
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.StringValue
import graphql.schema.idl.MockedWiringFactory
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.validation.Validator
import spock.lang.Specification

class RepeatableDirectivesTest extends Specification {

    def sdl = '''
        directive @repeatableDirective(arg: String) repeatable on FIELD
         
        directive @nonRepeatableDirective on FIELD
        type Query {
           namedField: String
        }
    '''

    def schema = TestUtil.schema(sdl)


    def "repeatableDirectives"() {
        def spec = '''
            query {
                f1: namedField @repeatableDirective @repeatableDirective
                f2: namedField @repeatableDirective
                f3: namedField @nonRepeatableDirective
            }
        '''

        when:
        def document = TestUtil.parseQuery(spec)
        def validator = new Validator()
        def validationErrors = validator.validateDocument(schema, document)

        then:
        validationErrors.size() == 0
    }

    def "nonRepeatableDirective"() {

        def spec = '''
            query {
                namedField @nonRepeatableDirective @nonRepeatableDirective
            }
        '''

        when:
        def document = TestUtil.parseQuery(spec)
        def validator = new Validator()
        def validationErrors = validator.validateDocument(schema, document)

        then:
        validationErrors.size() == 1
        validationErrors[0].message == "Validation error of type DuplicateDirectiveName: Non repeatable directives must be uniquely named within a location. The directive 'nonRepeatableDirective' used on a 'Field' is not unique. @ 'namedField'"
    }

    def "getRepeatableDirectivesInfo"() {

        def spec = '''
            query {
                namedField @repeatableDirective(arg: "value1") @repeatableDirective(arg: "value2")
            }
        '''

        when:
        def document = TestUtil.parseQuery(spec)
        def validator = new Validator()
        def validationErrors = validator.validateDocument(schema, document)

        OperationDefinition operationDefinition = document.getDefinitions()[0]
        Field field = operationDefinition.getSelectionSet().getSelections()[0]
        List<Directive> directives = field.getDirectives()

        then:
        validationErrors.size() == 0
        directives.size() == 2
        ((StringValue) directives[0].getArgument("arg").getValue()).getValue() == "value1"
        ((StringValue) directives[1].getArgument("arg").getValue()).getValue() == "value2"
    }

    def " ensure repeatable directive on extend type run correctly "() {
        given:
        def spec = '''
            directive @key(dirArg:String!) repeatable on OBJECT
            type Query{
                field: String!  
                pTypedField(fieldArg: String!): PType 
            }
            
            type PType @key(dirArg:"a") @key(dirArg:"b") {
                name: String 
            }
            
            extend type PType @key(dirArg:"c") {
                extendField: String 
            }
        '''

        when:
        SchemaParser parser = new SchemaParser();
        def typeDefinitionRegistry = parser.parse(spec);
        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(new MockedWiringFactory())
                .build();
        def schemaGenerator = new SchemaGenerator();
        def schema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
        def pType = schema.getObjectType("PType")

        then:
        pType.getExtensionDefinitions().size() == 1
        def extensionType = pType.getExtensionDefinitions().get(0)
        extensionType.getDirectives().size() == 1
        extensionType.getDirectives("key") != null
        extensionType.getFieldDefinitions().size() == 1
        extensionType.getFieldDefinitions().get(0).getName() == "extendField"
    }

}
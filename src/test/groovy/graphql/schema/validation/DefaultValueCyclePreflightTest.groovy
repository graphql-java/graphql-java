package graphql.schema.validation

import graphql.language.NullValue
import graphql.language.ObjectValue
import graphql.schema.Coercing
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION
import static graphql.schema.GraphQLAppliedDirective.newDirective as newAppliedDirective
import static graphql.schema.GraphQLAppliedDirectiveArgument.newArgument as newAppliedArgument
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLDirective.newDirective
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar
import static graphql.schema.GraphQLSchema.newSchema
import static graphql.schema.GraphQLTypeReference.typeRef

class DefaultValueCyclePreflightTest extends Specification {

    def "nested #representation argument default reports its cycle before coercion - fast #fast"() {
        given:
        def recursive = recursiveType('ZRecursive')
        def childType = nonNull(typeRef('ZRecursive'))
        def wrapper = newInputObject().name('AWrapper')
                .field(newInputObjectField().name('child')
                        .type(representation == 'object' ? childType : list(childType))).build()
        def value = representation == 'object' ? [:] : [[:]]
        if (representation == 'array') {
            value = value as Object[]
        }
        def query = queryWithArgument(newArgument().name('arg').type(wrapper)
                .defaultValueProgrammatic([child: value]).build())

        when:
        buildSchema(query, [wrapper, recursive], fast)

        then:
        def exception = thrown(InvalidSchemaException)
        exception.errors*.classification == [SchemaValidationErrorType.DefaultValueCircularRef]
        exception.message.contains('ZRecursive.next references itself')

        where:
        [fast, representation] << [[false, true], ['object', 'list', 'array']].combinations()
    }

    def "input types used only by directives are checked - fast #fast"() {
        given:
        def recursive = recursiveType('DirectiveInput')
        def directive = newDirective().name('directive').validLocation(FIELD_DEFINITION)
                .argument(newArgument().name('arg').type(recursive)).build()
        def query = newObject().name('Query')
                .field(newFieldDefinition().name('test').type(GraphQLString)).build()

        when:
        buildSchema(query, fast ? [recursive] : [], fast, directive)

        then:
        def exception = thrown(InvalidSchemaException)
        exception.message.contains('DirectiveInput.next references itself')

        where:
        fast << [false, true]
    }

    def "all cycles are reported before any default or directive coercion - fast #fast"() {
        given:
        def coercing = Mock(Coercing)
        def scalar = newScalar().name('Tracked').coercing(coercing).build()
        def directive = newDirective().name('probe').validLocation(FIELD_DEFINITION)
                .argument(newArgument().name('value').type(scalar)).build()
        def applied = newAppliedDirective().name('probe')
                .argument(newAppliedArgument().name('value').type(scalar)
                        .valueProgrammatic('applied')).build()
        def query = newObject().name('Query')
                .field(newFieldDefinition().name('test').type(GraphQLString)
                        .argument(newArgument().name('arg').type(scalar).defaultValueProgrammatic('default'))
                        .argument(newArgument().name('invalid').type(GraphQLString).defaultValueProgrammatic(42))
                        .withAppliedDirective(applied)).build()
        def input = newInputObject().name('Input')
                .field(newInputObjectField().name('value').type(scalar).defaultValueProgrammatic('input')).build()

        when:
        buildSchema(query, [scalar, input, recursiveType('First'), recursiveType('Second')], fast, directive)

        then:
        def exception = thrown(InvalidSchemaException)
        exception.errors.size() == 2
        exception.errors.every { it.classification == SchemaValidationErrorType.DefaultValueCircularRef }
        exception.message.contains('First.next references itself')
        exception.message.contains('Second.next references itself')
        0 * coercing._

        where:
        fast << [false, true]
    }

    def "repeated validation does not retain cycle state between schemas or runs"() {
        given:
        def valid = uncheckedSchema(false)
        def invalid = uncheckedSchema(true)
        def validator = new SchemaValidator()

        when:
        def before = validator.validateSchema(valid)
        def first = validator.validateSchema(invalid)
        def second = validator.validateSchema(invalid)
        def after = validator.validateSchema(valid)

        then:
        before.empty
        first*.classification == [SchemaValidationErrorType.DefaultValueCircularRef]
        first*.description == second*.description
        after.empty
    }

    def "terminating defaults are accepted through nested arguments - fast #fast default #defaultValue"() {
        given:
        def recursive = newInputObject().name('Recursive')
                .field(newInputObjectField().name('next').type(list(typeRef('Recursive')))
                        .defaultValueProgrammatic(defaultValue)).build()
        def wrapper = newInputObject().name('Wrapper')
                .field(newInputObjectField().name('child').type(recursive)).build()
        def query = queryWithArgument(newArgument().name('arg').type(wrapper)
                .defaultValueProgrammatic([child: [:]]).build())

        expect:
        buildSchema(query, [wrapper, recursive], fast).getType('Recursive') != null

        where:
        [fast, defaultValue] << [[false, true], [null, []]].combinations()
    }

    private static GraphQLInputObjectType recursiveType(String name) {
        newInputObject().name(name)
                .field(newInputObjectField().name('next').type(typeRef(name))
                        .defaultValueProgrammatic([:])).build()
    }

    private static GraphQLObjectType queryWithArgument(GraphQLArgument argument) {
        newObject().name('Query')
                .field(newFieldDefinition().name('test').type(GraphQLString).argument(argument)).build()
    }

    private static GraphQLSchema uncheckedSchema(boolean cyclic) {
        def input = newInputObject().name('Input')
                .field(newInputObjectField().name('next').type(typeRef('Input'))
                        .defaultValueLiteral(cyclic ? ObjectValue.newObjectValue().build() : NullValue.of())).build()
        def query = queryWithArgument(newArgument().name('arg').type(input).build())
        new GraphQLSchema.FastBuilder(GraphQLCodeRegistry.newCodeRegistry(), query, null, null)
                .addType(input).build()
    }

    private static GraphQLSchema buildSchema(GraphQLObjectType query, List<GraphQLNamedType> types,
                                             boolean fast, GraphQLDirective directive = null) {
        def builder = fast
                ? new GraphQLSchema.FastBuilder(GraphQLCodeRegistry.newCodeRegistry(), query, null, null)
                        .addTypes(types).withValidation(true)
                : newSchema().query(query).additionalTypes(new LinkedHashSet<>(types))
        if (directive != null) {
            builder.additionalDirective(directive)
        }
        builder.build()
    }
}

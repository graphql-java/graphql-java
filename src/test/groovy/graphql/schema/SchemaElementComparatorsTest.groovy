package graphql.schema

import graphql.introspection.Introspection
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLUnionType.newUnionType
import static graphql.schema.SchemaElementComparatorEnvironment.newEnvironment

class SchemaElementComparatorsTest extends Specification {

    def "comparator environment identifies semantic schema kinds"() {
        given:
        def environment = newEnvironment(
                SchemaObject,
                SchemaField)
        def equalEnvironment = newEnvironment(
                SchemaObject,
                SchemaField)
        def differentEnvironment = newEnvironment(
                SchemaObject,
                SchemaArgument)

        expect:
        environment.parentType == SchemaObject
        environment.elementType == SchemaField
        environment.is(environment)
        environment == equalEnvironment
        environment.hashCode() == equalEnvironment.hashCode()
        environment != differentEnvironment
        environment != "not an environment"
        !environment.equals(null)
    }

    def "schema element comparators provide as-is and by-name ordering"() {
        given:
        SchemaElement first = newObject().name("a").build()
        SchemaElement second = newObject().name("b").build()
        def environment = newEnvironment(
                SchemaElement,
                SchemaObject)
        def directlySorted = [second, first]
        def registrySorted = [second, first]

        when:
        directlySorted.sort(
                SchemaElementComparators.byNameAsc())
        registrySorted.sort(
                SchemaElementComparatorRegistry.BY_NAME_REGISTRY
                        .getComparator(environment))

        then:
        SchemaElementComparators.asIsOrder()
                .compare(second, first) == 0
        directlySorted == [first, second]
        SchemaElementComparatorRegistry.AS_IS_REGISTRY
                .getComparator(environment)
                .compare(second, first) == 0
        registrySorted == [first, second]
    }

    def "default schema element registry matches grouped and name ordering"() {
        given:
        SchemaElement modified = nonNull(GraphQLString)
        SchemaElement directive = GraphQLDirective.newDirective()
                .name("Directive")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .build()
        SchemaElement interfaceType = newInterface()
                .name("Interface")
                .build()
        SchemaElement possibleType = newObject()
                .name("Possible")
                .build()
        SchemaElement unionType = newUnionType()
                .name("Union")
                .possibleType((GraphQLObjectType) possibleType)
                .build()
        SchemaElement objectType = newObject()
                .name("Object")
                .build()
        SchemaElement enumType = newEnum()
                .name("Enum")
                .value("VALUE")
                .build()
        SchemaElement inputType = newInputObject()
                .name("Input")
                .build()
        def elements = [
                inputType,
                GraphQLString,
                enumType,
                objectType,
                unionType,
                interfaceType,
                directive,
                modified
        ]
        def graphQLSorted = elements.toList()

        when:
        graphQLSorted.sort(
                DefaultGraphqlTypeComparatorRegistry.DEFAULT_COMPARATOR)
        elements.sort(SchemaElementComparatorRegistry.DEFAULT_REGISTRY
                .getComparator(newEnvironment(
                        SchemaElement,
                        null)))

        then:
        elements == graphQLSorted
        elements == [
                directive,
                interfaceType,
                unionType,
                objectType,
                enumType,
                GraphQLString,
                modified,
                inputType
        ]
    }
}

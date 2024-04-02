package graphql.schema

import graphql.AssertException
import graphql.TestUtil
import graphql.introspection.Introspection
import graphql.schema.idl.SchemaPrinter
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLFloat
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.TestUtil.mockArguments
import static graphql.TestUtil.mkDirective
import static graphql.schema.DefaultGraphqlTypeComparatorRegistry.newComparators
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.idl.SchemaPrinter.Options.defaultOptions

class GraphQLFieldDefinitionTest extends Specification {

    def "dataFetcher can't be null"() {
        when:
        newFieldDefinition().dataFetcher(null) // Retain for test coverage
        then:
        def exception = thrown(AssertException)
        exception.getMessage().contains("dataFetcher")
    }

    def "object can be transformed"() {
        given:
        def startingField = newFieldDefinition()
                .name("F1")
                .type(GraphQLFloat)
                .description("F1_description")
                .deprecate("F1_deprecated")
                .argument(newArgument().name("argStr").type(GraphQLString))
                .argument(newArgument().name("argInt").type(GraphQLInt))
                .withDirective(mkDirective("directive1", Introspection.DirectiveLocation.FIELD_DEFINITION))
                .withDirective(mkDirective("directive2", Introspection.DirectiveLocation.FIELD_DEFINITION))
                .build()

        when:
        def transformedField = startingField.transform({ builder ->
            builder.name("F2")
                    .type(GraphQLInt)
                    .deprecate(null)
                    .argument(newArgument().name("argStr").type(GraphQLString))
                    .argument(newArgument().name("argInt").type(GraphQLBoolean))
                    .argument(newArgument().name("argIntAdded").type(GraphQLInt))
                    .withDirective(mkDirective("directive3", Introspection.DirectiveLocation.FIELD_DEFINITION))
        })

        then:
        startingField.name == "F1"
        startingField.type == GraphQLFloat
        startingField.description == "F1_description"
        startingField.deprecated
        startingField.deprecationReason == "F1_deprecated"
        startingField.getArguments().size() == 2
        startingField.getArgument("argStr").type == GraphQLString
        startingField.getArgument("argInt").type == GraphQLInt

        startingField.getDirectives().size() == 2
        startingField.getDirective("directive1") != null
        startingField.getDirective("directive2") != null

        transformedField.name == "F2"
        transformedField.type == GraphQLInt
        transformedField.description == "F1_description" // left alone
        !transformedField.deprecated
        transformedField.deprecationReason == null
        transformedField.getArguments().size() == 3
        transformedField.getArgument("argStr").type == GraphQLString
        transformedField.getArgument("argInt").type == GraphQLBoolean
        transformedField.getArgument("argIntAdded").type == GraphQLInt

        transformedField.getDirectives().size() == 3
        transformedField.getDirective("directive1") != null
        transformedField.getDirective("directive2") != null
        transformedField.getDirective("directive3") != null
    }

    def "test deprecated argument builder for list"() {
        given:
        def field = newFieldDefinition().name("field").type(GraphQLInt).argument(mockArguments("a", "bb")).build() // Retain for test coverage

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLFieldDefinition.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, TestUtil.byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def printer = new SchemaPrinter(options)

        then:
        printer.argsString(GraphQLFieldDefinition.class, field.arguments) == '''(bb: Int, a: Int)'''
    }
}

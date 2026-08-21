package graphql.schema

import graphql.TestUtil
import graphql.schema.idl.AbstractSchemaPrintingTest
import graphql.schema.idl.SchemaPrinter

import java.util.stream.Collectors

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.TestUtil.*
import static graphql.schema.DefaultGraphqlTypeComparatorRegistry.DEFAULT_COMPARATOR
import static graphql.schema.DefaultGraphqlTypeComparatorRegistry.newComparators
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLEnumValueDefinition.newEnumValueDefinition
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar
import static graphql.schema.GraphQLUnionType.newUnionType
import static graphql.schema.GraphqlTypeComparatorEnvironment.newEnvironment
import static graphql.schema.idl.SchemaPrinter.Options.defaultOptions

class SchemaPrinterComparatorsTest extends AbstractSchemaPrintingTest {

    def "scalarPrinter default comparator"() {
        given:
        GraphQLScalarType scalarType = newScalar(mockScalar("TestScalar"))
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def options = defaultOptions().includeScalarTypes(true)
        def result = printType(new SchemaPrinter(options), scalarType)

        then:
        result == '''"TestScalar"
scalar TestScalar @a(a : 0, bb : 0) @bb(a : 0, bb : 0)
'''
    }

    def "enumPrinter default comparator"() {
        given:
        GraphQLEnumType enumType = newEnum().name("TestEnum")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .value(newEnumValueDefinition().name("a").value(0).withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .value(newEnumValueDefinition().name("bb").value(1).withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        when:
        def options = defaultOptions()
        def result = printType(new SchemaPrinter(options), enumType)

        then:
        result == '''enum TestEnum @a(a : 0, bb : 0) @bb(a : 0, bb : 0) {
  a @a(a : 0, bb : 0) @bb(a : 0, bb : 0)
  bb @a(a : 0, bb : 0) @bb(a : 0, bb : 0)
}
'''
    }

    def "unionPrinter default comparator"() {
        given:
        GraphQLUnionType unionType = newUnionType().name("TestUnion")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .possibleType(newObject().name("a")
                        .field(newFieldDefinition()
                                .name("value")
                                .type(GraphQLString))
                        .build())
                .possibleType(newObject().name("bb")
                        .field(newFieldDefinition()
                                .name("value")
                                .type(GraphQLString))
                        .build())
                .build()

        when:
        def options = defaultOptions()
        def result = printType(new SchemaPrinter(options), unionType)

        then:
        result == '''union TestUnion @a(a : 0, bb : 0) @bb(a : 0, bb : 0) = a | bb
'''
    }

    def "interfacePrinter default comparator"() {
        given:
        // @formatter:off
        GraphQLInterfaceType interfaceType = newInterface().name("TypeA")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def options = defaultOptions()
        def result = printType(new SchemaPrinter(options), interfaceType)

        then:
        result == '''interface TypeA @a(a : 0, bb : 0) @bb(a : 0, bb : 0) {
  a(a: Int, bb: Int): String @a(a : 0, bb : 0) @bb(a : 0, bb : 0)
  bb(a: Int, bb: Int): String @a(a : 0, bb : 0) @bb(a : 0, bb : 0)
}
'''
    }

    def "objectPrinter default comparator"() {
        given:
        // @formatter:off
        GraphQLObjectType objectType = newObject().name("TypeA")
                .withInterfaces(
                        newInterface().name("a")
                                .field(newFieldDefinition()
                                        .name("a")
                                        .type(GraphQLString))
                                .build(),
                        newInterface().name("bb")
                                .field(newFieldDefinition()
                                        .name("bb")
                                        .type(GraphQLString))
                                .build())
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def options = defaultOptions()
        def result = printType(new SchemaPrinter(options), objectType)

        then:
        result == '''type TypeA implements a & bb @a(a : 0, bb : 0) @bb(a : 0, bb : 0) {
  a(a: Int, bb: Int): String @a(a : 0, bb : 0) @bb(a : 0, bb : 0)
  bb(a: Int, bb: Int): String @a(a : 0, bb : 0) @bb(a : 0, bb : 0)
}
'''
    }

    def "inputObjectPrinter default comparator"() {
        given:
        // @formatter:off
        GraphQLInputObjectType inputObjectType = newInputObject().name("TypeA")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newInputObjectField().name("a")
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newInputObjectField().name("bb")
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def options = defaultOptions()
        def result = printType(
                new SchemaPrinter(options),
                inputObjectType)

        then:
        result == '''input TypeA @a(a : 0, bb : 0) @bb(a : 0, bb : 0) {
  a: String @a(a : 0, bb : 0) @bb(a : 0, bb : 0)
  bb: String @a(a : 0, bb : 0) @bb(a : 0, bb : 0)
}
'''
    }

    def "argsString default comparator"() {
        given:
        def args = mockArguments("a", "bb")

        when:
        def options = defaultOptions()
        def printer = new SchemaPrinter(options)

        then:
        printer.argsString(args) == '''(a: Int, bb: Int)'''
        printer.argsString(null, args) == '''(a: Int, bb: Int)'''
    }

    def "directivesString default comparator"() {
        given:
        def directives = mockDirectivesWithArguments("a", "bb").collect { it }

        when:
        def options = defaultOptions()
        def result = new SchemaPrinter(options).directivesString(null, directives)

        then:
        result == ''' @a(a : 0, bb : 0) @bb(a : 0, bb : 0)'''
    }

    def "scalarPrinter uses most specific registered comparators"() {
        given:
        GraphQLScalarType scalarType = newScalar(mockScalar("TestScalar"))
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLScalarType.class).elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLAppliedDirective.class).elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLAppliedDirectiveArgument.class, TestUtil.byGreatestLength)
                .build()
        def schemaRegistry = schemaComparatorsByEnvironment(
                schemaEnvironment(SchemaScalar, SchemaAppliedDirective),
                schemaEnvironment(
                        SchemaAppliedDirective,
                        SchemaAppliedDirectiveArgument))

        def options = defaultOptions()
                .includeScalarTypes(true)
                .setComparators(registry)
                .sortSchemaElements(schemaRegistry)
        def result = printType(new SchemaPrinter(options), scalarType)

        then:
        result == '''"TestScalar"
scalar TestScalar @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
'''
    }

    def "scalarPrinter uses least specific registered comparators"() {
        given:
        GraphQLScalarType scalarType = newScalar(mockScalar("TestScalar"))
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLAppliedDirectiveArgument.class, TestUtil.byGreatestLength)
                .build()
        def schemaRegistry = schemaComparatorsByElement(
                SchemaAppliedDirective,
                SchemaAppliedDirectiveArgument)

        def options = defaultOptions()
                .includeScalarTypes(true)
                .setComparators(registry)
                .sortSchemaElements(schemaRegistry)
        def result = printType(new SchemaPrinter(options), scalarType)

        then:
        result == '''"TestScalar"
scalar TestScalar @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
'''
    }

    def "enumPrinter uses most specific registered comparators"() {
        given:
        GraphQLEnumType enumType = newEnum().name("TestEnum")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .value(newEnumValueDefinition().name("a").value(0).withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .value(newEnumValueDefinition().name("bb").value(1).withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLEnumType.class).elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLEnumType.class).elementType(GraphQLEnumValueDefinition.class) }, GraphQLEnumValueDefinition.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLEnumValueDefinition.class).elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLAppliedDirective.class).elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLAppliedDirectiveArgument.class, TestUtil.byGreatestLength)
                .build()
        def schemaRegistry = schemaComparatorsByEnvironment(
                schemaEnvironment(SchemaEnum, SchemaAppliedDirective),
                schemaEnvironment(SchemaEnum, SchemaEnumValue),
                schemaEnvironment(
                        SchemaEnumValue,
                        SchemaAppliedDirective),
                schemaEnvironment(
                        SchemaAppliedDirective,
                        SchemaAppliedDirectiveArgument))

        def options = defaultOptions()
                .setComparators(registry)
                .sortSchemaElements(schemaRegistry)
        def result = printType(new SchemaPrinter(options), enumType)

        then:
        result == '''enum TestEnum @bb(bb : 0, a : 0) @a(bb : 0, a : 0) {
  bb @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
  a @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
}
'''
    }

    def "enumPrinter uses least specific registered comparators"() {
        given:
        GraphQLEnumType enumType = newEnum().name("TestEnum")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .value(newEnumValueDefinition().name("a").value(0).withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .value(newEnumValueDefinition().name("bb").value(1).withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLEnumValueDefinition.class) }, GraphQLEnumValueDefinition.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLAppliedDirectiveArgument.class, TestUtil.byGreatestLength)
                .build()
        def schemaRegistry = schemaComparatorsByElement(
                SchemaAppliedDirective,
                SchemaEnumValue,
                SchemaAppliedDirectiveArgument)

        def options = defaultOptions()
                .setComparators(registry)
                .sortSchemaElements(schemaRegistry)
        def result = printType(new SchemaPrinter(options), enumType)

        then:
        result == '''enum TestEnum @bb(bb : 0, a : 0) @a(bb : 0, a : 0) {
  bb @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
  a @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
}
'''
    }

    def "unionPrinter uses most specific registered comparators"() {
        given:
        GraphQLUnionType unionType = newUnionType().name("TestUnion")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .possibleType(objectWithField("a"))
                .possibleType(objectWithField("bb"))
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLUnionType.class).elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLUnionType.class).elementType(GraphQLOutputType.class) }, GraphQLOutputType.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLAppliedDirective.class).elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLAppliedDirectiveArgument.class, TestUtil.byGreatestLength)
                .build()
        def schemaRegistry = schemaComparatorsByEnvironment(
                schemaEnvironment(SchemaUnion, SchemaAppliedDirective),
                schemaEnvironment(SchemaUnion, SchemaOutputType),
                schemaEnvironment(
                        SchemaAppliedDirective,
                        SchemaAppliedDirectiveArgument))

        def options = defaultOptions()
                .setComparators(registry)
                .sortSchemaElements(schemaRegistry)
        def result = printType(new SchemaPrinter(options), unionType)

        then:
        result == '''union TestUnion @bb(bb : 0, a : 0) @a(bb : 0, a : 0) = bb | a
'''
    }

    def "unionPrinter uses least specific registered comparators"() {
        given:
        GraphQLUnionType unionType = newUnionType().name("TestUnion")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .possibleType(objectWithField("a"))
                .possibleType(objectWithField("bb"))
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLOutputType.class) }, GraphQLOutputType.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLAppliedDirectiveArgument.class, TestUtil.byGreatestLength)
                .build()
        def schemaRegistry = schemaComparatorsByElement(
                SchemaAppliedDirective,
                SchemaOutputType,
                SchemaAppliedDirectiveArgument)

        def options = defaultOptions()
                .setComparators(registry)
                .sortSchemaElements(schemaRegistry)
        def result = printType(new SchemaPrinter(options), unionType)

        then:
        result == '''union TestUnion @bb(bb : 0, a : 0) @a(bb : 0, a : 0) = bb | a
'''
    }

    def "interfacePrinter uses most specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLInterfaceType interfaceType = newInterface().name("TypeA")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLInterfaceType.class).elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLInterfaceType.class).elementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLFieldDefinition.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLFieldDefinition.class).elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLAppliedDirective.class).elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLAppliedDirectiveArgument.class, TestUtil.byGreatestLength)
                .build()
        def schemaRegistry = schemaComparatorsByEnvironment(
                schemaEnvironment(SchemaInterface, SchemaAppliedDirective),
                schemaEnvironment(SchemaInterface, SchemaField),
                schemaEnvironment(SchemaField, SchemaArgument),
                schemaEnvironment(SchemaField, SchemaAppliedDirective),
                schemaEnvironment(
                        SchemaAppliedDirective,
                        SchemaAppliedDirectiveArgument))

        def options = defaultOptions()
                .setComparators(registry)
                .sortSchemaElements(schemaRegistry)
        def result = printType(new SchemaPrinter(options), interfaceType)

        then:
        result == '''interface TypeA @bb(bb : 0, a : 0) @a(bb : 0, a : 0) {
  bb(bb: Int, a: Int): String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
  a(bb: Int, a: Int): String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
}
'''
    }

    def "interfacePrinter uses least specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLInterfaceType interfaceType = newInterface().name("TypeA")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLArgument.class) }, GraphQLArgument.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLAppliedDirectiveArgument.class, TestUtil.byGreatestLength)
                .build()
        def schemaRegistry = schemaComparatorsByElement(
                SchemaAppliedDirective,
                SchemaField,
                SchemaArgument,
                SchemaAppliedDirectiveArgument)

        def options = defaultOptions()
                .setComparators(registry)
                .sortSchemaElements(schemaRegistry)
        def result = printType(new SchemaPrinter(options), interfaceType)

        then:
        result == '''interface TypeA @bb(bb : 0, a : 0) @a(bb : 0, a : 0) {
  bb(bb: Int, a: Int): String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
  a(bb: Int, a: Int): String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
}
'''
    }

    def "objectPrinter uses most specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLObjectType objectType = newObject().name("TypeA")
                .withInterfaces(
                        interfaceWithField("a"),
                        interfaceWithField("bb"))
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString).withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLObjectType.class).elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLObjectType.class).elementType(GraphQLOutputType.class) }, GraphQLOutputType.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLObjectType.class).elementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLFieldDefinition.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLFieldDefinition.class).elementType(GraphQLAppliedDirective.class) }, GraphQLDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLAppliedDirective.class).elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLArgument.class, TestUtil.byGreatestLength)
                .build()
        def schemaRegistry = schemaComparatorsByEnvironment(
                schemaEnvironment(SchemaObject, SchemaAppliedDirective),
                schemaEnvironment(SchemaObject, SchemaOutputType),
                schemaEnvironment(SchemaObject, SchemaField),
                schemaEnvironment(SchemaField, SchemaArgument),
                schemaEnvironment(SchemaField, SchemaAppliedDirective),
                schemaEnvironment(
                        SchemaAppliedDirective,
                        SchemaAppliedDirectiveArgument))
        def options = defaultOptions()
                .setComparators(registry)
                .sortSchemaElements(schemaRegistry)
        def result = printType(new SchemaPrinter(options), objectType)

        then:
        result == '''type TypeA implements bb & a @bb(bb : 0, a : 0) @a(bb : 0, a : 0) {
  bb(bb: Int, a: Int): String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
  a(bb: Int, a: Int): String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
}
'''
    }

    def "objectPrinter uses least specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLObjectType objectType = newObject().name("TypeA")
                .withInterfaces(
                        interfaceWithField("a"),
                        interfaceWithField("bb"))
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString).withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLOutputType.class) }, GraphQLOutputType.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLArgument.class) }, GraphQLArgument.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLAppliedDirectiveArgument.class, TestUtil.byGreatestLength)
                .build()
        def schemaRegistry = schemaComparatorsByElement(
                SchemaAppliedDirective,
                SchemaOutputType,
                SchemaField,
                SchemaArgument,
                SchemaAppliedDirectiveArgument)
        def options = defaultOptions()
                .setComparators(registry)
                .sortSchemaElements(schemaRegistry)
        def result = printType(new SchemaPrinter(options), objectType)

        then:
        result == '''type TypeA implements bb & a @bb(bb : 0, a : 0) @a(bb : 0, a : 0) {
  bb(bb: Int, a: Int): String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
  a(bb: Int, a: Int): String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
}
'''
    }

    def "inputObjectPrinter uses most specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLInputObjectType inputObjectType = newInputObject().name("TypeA")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newInputObjectField().name("a")
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newInputObjectField().name("bb")
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLInputObjectType.class).elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLInputObjectType.class).elementType(GraphQLInputObjectField.class) }, GraphQLInputObjectField.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLInputObjectField.class).elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLAppliedDirective.class).elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLAppliedDirectiveArgument.class, TestUtil.byGreatestLength)
                .build()
        def schemaRegistry = schemaComparatorsByEnvironment(
                schemaEnvironment(
                        SchemaInputObject,
                        SchemaAppliedDirective),
                schemaEnvironment(SchemaInputObject, SchemaInputField),
                schemaEnvironment(
                        SchemaInputField,
                        SchemaAppliedDirective),
                schemaEnvironment(
                        SchemaAppliedDirective,
                        SchemaAppliedDirectiveArgument))
        def options = defaultOptions()
                .setComparators(registry)
                .sortSchemaElements(schemaRegistry)
        def result = printType(new SchemaPrinter(options), inputObjectType)

        then:
        result == '''input TypeA @bb(bb : 0, a : 0) @a(bb : 0, a : 0) {
  bb: String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
  a: String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
}
'''
    }

    def "inputObjectPrinter uses least specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLInputObjectType inputObjectType = newInputObject().name("TypeA")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newInputObjectField().name("a")
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newInputObjectField().name("bb")
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLInputObjectField.class) }, GraphQLInputObjectField.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLAppliedDirectiveArgument.class, TestUtil.byGreatestLength)
                .build()
        def schemaRegistry = schemaComparatorsByElement(
                SchemaAppliedDirective,
                SchemaInputField,
                SchemaAppliedDirectiveArgument)
        def options = defaultOptions()
                .setComparators(registry)
                .sortSchemaElements(schemaRegistry)
        def result = printType(new SchemaPrinter(options), inputObjectType)

        then:
        result == '''input TypeA @bb(bb : 0, a : 0) @a(bb : 0, a : 0) {
  bb: String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
  a: String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
}
'''
    }

    def "argsString uses most specific registered comparators"() {
        given:
        def field = newFieldDefinition().name("field").type(GraphQLInt).arguments(mockArguments("a", "bb")).build()

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLFieldDefinition.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, TestUtil.byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def printer = new SchemaPrinter(options)

        then:
        printer.argsString(GraphQLFieldDefinition.class, field.arguments) == '''(bb: Int, a: Int)'''
    }

    def "argsString uses least specific registered comparators"() {
        given:
        def field = newFieldDefinition().name("field").type(GraphQLInt).arguments(mockArguments("a", "bb")).build()

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLArgument.class) }, GraphQLArgument.class, TestUtil.byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def printer = new SchemaPrinter(options)

        then:
        printer.argsString(GraphQLFieldDefinition.class, field.arguments) == '''(bb: Int, a: Int)'''
        printer.argsString(null, field.arguments) == '''(bb: Int, a: Int)'''
    }

    def "executable schema printing derives GraphQL comparator types internally"() {
        given:
        def firstField = newFieldDefinition()
                .name("a")
                .type(GraphQLString)
                .arguments(mockArguments("a", "bb"))
                .withAppliedDirectives(
                        mockDirectivesWithArguments("a", "bb"))
                .build()
        def secondField = newFieldDefinition()
                .name("bb")
                .type(GraphQLString)
                .build()
        def query = newObject()
                .name("Query")
                .fields([firstField, secondField])
                .withAppliedDirectives(
                        mockDirectivesWithArguments("a", "bb"))
                .build()
        def schema = GraphQLSchema.newSchema().query(query).build()
        def environments = []
        def schemaEnvironments = []
        GraphqlTypeComparatorRegistry registry = { environment ->
            environments.add([
                    environment.parentType,
                    environment.elementType])
            DEFAULT_COMPARATOR
        }
        SchemaElementComparatorRegistry schemaRegistry = { environment ->
            schemaEnvironments.add([
                    environment.parentType,
                    environment.elementType])
            SchemaElementComparators.sensibleGroupedOrder()
        }
        def options = defaultOptions()
                .setComparators(registry)
                .sortSchemaElements(schemaRegistry)

        when:
        printSchema(new SchemaPrinter(options), schema)

        then:
        environments.count {
            it == [
                GraphQLSchemaElement.class,
                null]
        } == 1
        environments.contains([
                GraphQLObjectType.class,
                GraphQLFieldDefinition.class])
        environments.contains([
                GraphQLFieldDefinition.class,
                GraphQLArgument.class])
        environments.contains([
                GraphQLObjectType.class,
                GraphQLAppliedDirective.class])
        environments.contains([
                GraphQLFieldDefinition.class,
                GraphQLAppliedDirective.class])
        environments.contains([
                GraphQLAppliedDirective.class,
                GraphQLAppliedDirectiveArgument.class])
        schemaEnvironments.count {
            it == [
                SchemaElement.class,
                null]
        } == 1
        schemaEnvironments.contains([
                SchemaObject.class,
                SchemaField.class])
        schemaEnvironments.contains([
                SchemaField.class,
                SchemaArgument.class])
        schemaEnvironments.contains([
                SchemaObject.class,
                SchemaAppliedDirective.class])
        schemaEnvironments.contains([
                SchemaField.class,
                SchemaAppliedDirective.class])
        schemaEnvironments.contains([
                SchemaAppliedDirective.class,
                SchemaAppliedDirectiveArgument.class])
    }


    def "directivesString uses most specific registered comparators"() {
        given:
        def field = newFieldDefinition().name("field")
                .type(GraphQLString)
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLFieldDefinition.class).elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLAppliedDirective.class).elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLAppliedDirectiveArgument.class, TestUtil.byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).directivesString(GraphQLFieldDefinition.class, field)

        then:
        result == ''' @bb(bb : 0, a : 0) @a(bb : 0, a : 0)'''
    }

    def     "directivesString uses least specific registered comparators"() {
        given:
        def field = newFieldDefinition().name("field")
                .type(GraphQLString)
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.parentType(GraphQLAppliedDirective.class).elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLAppliedDirectiveArgument.class, TestUtil.byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).directivesString(GraphQLFieldDefinition.class, field)

        then:
        result == ''' @bb(bb : 0, a : 0) @a(bb : 0, a : 0)'''
    }


    def "least specific comparator applied across different types"() {
        given:
        // @formatter:off
        GraphQLScalarType scalarType = newScalar(mockScalar("TestScalar"))
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        GraphQLUnionType unionType = newUnionType().name("TestUnion")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .possibleType(objectWithField("a"))
                .possibleType(objectWithField("bb"))
                .build()

        GraphQLEnumType enumType = newEnum().name("TestEnum")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .value(newEnumValueDefinition().name("a").value(0).withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .value(newEnumValueDefinition().name("bb").value(0).withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        GraphQLObjectType objectType = newObject().name("TestObjectType")
                .withInterfaces(
                        interfaceWithField("a"),
                        interfaceWithField("bb"))
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString).withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        GraphQLInterfaceType interfaceType = newInterface().name("TestInterfaceType")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .arguments(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        GraphQLInputObjectType inputObjectType = newInputObject().name("TestInputObjectType")
                .withAppliedDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newInputObjectField().name("a")
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newInputObjectField().name("bb")
                    .type(GraphQLString)
                    .withAppliedDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLInputObjectField.class) }, GraphQLInputObjectField.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLEnumValueDefinition.class) }, GraphQLEnumValueDefinition.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLOutputType.class) }, GraphQLOutputType.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLAppliedDirective.class) }, GraphQLAppliedDirective.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLAppliedDirectiveArgument.class) }, GraphQLAppliedDirectiveArgument.class, TestUtil.byGreatestLength)
                .addComparator({ it.elementType(GraphQLArgument.class) }, GraphQLArgument.class, TestUtil.byGreatestLength)
                .build()
        def schemaRegistry = schemaComparatorsByElement(
                SchemaField,
                SchemaInputField,
                SchemaEnumValue,
                SchemaOutputType,
                SchemaAppliedDirective,
                SchemaAppliedDirectiveArgument,
                SchemaArgument)
        def options = defaultOptions()
                .includeScalarTypes(true)
                .setComparators(registry)
                .sortSchemaElements(schemaRegistry)
        def printer = new SchemaPrinter(options)

        def scalarResult = printType(printer, scalarType)
        def enumResult = printType(printer, enumType)
        def unionResult = printType(printer, unionType)
        def objectTypeResult = printType(printer, objectType)
        def interfaceTypeResult = printType(printer, interfaceType)
        def inputObjectTypeResult = printType(printer, inputObjectType)

        then:

        scalarResult == '''"TestScalar"
scalar TestScalar @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
'''

        enumResult == '''enum TestEnum @bb(bb : 0, a : 0) @a(bb : 0, a : 0) {
  bb @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
  a @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
}
'''

        unionResult == '''union TestUnion @bb(bb : 0, a : 0) @a(bb : 0, a : 0) = bb | a
'''

        interfaceTypeResult == '''interface TestInterfaceType @bb(bb : 0, a : 0) @a(bb : 0, a : 0) {
  bb(bb: Int, a: Int): String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
  a(bb: Int, a: Int): String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
}
'''

        objectTypeResult == '''type TestObjectType implements bb & a @bb(bb : 0, a : 0) @a(bb : 0, a : 0) {
  bb(bb: Int, a: Int): String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
  a(bb: Int, a: Int): String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
}
'''

        inputObjectTypeResult == '''input TestInputObjectType @bb(bb : 0, a : 0) @a(bb : 0, a : 0) {
  bb: String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
  a: String @bb(bb : 0, a : 0) @a(bb : 0, a : 0)
}
'''
    }

    def "DefaultSchemaPrinterComparatorRegistry finds expected comparator"() {
        given:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, TestUtil.byGreatestLength)
                .build()

        when:
        def result = registry.getComparator(newEnvironment().elementType(GraphQLFieldDefinition.class).build())

        then:
        result == byGreatestLength
    }

    def "DefaultSchemaPrinterComparatorRegistry provides default comparator when environment is not found"() {
        given:
        def registry = newComparators().build()

        when:
        def result = registry.getComparator(newEnvironment().elementType(GraphQLFieldDefinition.class).build())

        then:
        result == DEFAULT_COMPARATOR
    }

    def "directive string when argument has no value"() {
        given:
        GraphQLScalarType scalarType = newScalar(mockScalar("TestScalar"))
                .withAppliedDirectives(mockDirectivesWithNoValueArguments("a", "bb"))
                .build()

        when:
        def options = defaultOptions().includeScalarTypes(true)
        def result = printType(new SchemaPrinter(options), scalarType)

        then:
        result == '''"TestScalar"
scalar TestScalar @a @bb
'''
    }

    def " sort GraphQLSchemaElement by name or toString()"() {
        given:
        def coercing = new Coercing() {
            @Override
            Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                return null
            }

            @Override
            Object parseValue(Object input) throws CoercingParseValueException {
                return null
            }

            @Override
            Object parseLiteral(Object input) throws CoercingParseLiteralException {
                return null
            }
        }

        def a = newScalar()
                .name("a")
                .coercing(coercing)
                .build()
        def b = newScalar()
                .name("b")
                .coercing(coercing)
                .build()

        def nonNullA = GraphQLNonNull.nonNull(a)
        def nonNullB = GraphQLNonNull.nonNull(b)
        def list = [nonNullB, nonNullA]

        when:
        def sortedList = list.stream().sorted(
                DEFAULT_COMPARATOR).collect(Collectors.toList()
        )

        then:
        sortedList == [nonNullA, nonNullB]
    }

    private static SchemaElementComparatorEnvironment schemaEnvironment(
            Class<? extends SchemaElement> parentType,
            Class<? extends SchemaElement> elementType) {
        return SchemaElementComparatorEnvironment.newEnvironment(
                parentType,
                elementType)
    }

    private static SchemaElementComparatorRegistry
            schemaComparatorsByEnvironment(
                    SchemaElementComparatorEnvironment...
                            matchingEnvironments) {
        def matches = matchingEnvironments.toSet()
        return { environment ->
            if (matches.contains(environment)) {
                return schemaByGreatestLength()
            }
            return SchemaElementComparatorRegistry.DEFAULT_REGISTRY
                    .getComparator(environment)
        }
    }

    private static SchemaElementComparatorRegistry schemaComparatorsByElement(
            Class<? extends SchemaElement>... matchingElementTypes) {
        def matches = matchingElementTypes.toSet()
        return { environment ->
            if (matches.contains(environment.elementType)) {
                return schemaByGreatestLength()
            }
            return SchemaElementComparatorRegistry.DEFAULT_REGISTRY
                    .getComparator(environment)
        }
    }

    private static Comparator<SchemaElement> schemaByGreatestLength() {
        return { first, second ->
            ((SchemaNamedElement) second).name.length() <=>
                    ((SchemaNamedElement) first).name.length()
        } as Comparator<SchemaElement>
    }

    private static GraphQLObjectType objectWithField(String name) {
        return newObject()
                .name(name)
                .field(newFieldDefinition()
                        .name(name)
                        .type(GraphQLString))
                .build()
    }

    private static GraphQLInterfaceType interfaceWithField(String name) {
        return newInterface()
                .name(name)
                .field(newFieldDefinition()
                        .name(name)
                        .type(GraphQLString))
                .build()
    }
}

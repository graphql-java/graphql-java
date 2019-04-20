package graphql.schema.idl

import graphql.schema.*
import spock.lang.Specification

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.TestUtil.*
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLEnumValueDefinition.newEnumValueDefinition
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar
import static graphql.schema.GraphQLUnionType.newUnionType
import static graphql.schema.idl.DefaultSchemaPrinterComparatorRegistry.DEFAULT_COMPARATOR
import static graphql.schema.idl.DefaultSchemaPrinterComparatorRegistry.newComparators
import static graphql.schema.idl.SchemaPrinter.Options.defaultOptions
import static graphql.schema.idl.SchemaPrinterComparatorEnvironment.newEnvironment

class SchemaPrinterComparatorsTest extends Specification {

    def "scalarPrinter default comparator"() {
        given:
        GraphQLScalarType scalarType = newScalar(mockScalar("TestScalar"))
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def options = defaultOptions().includeScalarTypes(true).includeExtendedScalarTypes(true)
        def result = new SchemaPrinter(options).print(scalarType)

        then:
        result == '''#TestScalar
scalar TestScalar @a(a, bb) @bb(a, bb)

'''
    }

    def "enumPrinter default comparator"() {
        given:
        GraphQLEnumType enumType = newEnum().name("TestEnum")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .value(newEnumValueDefinition().name("a").value(0).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .value(newEnumValueDefinition().name("bb").value(1).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        when:
        def options = defaultOptions()
        def result = new SchemaPrinter(options).print(enumType)

        then:
        result == '''enum TestEnum @a(a, bb) @bb(a, bb) {
  a @a(a, bb) @bb(a, bb)
  bb @a(a, bb) @bb(a, bb)
}

'''
    }

    def "unionPrinter default comparator"() {
        given:
        GraphQLUnionType unionType = newUnionType().name("TestUnion")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .possibleType(newObject().name("a").build())
                .possibleType(newObject().name("bb").build())
                .build()

        when:
        def options = defaultOptions()
        def result = new SchemaPrinter(options).print(unionType)

        then:
        result == '''union TestUnion @a(a, bb) @bb(a, bb) = a | bb

'''
    }

    def "interfacePrinter default comparator"() {
        given:
        // @formatter:off
        GraphQLInterfaceType interfaceType = newInterface().name("TypeA")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def options = defaultOptions()
        def result = new SchemaPrinter(options).print(interfaceType)

        then:
        result == '''interface TypeA @a(a, bb) @bb(a, bb) {
  a(a: Int, bb: Int): String @a(a, bb) @bb(a, bb)
  bb(a: Int, bb: Int): String @a(a, bb) @bb(a, bb)
}

'''
    }

    def "objectPrinter default comparator"() {
        given:
        // @formatter:off
        GraphQLObjectType objectType = newObject().name("TypeA")
                .withInterfaces(newInterface().name("a").build(), newInterface().name("bb").build())
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def options = defaultOptions()
        def result = new SchemaPrinter(options).print(objectType)

        then:
        result == '''type TypeA implements a & bb @a(a, bb) @bb(a, bb) {
  a(a: Int, bb: Int): String @a(a, bb) @bb(a, bb)
  bb(a: Int, bb: Int): String @a(a, bb) @bb(a, bb)
}

'''
    }

    def "inputObjectPrinter default comparator"() {
        given:
        // @formatter:off
        GraphQLInputObjectType inputObjectType = newInputObject().name("TypeA")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newInputObjectField().name("a")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newInputObjectField().name("bb")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def options = defaultOptions()
        def result = new SchemaPrinter(options).print(inputObjectType)

        then:
        result == '''input TypeA @a(a, bb) @bb(a, bb) {
  a: String @a(a, bb) @bb(a, bb)
  bb: String @a(a, bb) @bb(a, bb)
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
        result == ''' @a(a, bb) @bb(a, bb)'''
    }

    def "scalarPrinter uses most specific registered comparators"() {
        given:
        GraphQLScalarType scalarType = newScalar(mockScalar("TestScalar"))
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLScalarType.class).elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLDirective.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().includeScalarTypes(true).includeExtendedScalarTypes(true).setComparators(registry)
        def result = new SchemaPrinter(options).print(scalarType)

        then:
        result == '''#TestScalar
scalar TestScalar @bb(bb, a) @a(bb, a)

'''
    }

    def "scalarPrinter uses least specific registered comparators"() {
        given:
        GraphQLScalarType scalarType = newScalar(mockScalar("TestScalar"))
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().includeScalarTypes(true).includeExtendedScalarTypes(true).setComparators(registry)
        def result = new SchemaPrinter(options).print(scalarType)

        then:
        result == '''#TestScalar
scalar TestScalar @bb(bb, a) @a(bb, a)

'''
    }

    def "enumPrinter uses most specific registered comparators"() {
        given:
        GraphQLEnumType enumType = newEnum().name("TestEnum")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .value(newEnumValueDefinition().name("a").value(0).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .value(newEnumValueDefinition().name("bb").value(1).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLEnumType.class).elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLEnumType.class).elementType(GraphQLEnumValueDefinition.class) }, GraphQLEnumValueDefinition.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLEnumValueDefinition.class).elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLDirective.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(enumType)

        then:
        result == '''enum TestEnum @bb(bb, a) @a(bb, a) {
  bb @bb(bb, a) @a(bb, a)
  a @bb(bb, a) @a(bb, a)
}

'''
    }

    def "enumPrinter uses least specific registered comparators"() {
        given:
        GraphQLEnumType enumType = newEnum().name("TestEnum")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .value(newEnumValueDefinition().name("a").value(0).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .value(newEnumValueDefinition().name("bb").value(1).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLEnumValueDefinition.class) }, GraphQLEnumValueDefinition.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(enumType)

        then:
        result == '''enum TestEnum @bb(bb, a) @a(bb, a) {
  bb @bb(bb, a) @a(bb, a)
  a @bb(bb, a) @a(bb, a)
}

'''
    }

    def "unionPrinter uses most specific registered comparators"() {
        given:
        GraphQLUnionType unionType = newUnionType().name("TestUnion")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .possibleType(newObject().name("a").build())
                .possibleType(newObject().name("bb").build())
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLUnionType.class).elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLUnionType.class).elementType(GraphQLOutputType.class) }, GraphQLOutputType.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLDirective.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(unionType)

        then:
        result == '''union TestUnion @bb(bb, a) @a(bb, a) = bb | a

'''
    }

    def "unionPrinter uses least specific registered comparators"() {
        given:
        GraphQLUnionType unionType = newUnionType().name("TestUnion")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .possibleType(newObject().name("a").build())
                .possibleType(newObject().name("bb").build())
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLOutputType.class) }, GraphQLOutputType.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(unionType)

        then:
        result == '''union TestUnion @bb(bb, a) @a(bb, a) = bb | a

'''
    }

    def "interfacePrinter uses most specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLInterfaceType interfaceType = newInterface().name("TypeA")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLInterfaceType.class).elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLInterfaceType.class).elementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLFieldDefinition.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLFieldDefinition.class).elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLDirective.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(interfaceType)

        then:
        result == '''interface TypeA @bb(bb, a) @a(bb, a) {
  bb(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
  a(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
}

'''
    }

    def "interfacePrinter uses least specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLInterfaceType interfaceType = newInterface().name("TypeA")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(interfaceType)

        then:
        result == '''interface TypeA @bb(bb, a) @a(bb, a) {
  bb(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
  a(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
}

'''
    }

    def "objectPrinter uses most specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLObjectType objectType = newObject().name("TypeA")
                .withInterfaces(newInterface().name("a") .build(), newInterface().name("bb").build())
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLObjectType.class).elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLObjectType.class).elementType(GraphQLOutputType.class) }, GraphQLOutputType.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLObjectType.class).elementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLFieldDefinition.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLFieldDefinition.class).elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLDirective.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(objectType)

        then:
        result == '''type TypeA implements bb & a @bb(bb, a) @a(bb, a) {
  bb(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
  a(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
}

'''
    }

    def "objectPrinter uses least specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLObjectType objectType = newObject().name("TypeA")
                .withInterfaces(newInterface().name("a") .build(), newInterface().name("bb").build())
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLOutputType.class) }, GraphQLOutputType.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(objectType)

        then:
        result == '''type TypeA implements bb & a @bb(bb, a) @a(bb, a) {
  bb(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
  a(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
}

'''
    }

    def "inputObjectPrinter uses most specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLInputObjectType inputObjectType = newInputObject().name("TypeA")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newInputObjectField().name("a")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newInputObjectField().name("bb")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLInputObjectType.class).elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLInputObjectType.class).elementType(GraphQLInputObjectField.class) }, GraphQLInputObjectField.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLInputObjectField.class).elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLDirective.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(inputObjectType)

        then:
        result == '''input TypeA @bb(bb, a) @a(bb, a) {
  bb: String @bb(bb, a) @a(bb, a)
  a: String @bb(bb, a) @a(bb, a)
}

'''
    }

    def "inputObjectPrinter uses least specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLInputObjectType inputObjectType = newInputObject().name("TypeA")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newInputObjectField().name("a")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newInputObjectField().name("bb")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLInputObjectField.class) }, GraphQLInputObjectField.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(inputObjectType)

        then:
        result == '''input TypeA @bb(bb, a) @a(bb, a) {
  bb: String @bb(bb, a) @a(bb, a)
  a: String @bb(bb, a) @a(bb, a)
}

'''
    }

    def "argsString uses most specific registered comparators"() {
        given:
        def field = newFieldDefinition().name("field").type(GraphQLInt).argument(mockArguments("a", "bb")).build()

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLFieldDefinition.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def printer = new SchemaPrinter(options)

        then:
        printer.argsString(GraphQLFieldDefinition.class, field.arguments) == '''(bb: Int, a: Int)'''
    }

    def "argsString uses least specific registered comparators"() {
        given:
        def field = newFieldDefinition().name("field").type(GraphQLInt).argument(mockArguments("a", "bb")).build()

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def printer = new SchemaPrinter(options)

        then:
        printer.argsString(GraphQLFieldDefinition.class, field.arguments) == '''(bb: Int, a: Int)'''
        printer.argsString(null, field.arguments) == '''(bb: Int, a: Int)'''
    }


    def "directivesString uses most specific registered comparators"() {
        given:
        def field = newFieldDefinition().name("field")
                .type(GraphQLString)
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.parentType(GraphQLFieldDefinition.class).elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLDirective.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).directivesString(GraphQLFieldDefinition.class, field.directives)

        then:
        result == ''' @bb(bb, a) @a(bb, a)'''
    }

    def "directivesString uses least specific registered comparators"() {
        given:
        def field = newFieldDefinition().name("field")
                .type(GraphQLString)
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.parentType(GraphQLDirective.class).elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).directivesString(GraphQLFieldDefinition.class, field.directives)

        then:
        result == ''' @bb(bb, a) @a(bb, a)'''
    }


    def "least specific comparator applied across different types"() {
        given:
        // @formatter:off
        GraphQLScalarType scalarType = newScalar(mockScalar("TestScalar"))
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        GraphQLUnionType unionType = newUnionType().name("TestUnion")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .possibleType(newObject().name("a").build())
                .possibleType(newObject().name("bb").build())
                .build()

        GraphQLEnumType enumType = newEnum().name("TestEnum")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .value(newEnumValueDefinition().name("a").value(0).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .value(newEnumValueDefinition().name("bb").value(0).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        GraphQLObjectType objectType = newObject().name("TestObjectType")
                .withInterfaces(newInterface().name("a") .build(), newInterface().name("bb").build())
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        GraphQLInterfaceType interfaceType = newInterface().name("TestInterfaceType")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        GraphQLInputObjectType inputObjectType = newInputObject().name("TestInputObjectType")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newInputObjectField().name("a")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newInputObjectField().name("bb")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLInputObjectField.class) }, GraphQLInputObjectField.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLEnumValueDefinition.class) }, GraphQLEnumValueDefinition.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLOutputType.class) }, GraphQLOutputType.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .addComparator({ it.elementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().includeScalarTypes(true).includeExtendedScalarTypes(true).setComparators(registry)
        def printer = new SchemaPrinter(options)

        def scalarResult = printer.print(scalarType)
        def enumResult = printer.print(enumType)
        def unionResult = printer.print(unionType)
        def objectTypeResult = printer.print(objectType)
        def interfaceTypeResult = printer.print(interfaceType)
        def inputObjectTypeResult = printer.print(inputObjectType)

        then:

        scalarResult == '''#TestScalar
scalar TestScalar @bb(bb, a) @a(bb, a)

'''

        enumResult == '''enum TestEnum @bb(bb, a) @a(bb, a) {
  bb @bb(bb, a) @a(bb, a)
  a @bb(bb, a) @a(bb, a)
}

'''

        unionResult == '''union TestUnion @bb(bb, a) @a(bb, a) = bb | a

'''

        interfaceTypeResult == '''interface TestInterfaceType @bb(bb, a) @a(bb, a) {
  bb(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
  a(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
}

'''

        objectTypeResult == '''type TestObjectType implements bb & a @bb(bb, a) @a(bb, a) {
  bb(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
  a(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
}

'''

        inputObjectTypeResult == '''input TestInputObjectType @bb(bb, a) @a(bb, a) {
  bb: String @bb(bb, a) @a(bb, a)
  a: String @bb(bb, a) @a(bb, a)
}

'''
    }

    def "DefaultSchemaPrinterComparatorRegistry finds expected comparator"() {
        given:
        def registry = newComparators()
                .addComparator({ it.elementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, byGreatestLength)
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
}
package graphql.schema.idl

import graphql.Scalars
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLImplementingType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLType
import graphql.schema.GraphQLUnionType
import graphql.schema.SchemaDirective
import graphql.schema.SchemaElement
import graphql.schema.SchemaType
import graphql.schema.TypeResolver
import graphql.schema.universe.SchemaUniverse
import graphql.schema.universe.view.SUExecutableSchema
import spock.lang.Specification

abstract class AbstractSchemaPrintingTest extends Specification {

    String printSchema(SchemaPrinter printer, GraphQLSchema schema) {
        def expected = printer.print(schema)
        def imported = new SchemaUniverse()
                .importSchema("schema_printer_test", schema)
        def executable = SUExecutableSchema.fromGraphQLSchema(imported, schema)

        assert printer.print(executable) == expected
        return expected
    }

    String printType(
            SchemaPrinter printer,
            GraphQLSchema schema,
            GraphQLType type) {
        assert type instanceof GraphQLNamedType
        def expected = printer.print(type)
        def executable = importSchema(schema)
        SchemaType importedType =
                executable.getType((type as GraphQLNamedType).name)

        assert importedType != null
        assert printer.print(importedType) == expected
        return expected
    }

    String printType(
            SchemaPrinter printer,
            GraphQLType type) {
        return printType(printer, schemaForTypes([type]), type)
    }

    String printDirective(
            SchemaPrinter printer,
            GraphQLSchema schema,
            GraphQLDirective directive) {
        def expected = printer.print(directive)
        def executable = importSchema(schema)
        SchemaDirective importedDirective =
                executable.getDirective(directive.name)

        assert importedDirective != null
        assert printer.print(importedDirective) == expected
        return expected
    }

    String printTypes(
            SchemaPrinter printer,
            List<? extends GraphQLType> types) {
        assert types.every { it instanceof GraphQLNamedType }
        def expected = printer.print(types)
        def executable = importSchema(schemaForTypes(types))
        List<SchemaType> importedTypes = types.collect {
            executable.getType((it as GraphQLNamedType).name)
        }

        assert importedTypes.every { it != null }
        assert printer.print(importedTypes) == expected
        return expected
    }

    String printElements(
            SchemaPrinter printer,
            GraphQLSchema schema,
            List<? extends GraphQLSchemaElement> elements) {
        def expected = printer.print(elements)
        def executable = importSchema(schema)
        List<SchemaElement> importedElements = elements.collect {
            if (it instanceof GraphQLDirective) {
                return executable.getDirective(it.name)
            }
            assert it instanceof GraphQLNamedType
            return executable.getType(it.name)
        }

        assert importedElements.every { it != null }
        assert printer.print(importedElements) == expected
        return expected
    }

    private static GraphQLSchema schemaForTypes(
            List<? extends GraphQLType> types) {
        def query = GraphQLObjectType.newObject()
                .name("SchemaPrinterTestQuery")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("value")
                        .type(Scalars.GraphQLString))
                .build()
        def builder = GraphQLSchema.newSchema().query(query)
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
        Set<String> registeredResolvers = []
        types.each {
            GraphQLNamedType namedType = it as GraphQLNamedType
            builder.additionalType(namedType)
            registerTypeResolvers(
                    namedType,
                    codeRegistry,
                    registeredResolvers)
        }
        builder.codeRegistry(codeRegistry.build())
        return builder.build()
    }

    private static void registerTypeResolvers(
            GraphQLNamedType type,
            GraphQLCodeRegistry.Builder codeRegistry,
            Set<String> registeredResolvers) {
        if (type instanceof GraphQLInterfaceType
                || type instanceof GraphQLUnionType) {
            if (registeredResolvers.add(type.name)) {
                codeRegistry.typeResolver(
                        type.name,
                        { null } as TypeResolver)
            }
        }
        if (!(type instanceof GraphQLImplementingType)) {
            return
        }
        type.interfaces.each {
            registerTypeResolvers(
                    it as GraphQLNamedType,
                    codeRegistry,
                    registeredResolvers)
        }
    }

    private static SUExecutableSchema importSchema(
            GraphQLSchema schema) {
        def imported = new SchemaUniverse()
                .importSchema("schema_printer_element_test", schema)
        return SUExecutableSchema.fromGraphQLSchema(imported, schema)
    }
}

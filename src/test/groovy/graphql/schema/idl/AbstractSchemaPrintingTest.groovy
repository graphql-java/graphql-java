package graphql.schema.idl

import graphql.schema.GraphQLSchema
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
}

package graphql

import static graphql.schema.GraphQLUnionType.newUnionType
import static graphql.schema.GraphQLTypeReference.typeRef
import graphql.schema.idl.SchemaPrinter

import spock.lang.Specification

class Issue3434 extends Specification {

    def "allow printing of union types"() {
        given:
        def schema = newUnionType().name("Shape")
                .possibleType(typeRef("Circle"))
                .possibleType(typeRef("Square"))
                .build()

        when:
        def printer = new SchemaPrinter()
        def result = printer.print(schema)

        then:
        result.trim() == "union Shape = Circle | Square"
    }
}


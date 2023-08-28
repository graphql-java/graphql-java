package graphql

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import graphql.schema.idl.UnExecutableSchemaGenerator;

import spock.lang.Specification


class Issue2141 extends Specification {

    def " remove redundant parenthesis "() {
        when:
        def schemaDesc = """
        directive @auth(roles: [String!]) on FIELD_DEFINITION
        
        type Query {
             hello: String! @auth 
        }
        """
        def schema = UnExecutableSchemaGenerator.makeUnExecutableSchema(new SchemaParser().parse(schemaDesc))
        def schemaStr = new SchemaPrinter().print(schema)

        then:
        schemaStr == '''directive @auth(roles: [String!]) on FIELD_DEFINITION

"Marks the field, argument, input field or enum value as deprecated"
directive @deprecated(
    "The reason for the deprecation"
    reason: String = "No longer supported"
  ) on FIELD_DEFINITION | ARGUMENT_DEFINITION | ENUM_VALUE | INPUT_FIELD_DEFINITION

"Directs the executor to include this field or fragment only when the `if` argument is true"
directive @include(
    "Included when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Indicates an Input Object is a OneOf Input Object."
directive @oneOf on INPUT_OBJECT

"Directs the executor to skip this field or fragment when the `if` argument is true."
directive @skip(
    "Skipped when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Exposes a URL that specifies the behaviour of this scalar."
directive @specifiedBy(
    "The URL that specifies the behaviour of this scalar."
    url: String!
  ) on SCALAR

type Query {
  hello: String! @auth
}
'''
    }
}

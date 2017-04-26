package graphql

import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

class Issue296 extends Specification {

    def "test introspection for #296"() {

        def graphql = GraphQL.newGraphQL(newSchema()
                .query(newObject()
                .name("Query")
                .field(newFieldDefinition()
                .name("field")
                .type(GraphQLString)
                .argument(newArgument()
                .name("argument")
                .type(newInputObject()
                .name("InputObjectType")
                .field(newInputObjectField()
                .name("inputField")
                .type(GraphQLString))
                .build())
                .defaultValue([field1:'value1']))))
                .build())
                .build()

        def query = '{ __type(name: "Query") { fields { args { defaultValue } } } }'

        // Instead of `'{field: "value"}'` you get '{field1=value1}' (#toString())
        expect:
        graphql.execute(query).data == [ __type: [ fields: [ [ args: [ [ defaultValue: '{field: "value"}' ] ] ] ] ] ]
    }
}
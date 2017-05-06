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

    def "test introspection for #296 with map"() {

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
                .defaultValue([inputField: 'value1']))))
                .build())
                .build()

        def query = '{ __type(name: "Query") { fields { args { defaultValue } } } }'

        expect:
        // converts the default object value to AST, then graphql pretty prints that as the value
        graphql.execute(query).data ==
                [__type: [fields: [[args: [[defaultValue: '{inputField : "value1"}']]]]]]
    }

    class FooBar {
        final String inputField = "foo"
        final String bar = "bar"

        String getInputField() {
            return inputField
        }

        String getBar() {
            return bar
        }
    }

    def "test introspection for #296 with some object"() {

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
                .defaultValue(new FooBar()))))
                .build())
                .build()

        def query = '{ __type(name: "Query") { fields { args { defaultValue } } } }'

        expect:
        // converts the default object value to AST, then graphql pretty prints that as the value
        graphql.execute(query).data ==
                [__type: [fields: [[args: [[defaultValue: '{inputField : "foo"}']]]]]]
    }
}


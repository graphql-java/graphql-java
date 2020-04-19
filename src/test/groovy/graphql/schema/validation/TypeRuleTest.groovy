package graphql.schema.validation

import graphql.AssertException
import graphql.TestUtil
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.validation.exception.InvalidSchemaException
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

class TypeRuleTest extends Specification {


    def "A non null type cannot wrap an existing non null type"() {
        given:
        def graphqlObjectType = GraphQLObjectType.newObject()
                .name("TypeA")
                .field(newFieldDefinition()
                        .name("field")
                        .type(GraphQLString))
                .build()

        when:
        def nestedNonNullType = GraphQLNonNull.nonNull(GraphQLNonNull.nonNull(graphqlObjectType))
        newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(newFieldDefinition()
                                .name("field")
                                .type(nestedNonNullType))
                        .build()
        ).build()

        then:
        def exception = thrown(AssertException)
        exception.message.contains("A non null type cannot wrap an existing non null type")
    }


    def "Directive name must not begin with \"__\""() {
        given:
        String spec = """
        directive @__customedDirective on FIELD_DEFINITION
        
        
        type Query {
            ID: String @__customedDirective
        }
        """

        when:
        TestUtil.schema(spec)

        then:
        def exception = thrown(InvalidSchemaException)
        exception instanceof InvalidSchemaException
        exception.getMessage() == "invalid schema:\nDirective \"__customedDirective\" must not begin with \"__\", which is reserved by GraphQL introspection."
    }


    def "Directive must not reference itself directly or indirectly"() {
        given:
        String spec = """
        directive @invalidExample(arg: String @invalidExample) on ARGUMENT_DEFINITION
        
        type Query {
            ID: String 
        }
        """

        when:
        TestUtil.schema(spec)

        then:
        def exception = thrown(InvalidSchemaException)
        exception instanceof InvalidSchemaException
        exception.getMessage() == "invalid schema:\nDirective \"invalidExample\" must not reference itself directly or indirectly."
    }

}

package graphql.schema.validation

import graphql.Scalars
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition

class SchemaValidatorTest extends Specification {


    def "check used rules"() {
        when:
        def validator = new SchemaValidator()
        def rules = validator.rules
        then:
        rules.size() == 3
        rules[0] instanceof NoUnbrokenInputCycles
        rules[1] instanceof TypesImplementInterfaces
        rules[2] instanceof TypeAndFieldRule
    }

    def "rules are used"() {
        def queryType = GraphQLObjectType.newObject()
                .name("query")
                .field(newFieldDefinition()
                        .name("field")
                        .type(GraphQLString))
                .build()
        def schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build()
        def dummyRule = Mock(SchemaValidationRule)
        when:
        def validator = new SchemaValidator([dummyRule])
        validator.validateSchema(schema)

        then:
        1 * dummyRule.check(queryType, _ as SchemaValidationErrorCollector)
    }

    def "query fields are checked"() {
        def field = GraphQLFieldDefinition.newFieldDefinition()
                .name("field")
                .type(Scalars.GraphQLString)
                .build()
        def queryType = GraphQLObjectType.newObject()
                .name("query")
                .field(field)
                .build()
        def schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build()
        def dummyRule = Mock(SchemaValidationRule)
        when:
        def validator = new SchemaValidator([dummyRule])
        validator.validateSchema(schema)

        then:
        1 * dummyRule.check(field, _ as SchemaValidationErrorCollector)
    }

    def "mutation fields are checked"() {
        def dummyRule = Mock(SchemaValidationRule)
        def field = GraphQLFieldDefinition.newFieldDefinition()
                .name("field")
                .type(Scalars.GraphQLString)
                .build()
        def mutation = GraphQLObjectType.newObject()
                .name("mutation")
                .field(field)
                .build()
        def queryType = GraphQLObjectType.newObject()
                .name("query")
                .field(field)
                .build()
        def schema = GraphQLSchema.newSchema()
                .query(queryType)
                .mutation(mutation)
                .build()
        when:
        def validator = new SchemaValidator([dummyRule])
        validator.validateSchema(schema)

        then:
        2 * dummyRule.check(field, _ as SchemaValidationErrorCollector)
    }

    def "subscription fields are checked"() {
        def dummyRule = Mock(SchemaValidationRule)
        def field = GraphQLFieldDefinition.newFieldDefinition()
                .name("field")
                .type(Scalars.GraphQLString)
                .build()
        def mutation = GraphQLObjectType.newObject()
                .name("subscription")
                .field(field)
                .build()
        def queryType = GraphQLObjectType.newObject()
                .name("query")
                .field(field)
                .build()
        def schema = GraphQLSchema.newSchema()
                .query(queryType)
                .subscription(mutation)
                .build()
        when:
        def validator = new SchemaValidator([dummyRule])
        validator.validateSchema(schema)

        then:
        2 * dummyRule.check(field, _ as SchemaValidationErrorCollector)
    }

}

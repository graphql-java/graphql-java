package graphql.schema.validation

import graphql.Scalars
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.validation.exception.SchemaValidationErrorCollector
import graphql.schema.validation.rules.NoUnbrokenInputCyclesRule
import graphql.schema.validation.rules.ObjectsImplementInterfaces
import graphql.schema.validation.rules.SchemaValidationRule

import spock.lang.Specification

class SchemaValidatorTest extends Specification {


    def "check used rules"() {
        when:
        def validator = new SchemaValidator()
        def rules = validator.rules
        then:
        rules.size() == 2
        rules[0] instanceof NoUnbrokenInputCyclesRule
        rules[1] instanceof ObjectsImplementInterfaces
    }

    def "rules are used"() {
        def queryType = GraphQLObjectType.newObject()
                .name("query")
                .build()
        def schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build()
        def dummyRule = Mock(SchemaValidationRule)
        when:
        def validator = new SchemaValidator([dummyRule])
        validator.validateSchema(schema)

        then:
        1 * dummyRule.check(schema, _ as SchemaValidationErrorCollector)
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
        1 * dummyRule.check(schema, _ as SchemaValidationErrorCollector)
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
                .build()
        def schema = GraphQLSchema.newSchema()
                .query(queryType)
                .mutation(mutation)
                .build()
        when:
        def validator = new SchemaValidator([dummyRule])
        validator.validateSchema(schema)

        then:
        1 * dummyRule.check(schema, _ as SchemaValidationErrorCollector)
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
                .build()
        def schema = GraphQLSchema.newSchema()
                .query(queryType)
                .subscription(mutation)
                .build()
        when:
        def validator = new SchemaValidator([dummyRule])
        validator.validateSchema(schema)

        then:
        1 * dummyRule.check(schema, _ as SchemaValidationErrorCollector)
    }

}

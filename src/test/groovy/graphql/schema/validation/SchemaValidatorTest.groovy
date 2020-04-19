package graphql.schema.validation

import graphql.Scalars
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.validation.exception.SchemaValidationErrorCollector
import graphql.schema.validation.rule.DirectiveRule

import graphql.schema.validation.rule.NoUnbrokenInputCycles
import graphql.schema.validation.rule.ObjectsImplementInterfaces
import graphql.schema.validation.rule.SchemaValidationRule
import graphql.schema.validation.rule.TypeRule
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition

class SchemaValidatorTest extends Specification {


    def "check used rules"() {
        when:
        def validator = new SchemaValidator()
        def rules = validator.rules
        then:
        rules.size() == 4
        rules[0] instanceof NoUnbrokenInputCycles
        rules[1] instanceof ObjectsImplementInterfaces
        rules[2] instanceof TypeRule
        rules[3] instanceof DirectiveRule
    }

    def "rules are used"() {
        def queryType = GraphQLObjectType.newObject()
                .name("query")
                .field(newFieldDefinition()
                        .name("field")
                        .type(GraphQLString)
                        .argument(newArgument()
                                .name("arg")
                                .type(GraphQLNonNull.nonNull(GraphQLString))))
                .build()
        def schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build()
        def dummyRule = Mock(SchemaValidationRule)
        when:
        def validator = new SchemaValidator([dummyRule])
        validator.validateSchema(schema)

        then:
        1 * dummyRule.apply(schema, _ as SchemaValidationErrorCollector)
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
        1 * dummyRule.apply(schema, _ as SchemaValidationErrorCollector)
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
        1 * dummyRule.apply(schema, _ as SchemaValidationErrorCollector)
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
        1 * dummyRule.apply(schema, _ as SchemaValidationErrorCollector)
    }

}

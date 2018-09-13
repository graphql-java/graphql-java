package graphql.validation.rules

import graphql.Directives
import graphql.StarWarsSchema
import graphql.language.Directive
import graphql.validation.ValidationContext
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLNonNull.nonNull

class DeferredDirectiveOnNonNullableFieldTest extends Specification {
    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    DeferredDirectiveOnNonNullableField deferredOnNullableField = new DeferredDirectiveOnNonNullableField(validationContext, errorCollector)


    def "denies non nullable fields"() {
        def fieldDefinition = newFieldDefinition().name("field").type(nonNull(GraphQLString)).build()

        validationContext.getParentType() >> StarWarsSchema.humanType
        validationContext.getFieldDef() >> fieldDefinition

        when:
        deferredOnNullableField.checkDirective(new Directive(Directives.DeferDirective.name), [])
        then:
        !errorCollector.errors.isEmpty()
        (errorCollector.errors[0] as ValidationError).validationErrorType == ValidationErrorType.DeferDirectiveOnNonNullField
    }

    def "allows nullable fields"() {
        def fieldDefinition = newFieldDefinition().name("field").type(GraphQLString).build()

        validationContext.getParentType() >> StarWarsSchema.humanType
        validationContext.getFieldDef() >> fieldDefinition

        when:
        deferredOnNullableField.checkDirective(new Directive(Directives.DeferDirective.name), [])
        then:
        errorCollector.errors.isEmpty()
    }
}

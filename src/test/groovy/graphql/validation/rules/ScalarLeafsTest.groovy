package graphql.validation.rules

import graphql.Scalars
import graphql.language.Field
import graphql.language.SelectionSet
import graphql.schema.GraphQLObjectType
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import static graphql.language.Field.newField

class ScalarLeafsTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    ValidationContext validationContext = Mock(ValidationContext)
    ScalarLeafs scalarLeafs = new ScalarLeafs(validationContext, errorCollector)

    def "sub selection not allowed"() {
        given:
        Field field = newField("hello", SelectionSet.newSelectionSet([newField("world").build()]).build()).build()
        validationContext.getOutputType() >> Scalars.GraphQLString
        when:
        scalarLeafs.checkField(field)

        then:
        errorCollector.containsValidationError(
                ValidationErrorType.SubSelectionNotAllowed,
                "Sub selection not allowed on leaf type String of field hello"
        )
    }

    def "sub selection required"() {
        given:
        Field field = newField("hello").build()
        validationContext.getOutputType() >> GraphQLObjectType.newObject().name("objectType").build()
        when:
        scalarLeafs.checkField(field)

        then:
        errorCollector.containsValidationError(
                ValidationErrorType.SubSelectionRequired,
                "Sub selection required for type objectType of field hello"
        )
    }
}

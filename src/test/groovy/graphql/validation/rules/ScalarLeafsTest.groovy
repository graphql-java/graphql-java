package graphql.validation.rules

import graphql.Scalars
import graphql.language.Field
import graphql.language.SelectionSet
import graphql.schema.GraphQLObjectType
import graphql.validation.ErrorCollector
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorType
import spock.lang.Specification


class ScalarLeafsTest extends Specification {

    ErrorCollector errorCollector = new ErrorCollector()
    ValidationContext validationContext = Mock(ValidationContext)
    ScalarLeafs scalarLeafs = new ScalarLeafs(validationContext, errorCollector)

    def "sub selection not allowed"() {
        given:
        Field field = new Field("hello", new SelectionSet([new Field("world")]))
        validationContext.getOutputType() >> Scalars.GraphQLString
        when:
        scalarLeafs.checkField(field)

        then:
        errorCollector.containsError(ValidationErrorType.SubSelectionNotAllowed)
    }

    def "sub selection required"() {
        given:
        Field field = new Field("hello")
        validationContext.getOutputType() >> GraphQLObjectType.newObject().build()
        when:
        scalarLeafs.checkField(field)

        then:
        errorCollector.containsError(ValidationErrorType.SubSelectionRequired)
    }
}

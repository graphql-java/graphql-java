package graphql.validation.rules

import graphql.StarWarsSchema
import graphql.language.FragmentDefinition
import graphql.language.InlineFragment
import graphql.language.TypeName
import graphql.validation.ErrorCollector
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorType
import spock.lang.Specification


class FragmentsOnCompositeTypeTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ErrorCollector errorCollector = new ErrorCollector()
    FragmentsOnCompositeType fragmentsOnCompositeType = new FragmentsOnCompositeType(validationContext, errorCollector)

    def "inline fragment type condition must refer to a composite type"() {
        given:
        InlineFragment inlineFragment = new InlineFragment(new TypeName("String"))
        validationContext.getSchema() >> StarWarsSchema.starWarsSchema

        when:
        fragmentsOnCompositeType.checkInlineFragment(inlineFragment)

        then:
        errorCollector.containsError(ValidationErrorType.InlineFragmentTypeConditionInvalid)
    }

    def "fragment type condition must refer to a composite type"() {
        given:
        FragmentDefinition fragmentDefinition = new FragmentDefinition("fragment",new TypeName("String"))
        validationContext.getSchema() >> StarWarsSchema.starWarsSchema

        when:
        fragmentsOnCompositeType.checkFragmentDefinition(fragmentDefinition)

        then:
        errorCollector.containsError(ValidationErrorType.InlineFragmentTypeConditionInvalid)
    }


}

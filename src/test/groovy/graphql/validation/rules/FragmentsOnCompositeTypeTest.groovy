package graphql.validation.rules

import graphql.StarWarsSchema
import graphql.language.FragmentDefinition
import graphql.language.InlineFragment
import graphql.language.TypeName
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class FragmentsOnCompositeTypeTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    FragmentsOnCompositeType fragmentsOnCompositeType = new FragmentsOnCompositeType(validationContext, errorCollector)

    def "inline fragment type condition must refer to a composite type"() {
        given:
        InlineFragment inlineFragment = new InlineFragment(new TypeName("String"))
        validationContext.getSchema() >> StarWarsSchema.starWarsSchema

        when:
        fragmentsOnCompositeType.checkInlineFragment(inlineFragment)

        then:
        errorCollector.containsValidationError(ValidationErrorType.InlineFragmentTypeConditionInvalid)
        errorCollector.errors.size() == 1
        errorCollector.errors[0].message == "Validation error of type InlineFragmentTypeConditionInvalid: Inline fragment type condition is invalid, must be on Object/Interface/Union"
    }

    def "should results in no error"(InlineFragment inlineFragment) {
        given:
        validationContext.getSchema() >> StarWarsSchema.starWarsSchema

        when:
        fragmentsOnCompositeType.checkInlineFragment(inlineFragment)

        then:
        errorCollector.errors.isEmpty()

        where:
        inlineFragment << [
                getInlineFragmentWithTypeConditionNull(),
                getInlineFragmentWithConditionWithStrangeType(),
                getInlineFragmentWithConditionWithRightType()
        ]
    }

    private InlineFragment getInlineFragmentWithTypeConditionNull() {
        Mock(InlineFragment)
    }

    private InlineFragment getInlineFragmentWithConditionWithStrangeType() {
        new InlineFragment(new TypeName("StrangeType"))
    }

    private InlineFragment getInlineFragmentWithConditionWithRightType() {
        new InlineFragment(new TypeName("Character"))
    }

    def "fragment type condition must refer to a composite type"() {
        given:
        FragmentDefinition fragmentDefinition = new FragmentDefinition("fragment",new TypeName("String"))
        validationContext.getSchema() >> StarWarsSchema.starWarsSchema

        when:
        fragmentsOnCompositeType.checkFragmentDefinition(fragmentDefinition)

        then:
        errorCollector.containsValidationError(ValidationErrorType.InlineFragmentTypeConditionInvalid)
    }


}

package graphql.validation.rules

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.TestUtil
import graphql.language.FragmentDefinition
import graphql.language.InlineFragment
import graphql.language.TypeName
import graphql.validation.ValidationContext
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class FragmentsOnCompositeTypeTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    FragmentsOnCompositeType fragmentsOnCompositeType = new FragmentsOnCompositeType(validationContext, errorCollector)

    def setup() {
        validationContext.i18n(_, _) >> "test error message"
    }

    def "inline fragment type condition must refer to a composite type"() {
        given:
        InlineFragment inlineFragment = InlineFragment.newInlineFragment().typeCondition(TypeName.newTypeName("String").build()).build()
        validationContext.getSchema() >> StarWarsSchema.starWarsSchema

        when:
        fragmentsOnCompositeType.checkInlineFragment(inlineFragment)

        then:
        errorCollector.containsValidationError(ValidationErrorType.InlineFragmentTypeConditionInvalid)
        errorCollector.errors.size() == 1
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
        InlineFragment.newInlineFragment().build()
    }

    private InlineFragment getInlineFragmentWithConditionWithStrangeType() {
        InlineFragment.newInlineFragment().typeCondition(TypeName.newTypeName("StrangeType").build()).build()
    }

    private InlineFragment getInlineFragmentWithConditionWithRightType() {
        InlineFragment.newInlineFragment().typeCondition(TypeName.newTypeName("Character").build()).build()
    }

    def "fragment type condition must refer to a composite type"() {
        given:
        FragmentDefinition fragmentDefinition = FragmentDefinition.newFragmentDefinition().name("fragment").typeCondition(TypeName.newTypeName("String").build()).build()
        validationContext.getSchema() >> StarWarsSchema.starWarsSchema

        when:
        fragmentsOnCompositeType.checkFragmentDefinition(fragmentDefinition)

        then:
        errorCollector.containsValidationError(ValidationErrorType.FragmentTypeConditionInvalid)
    }

    def schema = TestUtil.schema("""
            type Query {
                nothing: String
            }
            
            type Mutation {
                updateUDI(input: UDIInput!): UDIOutput
            }
            
            type UDIOutput {
                device: String
                version: String
            }
            
            input UDIInput {
                device: String 
                version: String
            }
        """)

    def graphQL = GraphQL.newGraphQL(schema).build()


    def "#1440 when fragment type condition is input type it should return validation error - not classCastException"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .query('''                    
                    mutation UpdateUDI($input: UDIInput!) { 
                        updateUDI(input: $input) { 
                            ...fragOnInputType 
                            __typename 
                        } 
                    }
                    
                    # fragment should only target composite types
                    fragment fragOnInputType on UDIInput { 
                        device
                        version 
                        __typename 
                    } 
                    
                    ''')
                .variables([input: [device: 'device', version: 'version'] ])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:

        executionResult.data == null
        executionResult.errors.size() == 1
        (executionResult.errors[0] as ValidationError).validationErrorType == ValidationErrorType.FragmentTypeConditionInvalid
        (executionResult.errors[0] as ValidationError).message == "Validation error (FragmentTypeConditionInvalid@[fragOnInputType]) : Fragment type condition is invalid, must be on Object/Interface/Union"
    }

    def "#1440 when inline fragment type condition is input type it should return validation error - not classCastException"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .query('''                    
                    mutation UpdateUDI($input: UDIInput!) { 
                        updateUDI(input: $input) { 
                            # fragment should only target composite types
                            ... on UDIInput { 
                                device
                                version 
                                __typename 
                            }  
                            __typename 
                        } 
                    }
                    ''')
                .variables([input: [device: 'device', version: 'version'] ])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:

        executionResult.data == null
        executionResult.errors.size() == 1
        (executionResult.errors[0] as ValidationError).validationErrorType == ValidationErrorType.InlineFragmentTypeConditionInvalid
        (executionResult.errors[0] as ValidationError).message == "Validation error (InlineFragmentTypeConditionInvalid@[updateUDI]) : Inline fragment type condition is invalid, must be on Object/Interface/Union"
    }

}

package graphql.validation

class SpecValidation532Test extends SpecValidationBase {

    def '5.3.2 Field Selection Merging - conflicting fields in mutually exclusive fragments'() {
        def query = """
            query getPetVolume {
              pet {
                ... safeDifferingFields
                
                ... safeDifferingArgs
              }
            }
            
            fragment safeDifferingFields on Pet {
              ... on Dog {
                volume: barkVolume
              }
              ... on Cat {
                volume: meowVolume
              }
            }
            
            fragment safeDifferingArgs on Pet {
              ... on Dog {
                doesKnowCommand(dogCommand: SIT)
              }
              ... on Cat {
                doesKnowCommand(catCommand: JUMP)
              }
            }
        """

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def '5.3.2 Field Selection Merging - field responses must be shapes which can be merged'() {
        def query = """
            query getPetVolume {
              pet {
                ... conflictingDifferingResponses
              }
            }
            
            fragment conflictingDifferingResponses on Pet {
              ... on Dog {
                someValue: nickname # String
              }
              ... on Cat {
                someValue: meowVolume # Int
              }
            }
        """

        when:
        def validationErrors = validate(query)

        then:
        ! validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.FieldsConflict
    }
}
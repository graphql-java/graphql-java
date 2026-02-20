package graphql.validation.rules

import graphql.language.Field
import graphql.parser.Parser
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationContext
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class FieldsOnCorrectTypeTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    ValidationContext validationContext = Mock(ValidationContext)
    FieldsOnCorrectType fieldsOnCorrectType = new FieldsOnCorrectType(validationContext, errorCollector)

    def setup() {
        validationContext.i18n(_, _) >> "test error message"
    }


    def "should add error to collector when field definition is null"() {
        given:
        def parentType = GraphQLObjectType.newObject().name("parentType").build()
        validationContext.getParentType() >> parentType
        validationContext.getFieldDef() >> null
        def field = new Field("name")

        when:
        fieldsOnCorrectType.checkField(field)

        then:
        errorCollector.containsValidationError(ValidationErrorType.FieldUndefined)
        errorCollector.errors.size() == 1
    }

    def "should results in no error when field definition is filled"() {
        given:
        def parentType = GraphQLObjectType.newObject().name("parentType").build()
        validationContext.getParentType() >> parentType
        validationContext.getFieldDef() >> Mock(GraphQLFieldDefinition)
        def field = new Field("name")

        when:
        fieldsOnCorrectType.checkField(field)

        then:
        errorCollector.errors.isEmpty()
    }

    def "should results in no error when parent type is null"() {
        given:
        validationContext.getParentType() >> null
        def field = new Field("name")

        when:
        fieldsOnCorrectType.checkField(field)

        then:
        errorCollector.errors.isEmpty()
    }

    def '5.2.1 Field Selections on ... fieldNotDefined'() {
        def query = """
{
  dog {
    ... fieldNotDefined
  }
}
fragment fieldNotDefined on Dog {
  meowVolume
}
"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.FieldUndefined
        validationErrors.get(0).message == "Validation error (FieldUndefined@[fieldNotDefined/meowVolume]) : Field 'meowVolume' in type 'Dog' is undefined"
    }

    def '5.2.1 Field Selections on ... aliasedLyingFieldTargetNotDefined'() {
        def query = """
{
  dog {
    ... aliasedLyingFieldTargetNotDefined
  }
}
fragment aliasedLyingFieldTargetNotDefined on Dog {
  barkVolume: kawVolume
}
"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.FieldUndefined
        validationErrors.get(0).message == "Validation error (FieldUndefined@[aliasedLyingFieldTargetNotDefined/kawVolume]) : Field 'kawVolume' in type 'Dog' is undefined"
    }

    def '5.2.1 Field Selections on ... interfaceFieldSelection'() {
        def query = """
{
  dog {
    ... interfaceFieldSelection
  }
}
fragment interfaceFieldSelection on Pet {
  name
}
"""
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def '5.2.1 Field Selections on ... definedOnImplementorsButNotInterface'() {
        def query = """
{
  dog {
    ... definedOnImplementorsButNotInterface
  }
}
fragment definedOnImplementorsButNotInterface on Pet {
  nickname
}
"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.FieldUndefined
        validationErrors.get(0).message == "Validation error (FieldUndefined@[definedOnImplementorsButNotInterface/nickname]) : Field 'nickname' in type 'Pet' is undefined"
    }

    def '5.2.1 Field Selections on ... inDirectFieldSelectionOnUnion'() {
        def query = """
{
  dog {
    ... inDirectFieldSelectionOnUnion
  }
}
fragment inDirectFieldSelectionOnUnion on CatOrDog {
  __typename
  ... on Pet {
    name
  }
  ... on Dog {
    barkVolume
  }
}
"""
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def '5.2.1 Field Selections on ... directFieldSelectionOnUnion'() {
        def query = """
{
  dog {
    ... directFieldSelectionOnUnion
  }
}
fragment directFieldSelectionOnUnion on CatOrDog {
  name
  barkVolume
}
"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 2
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.FieldUndefined
        validationErrors.get(0).message == "Validation error (FieldUndefined@[directFieldSelectionOnUnion/name]) : Field 'name' in type 'CatOrDog' is undefined"
        validationErrors.get(1).getValidationErrorType() == ValidationErrorType.FieldUndefined
        validationErrors.get(1).message == "Validation error (FieldUndefined@[directFieldSelectionOnUnion/barkVolume]) : Field 'barkVolume' in type 'CatOrDog' is undefined"
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}

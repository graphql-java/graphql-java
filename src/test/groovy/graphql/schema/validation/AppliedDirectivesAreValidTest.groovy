package graphql.schema.validation

import graphql.TestUtil
import spock.lang.Specification

class AppliedDirectivesAreValidTest extends Specification {

    def "non repeatable directives cannot be repeated"() {
        def sdl = '''

            directive @directiveA on FIELD_DEFINITION | ENUM_VALUE | INPUT_FIELD_DEFINITION
            directive @directiveOK on FIELD_DEFINITION | ENUM_VALUE | INPUT_FIELD_DEFINITION

            interface InterfaceType1 {
                fieldA : String @directiveA @directiveA 
            }

            type Query implements InterfaceType1 {
                fieldA : String
                fieldC : String @directiveA @directiveA
            }

            extend type Query {
                fieldB : Int
                fieldD: Int @directiveA @directiveA
                fieldE: Int @directiveA @directiveOK
            }
            
            enum EnumType {
                
                enumA @directiveA @directiveA
                enumB @directiveA @directiveOK
            }

            input InputType {
                inputFieldA : String @directiveA @directiveA
                inputFieldB : String @directiveA @directiveOK
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.getErrors().size() == 5
        hasError(schemaProblem, "The directive 'directiveA' on the 'GraphQLFieldDefinition' called 'fieldC' is a non repeatable directive but has been applied 2 times");
        hasError(schemaProblem, "The directive 'directiveA' on the 'GraphQLFieldDefinition' called 'fieldD' is a non repeatable directive but has been applied 2 times");
        hasError(schemaProblem, "The directive 'directiveA' on the 'GraphQLFieldDefinition' called 'fieldA' is a non repeatable directive but has been applied 2 times");
        hasError(schemaProblem, "The directive 'directiveA' on the 'GraphQLEnumValueDefinition' called 'enumA' is a non repeatable directive but has been applied 2 times");
        hasError(schemaProblem, "The directive 'directiveA' on the 'GraphQLInputObjectField' called 'inputFieldA' is a non repeatable directive but has been applied 2 times");
    }

    static boolean hasError(InvalidSchemaException schemaException, String msg) {
        def err = schemaException.getErrors().find { it.description == msg }
        return err != null
    }
}

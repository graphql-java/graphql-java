package graphql.schema.validation

import graphql.TestUtil
import graphql.schema.FieldCoordinates
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.TestUtil.mkDirective
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

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
        hasError(schemaProblem, "The directive 'directiveA' on the 'GraphQLFieldDefinition' called 'fieldC' is a non repeatable directive but has been applied 2 times")
        hasError(schemaProblem, "The directive 'directiveA' on the 'GraphQLFieldDefinition' called 'fieldD' is a non repeatable directive but has been applied 2 times")
        hasError(schemaProblem, "The directive 'directiveA' on the 'GraphQLFieldDefinition' called 'fieldA' is a non repeatable directive but has been applied 2 times")
        hasError(schemaProblem, "The directive 'directiveA' on the 'GraphQLEnumValueDefinition' called 'enumA' is a non repeatable directive but has been applied 2 times")
        hasError(schemaProblem, "The directive 'directiveA' on the 'GraphQLInputObjectField' called 'inputFieldA' is a non repeatable directive but has been applied 2 times")
    }

    def "applied directive builders do not clear any existing applied directives"() {
        given:
        def directive1 = mkDirective("myDirectiveName1", FIELD_DEFINITION)
        def directive2 = mkDirective("myDirectiveName2", FIELD_DEFINITION)
        def field = newFieldDefinition()
                .name("hello")
                .type(GraphQLString)
                .withAppliedDirectives(directive1.toAppliedDirective())
                .withAppliedDirectives(directive2.toAppliedDirective())
                .build()

        when:
        def schema = newSchema()
                .query(
                        newObject()
                                .name("Query")
                                .field(field)
                                .build()
                )
                .additionalDirective(directive1)
                .additionalDirective(directive2)
                .build()

        then:
        def fieldAppliedDirectives = schema.getFieldDefinition(FieldCoordinates.coordinates("Query", "hello")).getAppliedDirectives()
        fieldAppliedDirectives.size() == 2
        fieldAppliedDirectives.any { it.name == "myDirectiveName1" }
        fieldAppliedDirectives.any { it.name == "myDirectiveName2" }
    }

    def "replace applied directive builder does clear and replace existing applied directives"(){
        given:
        def directive1 = mkDirective("myDirectiveName1", FIELD_DEFINITION)
        def directive2 = mkDirective("myDirectiveName2", FIELD_DEFINITION)
        def field = newFieldDefinition()
                .name("hello")
                .type(GraphQLString)
                .withAppliedDirective(directive1.toAppliedDirective())
                .replaceAppliedDirectives(List.of(directive2.toAppliedDirective()))
                .build()

        when:
        def schema = newSchema()
                .query(
                        newObject()
                                .name("Query")
                                .field(field)
                                .build()
                )
                .additionalDirective(directive1)
                .additionalDirective(directive2)
                .build()

        then:
        // As prior applied directives are cleared, there is only 1 applied directive left on the field (directive container)
        def fieldAppliedDirectives = schema.getFieldDefinition(FieldCoordinates.coordinates("Query", "hello")).getAppliedDirectives()
        fieldAppliedDirectives.size() == 1
        fieldAppliedDirectives.find { it.name == "myDirectiveName1" } == null
        fieldAppliedDirectives.any { it.name == "myDirectiveName2" }
    }

    static boolean hasError(InvalidSchemaException schemaException, String msg) {
        def err = schemaException.getErrors().find { it.description == msg }
        return err != null
    }
}

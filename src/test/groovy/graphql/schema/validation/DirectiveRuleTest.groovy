package graphql.schema.validation

import graphql.TestUtil
import graphql.schema.validation.exception.InvalidSchemaException
import spock.lang.Specification

class DirectiveRuleTest extends Specification {


    def "Valid directive definition"() {
        given:

        String spec = """
        directive @validDirective on FIELD_DEFINITION
        
        type Query {
            ID: String @validDirective
        }
        
        """

        when:
        Exception exception;
        try {
            TestUtil.schema(spec)
        } catch (Exception e) {
            exception = e;
        }

        then:
        exception == null
    }


    def "Directive name must not begin with \"__\""() {
        given:

        String spec = """
        directive @__customedDirective on FIELD_DEFINITION
        
        
        type Query {
            ID: String @__customedDirective
        }
        
        """

        when:
        Exception exception;
        try {
            TestUtil.schema(spec)
        } catch (Exception e) {
            exception = e;
        }

        then:
        exception instanceof InvalidSchemaException
        exception.getMessage() == "invalid schema:\nDirective \"__customedDirective\" must not begin with \"__\", which is reserved by GraphQL introspection."
    }


    def "Directive must not reference itself directly or indirectly"() {
        given:

        String spec = """
        directive @invalidExample(arg: String @invalidExample) on ARGUMENT_DEFINITION
        
        type Query {
            ID: String 
        }
        
        """

        when:
        Exception exception;
        try {
            TestUtil.schema(spec)
        } catch (Exception e) {
            exception = e;
        }

        then:
        exception instanceof InvalidSchemaException
        exception.getMessage() == "invalid schema:\nDirective \"invalidExample\" must not reference itself directly or indirectly."
    }

}

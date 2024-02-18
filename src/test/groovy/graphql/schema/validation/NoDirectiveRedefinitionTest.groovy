package graphql.schema.validation

import graphql.TestUtil
import spock.lang.Specification

class NoDirectiveRedefinitionTest extends Specification {
    def "directive cannot be redefined in schema"() {
        def sdl = '''
            directive @exampleDirective on FIELD_DEFINITION
            directive @exampleDirective on FIELD_DEFINITION

            type Query {
                hello: String @exampleDirective
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        def schemaProblem = thrown(AssertionError)
        schemaProblem.message.contains("tried to redefine existing directive 'exampleDirective'")
    }
}

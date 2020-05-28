package graphql.execution

import graphql.ExecutionInput
import graphql.TestUtil
import spock.lang.Specification

class NonNullableFieldWasNullExceptionTest extends Specification {

    def "lower level sets a null when it should not and it bubbles all the way up"() {

        def sdl = '''
            type Query {
                topLevelField : Type1! 
            }
            
            type Type1 {
                middleLevelField : Type2!
            }
            
            type Type2 {
                bottomLevelField : String!
            }
        '''

        def graphql = TestUtil.graphQL(sdl).build()

        def query = '''
            { topLevelField { middleLevelField { bottomLevelField } } }
        '''
        when:

        ExecutionInput ei = ExecutionInput.newExecutionInput(query).root(
                [topLevelField: [middleLevelField: [bottomLevelField: null]]]
        ).build()

        def er = graphql.execute(ei)

        then:
        er.data == null
        er.errors[0].path.toList() == ["topLevelField", "middleLevelField", "bottomLevelField"]
    }

    def "lower level sets a null when it should not and it bubbles up one"() {
        def sdl = '''
            type Query {
                topLevelField : Type1! 
            }
            
            type Type1 {
                middleLevelField : Type2
            }
            
            type Type2 {
                bottomLevelField : String!
            }
        '''

        def graphql = TestUtil.graphQL(sdl).build()

        def query = '''
            { topLevelField { middleLevelField { bottomLevelField } } }
        '''
        when:

        ExecutionInput ei = ExecutionInput.newExecutionInput(query).root(
                [topLevelField: [middleLevelField: [bottomLevelField: null]]]
        ).build()

        def er = graphql.execute(ei)

        then:
        er.data == [topLevelField : [middleLevelField : null ]]
        er.errors[0].path.toList() == ["topLevelField", "middleLevelField", "bottomLevelField"]
    }

    def "a top level sets a null when it should not and it bubbles up one"() {
        def sdl = '''
            type Query {
                topLevelField : Type1! 
            }
            
            type Type1 {
                middleLevelField : Type2
            }
            
            type Type2 {
                bottomLevelField : String!
            }
        '''

        def graphql = TestUtil.graphQL(sdl).build()

        def query = '''
            { topLevelField { middleLevelField { bottomLevelField } } }
        '''
        when:

        ExecutionInput ei = ExecutionInput.newExecutionInput(query).root(
                [topLevelField: null]
        ).build()

        def er = graphql.execute(ei)

        then:
        er.data == null
        er.errors[0].path.toList() == ["topLevelField"]

        when:

        ei = ExecutionInput.newExecutionInput(query).root(
                null
        ).build()

        er = graphql.execute(ei)

        then:
        er.data == null
        er.errors[0].path.toList() == ["topLevelField"]
    }
}

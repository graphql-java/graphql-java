package graphql.language

import graphql.TestUtil
import spock.lang.Specification

class DocumentTest extends Specification {

    def "test deep copy works as expected"() {
        def query = '''
        query HeroNameAndFriendsQuery($varDef :VarType) {
            hero(id : $varDef) {
                id
                ...DroidFields
        
                ... on User { name }
                ... on Comment { body author { name } }
            }
            
            han: hero(id: "1001") { name }

            luke: hero(ids: ["1001"]) { name }
            
        }
        
        fragment DroidFields on Droid {
            primaryFunction
        }
        
        type User {
            name : [String!]
            height(units : String = "cms") : Int
        }
        
        '''
        def document = TestUtil.parseQuery(query)

        def expected = AstPrinter.printAst(document)

        def newDoc = document.deepCopy()
        def actual = AstPrinter.printAst(newDoc)

        expect:

        expected == actual
    }
}

package graphql.language

import graphql.TestUtil
import spock.lang.Specification

class AstSorterTest extends Specification {

    def "basic sorting works as expected"() {
        def query = '''
            query QZ {
                fieldZ(z: "valz", x : "valx", y:"valy") {
                    subfieldz
                    subfieldx
                    subfieldy
                }
                fieldX(z: "valz", x : "valx", y:"valy") {
                    subfieldz
                    subfieldx
                    subfieldy
                }
            }

            query QX {
                field(z: "valz", x : "valx", y:"valy") {
                    subfieldz
                    subfieldx
                    subfieldY
                }
            }
                    
        '''
        def expectedQuery = '''
            query QX {
                field(x: "valx", y : "valy", z : "valz") {
                    subfieldx
                    subfieldy
                    subfieldz
                }
            }

            query QZ {
                fieldZ(x: "valx", y : "valy", z : "valz") {
                    subfieldx
                    subfieldy
                    subfieldz
                }
                fieldX(x: "valx", y : "valy", z : "valz") {
                    subfieldx
                    subfieldy
                    subfieldz
                }
            }
        '''

        def doc = TestUtil.parseQuery(query)

        when:
        def newDoc = new AstSorter().sortQuery(doc)
        then:
        AstPrinter.printAst(newDoc) == expectedQuery
    }
}

package graphql.language

import graphql.TestUtil
import spock.lang.Specification

class AstSorterTest extends Specification {

    def "basic sorting of a query works"() {
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
            subfieldx(b : "valb", a : "vala", c : "valc")
            subfieldy
        }
    }
'''
        def expectedQuery = '''query QX {
  field(x: "valx", y: "valy", z: "valz") {
    subfieldx(a: "vala", b: "valb", c: "valc")
    subfieldy
    subfieldz
  }
}

query QZ {
  fieldZ(x: "valx", y: "valy", z: "valz") {
    subfieldx
    subfieldy
    subfieldz
  }
  fieldX(x: "valx", y: "valy", z: "valz") {
    subfieldx
    subfieldy
    subfieldz
  }
}
'''

        def doc = TestUtil.parseQuery(query)

        when:
        def newDoc = new AstSorter().sort(doc)
        then:
        AstPrinter.printAst(newDoc) == expectedQuery
    }
}

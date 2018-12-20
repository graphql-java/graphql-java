package graphql.language


import spock.lang.Specification

import static graphql.TestUtil.parseQuery
import static graphql.language.AstPrinter.printAst

class AstSorterTest extends Specification {

    def "basic sorting of a query works"() {
        def query = '''
    {
        unamedQueryY
        unamedQueryZ
        unamedQueryX
    }
    
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
        ... inlineFragmentB
        field(z: "valz", x : "valx", y:"valy") {
            subfieldz
            subfieldx(b : "valb", a : "vala", c : "valc")
            subfieldy
        }
        ... on Z {
            subfieldC
            subfieldA
            subfieldB
        }
        ... inlineFragmentA
        ... on X {
            subfieldC
            subfieldA
            subfieldB
        }
        ... inlineFragmentC
    }

    query QY($varZ : Int = 3, $varX : Int = 1, $varY : Int = 2) @directiveC @directiveA @directiveB {
        ... on SomeTypeB {
            fieldC
            fieldB
            fieldA @directiveSWithArgs(argZ : "valZ", argX : "valX", argY : "valY")
        }
        ... on SomeTypeA {
            fieldC
            fieldB
            fieldA @directiveSWithArgs(argZ : "valZ", argX : "valX", argY : "valY")
        }
    }  

    fragment FZ on SomeType {
        fieldY(z: "valz", x : "valx", y:"valy") {
            subfieldb
            subfielda
        }
        fieldX(z: "valz", x : "valx", y:"valy") {
            subfieldb
            subfielda
        }
    }
    fragment FX on SomeType {
        fieldY(z: "valz", x : "valx", y:"valy") {
            subfieldb
            subfielda
        }
        fieldX(z: "valz", x : "valx", y:"valy") {
            subfieldb
            subfielda
        }
    }
    fragment FY on SomeType {
        fieldY(z: "valz", x : "valx", y:"valy") {
            subfieldb
            subfielda
        }
        fieldX(z: "valz", x : "valx", y:"valy") {
            subfieldb
            subfielda
        }
    }
    
    
'''

        def expectedQuery = '''query {
  unamedQueryX
  unamedQueryY
  unamedQueryZ
}

query QX {
  field(x: "valx", y: "valy", z: "valz") {
    subfieldx(a: "vala", b: "valb", c: "valc")
    subfieldy
    subfieldz
  }
  ...inlineFragmentA
  ...inlineFragmentB
  ...inlineFragmentC
  ... on X {
    subfieldA
    subfieldB
    subfieldC
  }
  ... on Z {
    subfieldA
    subfieldB
    subfieldC
  }
}

query QY($varX: Int = 1, $varY: Int = 2, $varZ: Int = 3) @directiveA @directiveB @directiveC {
  ... on SomeTypeA {
    fieldA @directiveSWithArgs(argX: "valX", argY: "valY", argZ: "valZ")
    fieldB
    fieldC
  }
  ... on SomeTypeB {
    fieldA @directiveSWithArgs(argX: "valX", argY: "valY", argZ: "valZ")
    fieldB
    fieldC
  }
}

query QZ {
  fieldX(x: "valx", y: "valy", z: "valz") {
    subfieldx
    subfieldy
    subfieldz
  }
  fieldZ(x: "valx", y: "valy", z: "valz") {
    subfieldx
    subfieldy
    subfieldz
  }
}

fragment FX on SomeType {
  fieldX(x: "valx", y: "valy", z: "valz") {
    subfielda
    subfieldb
  }
  fieldY(x: "valx", y: "valy", z: "valz") {
    subfielda
    subfieldb
  }
}

fragment FY on SomeType {
  fieldX(x: "valx", y: "valy", z: "valz") {
    subfielda
    subfieldb
  }
  fieldY(x: "valx", y: "valy", z: "valz") {
    subfielda
    subfieldb
  }
}

fragment FZ on SomeType {
  fieldX(x: "valx", y: "valy", z: "valz") {
    subfielda
    subfieldb
  }
  fieldY(x: "valx", y: "valy", z: "valz") {
    subfielda
    subfieldb
  }
}
'''

        def doc = parseQuery(query)

        when:
        def newDoc = new AstSorter().sort(doc)
        then:
        printAst(newDoc) == expectedQuery
    }

    def "sdl will sort as expected"() {
        def sdl = '''
    
    interface InterfaceZ {
        fieldZ(argC : Int, argA : Int, argB : Int) : Int
        fieldX(argC : Int, argA : Int, argB : Int) : Int
        fieldY(argC : Int, argA : Int, argB : Int) : Int
    }
    
    interface InterfaceX {
        fieldZ(argC : Int, argA : Int, argB : Int) : Int
        fieldX(argC : Int, argA : Int, argB : Int) : Int
        fieldY(argC : Int, argA : Int, argB : Int) : Int
    }
    
    union UnionZ = Foo | Bar
    
    union UnionX = Foo | Bar

    schema {
        query : QueryType
        mutation : MutationType
        subscription : SubscriptionType
    }
    
    type TypeZ {
        fieldZ(argC : Int, argA : Int, argB : Int) : Int
        fieldX(argC : Int, argA : Int, argB : Int) : Int
        fieldY(argC : Int, argA : Int, argB : Int) : Int
    }

    type TypeX {
        fieldZ(argC : Int, argA : Int, argB : Int) : Int
        fieldX(argC : Int, argA : Int, argB : Int) : Int
        fieldY(argC : Int, argA : Int, argB : Int) : Int
    }
    
    input InputZ {
        fieldZ : String
        fieldX : String
        fieldY : String
    }

    input InputX {
        fieldZ : String
        fieldX : String
        fieldY : String
    }
        

    scalar ScalarZ    
    scalar ScalarX    

    directive @directiveZ on FIELD_DEFINITION | ENUM_VALUE
    
    directive @directiveX on FIELD_DEFINITION | ENUM_VALUE
    
    enum EnumZ {
        Z, Y , X
    }

    enum EnumX {
        Z, Y , X
    }
'''

        def expectedSDL = '''directive @directiveX on ENUM_VALUE | FIELD_DEFINITION

directive @directiveZ on ENUM_VALUE | FIELD_DEFINITION

schema {
  mutation: MutationType
  query: QueryType
  subscription: SubscriptionType
}

type TypeX {
  fieldX(argA: Int, argB: Int, argC: Int): Int
  fieldY(argA: Int, argB: Int, argC: Int): Int
  fieldZ(argA: Int, argB: Int, argC: Int): Int
}

type TypeZ {
  fieldX(argA: Int, argB: Int, argC: Int): Int
  fieldY(argA: Int, argB: Int, argC: Int): Int
  fieldZ(argA: Int, argB: Int, argC: Int): Int
}

interface InterfaceX {
  fieldX(argA: Int, argB: Int, argC: Int): Int
  fieldY(argA: Int, argB: Int, argC: Int): Int
  fieldZ(argA: Int, argB: Int, argC: Int): Int
}

interface InterfaceZ {
  fieldX(argA: Int, argB: Int, argC: Int): Int
  fieldY(argA: Int, argB: Int, argC: Int): Int
  fieldZ(argA: Int, argB: Int, argC: Int): Int
}

union UnionX = Bar | Foo

union UnionZ = Bar | Foo

enum EnumX {
  X
  Y
  Z
}

enum EnumZ {
  X
  Y
  Z
}

scalar ScalarX

scalar ScalarZ

input InputX {
  fieldX: String
  fieldY: String
  fieldZ: String
}

input InputZ {
  fieldX: String
  fieldY: String
  fieldZ: String
}
'''

        def doc = parseQuery(sdl)

        when:
        def newDoc = new AstSorter().sort(doc)
        then:
        printAst(newDoc) == expectedSDL
    }

    def "object literals gets sorted"() {
        def query = '''
            {
                field( arg : { z : "valz", x : { c : "valc" , a : "vala", b : "valb" }, y : "valy" } )
            }
        '''

        def expectedQuery = '''query {
  field(arg: {x : {a : "vala", b : "valb", c : "valc"}, y : "valy", z : "valz"})
}
'''

        def doc = parseQuery(query)

        when:
        def newDoc = new AstSorter().sort(doc)
        then:
        printAst(newDoc) == expectedQuery

    }
}

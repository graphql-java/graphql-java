package graphql.language

import spock.lang.Specification

class PrettyAstPrinterTest extends Specification {

    def "can print type with comments"() {
        given:
        def input = '''
# before description
""" Description """
# before def #1
# before def #2
type Query { # beginning of block
a: A b: B
        # end of block inside #1
# end of block inside #2
}               # end of block
'''

        def expected = '''# before description
"""
 Description 
"""
# before def #1
# before def #2
type Query { # beginning of block
  a: A
  b: B
  # end of block inside #1
  # end of block inside #2
} # end of block
'''
        when:
        def result = print(input)

        then:
        result == expected
    }


    def "can print type with no comments"() {
        given:
        def input = '''
""" Description """
type Query {
a: A b: B}
'''

        def expected = '''"""
 Description 
"""
type Query {
  a: A
  b: B
}
'''
        when:
        def result = print(input)
        then:
        result == expected
    }


    def "can print interface with comments"() {
        given:
        def input = '''
# before description
""" Description """
# before def #1
# before def #2
interface MyInterface { # beginning of block
a: A b: B} # end of block
'''

        def expected = '''# before description
"""
 Description 
"""
# before def #1
# before def #2
interface MyInterface { # beginning of block
  a: A
  b: B
} # end of block
'''
        when:
        def result = print(input)

        then:
        result == expected
    }


    def "can print interface with no comments"() {
        given:
        def input = '''
""" Description """
interface MyInterface {
a: A b: B}
'''

        def expected = '''"""
 Description 
"""
interface MyInterface {
  a: A
  b: B
}
'''
        when:
        def result = print(input)
        then:
        result == expected
    }


    def "can print fields with comments"() {
        given:
        def input = '''
# before type
type MyType { # beginning of block

# before field A #1

# before field A #2
""" Description fieldA """
# before field A #3
           a(arg1: String, arg2: String, arg3: String): A # after field A
           # before field B #1
b(arg1: String, arg2: String, arg3: String): B    # after field B
} # end of block
'''

        def expected = '''# before type
type MyType { # beginning of block
  # before field A #1
  # before field A #2
  """
   Description fieldA 
  """
  # before field A #3
  a(arg1: String, arg2: String, arg3: String): A # after field A
  # before field B #1
  b(arg1: String, arg2: String, arg3: String): B # after field B
} # end of block
'''
        when:
        def result = print(input)

        then:
        result == expected
    }


    def "can print field arguments with comments"() {
        given:
        def input = '''type MyType {
""" Description fieldA """
           a(arg1: String  # arg1 #1
           # arg2 #1
           """ Description arg2 """
           # arg2 #2
           arg2: String, arg3: String # arg3 #1
           # after all args
           ): A # after field A
}
'''

        def expected = '''type MyType {
  """
   Description fieldA 
  """
  a(
    arg1: String # arg1 #1
    # arg2 #1
    """
     Description arg2 
    """
    # arg2 #2
    arg2: String
    arg3: String # arg3 #1
    # after all args
  ): A # after field A
}
'''
        when:
        def result = print(input)

        then:
        result == expected
    }


    def "can print schema keeping document level comments"() {
        given:
        def input = '''   # start of document

# before Query def

type Query {a: A b: B}
type MyType {field(id: ID!): MyType! listField: [MyType!]!}
enum MyEnum {VALUE_1 VALUE_2}
# end of document #1
# end of document #2
'''

        def expected = """# start of document
# before Query def
type Query {
  a: A
  b: B
}

type MyType {
  field(id: ID!): MyType!
  listField: [MyType!]!
}

enum MyEnum {
  VALUE_1
  VALUE_2
}
# end of document #1
# end of document #2
"""

        when:
        def result = print(input)
        then:

        result == expected
    }


    def "can print comments between implements"() {
        given:
        def input = '''
type MyType implements A & 
# interface B #1
    # interface B #2
    B 
    & 
    # interface C
    C {a: A}
'''

        def expected = '''type MyType implements 
  A &
  # interface B #1
  # interface B #2
  B &
  # interface C
  C
 {
  a: A
}
'''

        when:
        def result = print(input)
        then:

        result == expected
    }


    def "can print type implementing interfaces with no comments"() {
        given:
        def input = '''
type MyType implements A & 
    B & 
    C 
    & D 
    & E { # trailing comment
    a: A}
'''

        def expected = '''type MyType implements A & B & C & D & E { # trailing comment
  a: A
}
'''

        when:
        def result = print(input)
        then:

        result == expected
    }


    def "can print enums without comments"() {
        given:
        def input = '''
enum MyEnum { 
        VALUE_1 
VALUE_2 VALUE_3
}
'''

        def expected = '''enum MyEnum {
  VALUE_1
  VALUE_2
  VALUE_3
}
'''

        when:
        def result = print(input)
        then:

        result == expected
    }


    def "can print comments in enums"() {
        given:
        def input = '''
# before def
enum MyEnum { # inside block #1
# before VALUE_1 #1
        """ VALUE_1 description """
        # before VALUE_1 #2
        VALUE_1 # after VALUE_1
VALUE_2
    #inside block #2
}
'''

        def expected = '''# before def
enum MyEnum { # inside block #1
  # before VALUE_1 #1
  """
   VALUE_1 description 
  """
  # before VALUE_1 #2
  VALUE_1 # after VALUE_1
  VALUE_2
  #inside block #2
}
'''

        when:
        def result = print(input)
        then:

        result == expected
    }


    def "can print comments in scalars"() {
        given:
        def input = '''
# before def #1
""" Description """
 # before def #2
scalar 
MyScalar # def trailing 
# after def
'''

        def expected = '''# before def #1
"""
 Description 
"""
# before def #2
scalar MyScalar # def trailing 
# after def
'''

        when:
        def result = print(input)
        then:

        result == expected
    }


    def "can print comments in directives"() {
        given:
        def input = '''
# before def #1
""" Description def """
 # before def #2
directive 
@myDirective( # inside block #1
    # arg1 #1
   """ Description arg1 """
   
    # arg1 #2
   arg1: String!      # arg1 trailing
   # arg2 #1
  arg2: ID arg3: String! # arg3 trailing
  # inside block #1
 ) on FIELD_DEFINITION # trailing def
# after def
'''

        def expected = '''# before def #1
"""
 Description def 
"""
# before def #2
directive @myDirective( # inside block #1
  # arg1 #1
  """
   Description arg1 
  """
  # arg1 #2
  arg1: String! # arg1 trailing
  # arg2 #1
  arg2: ID
  arg3: String! # arg3 trailing
  # inside block #1
) on FIELD_DEFINITION # trailing def
# after def
'''

        when:
        def result = print(input)
        then:

        result == expected
    }

    def "can print extended type with comments"() {
        given:
        def input = '''
# before description
extend type Query { # beginning of block
a: A b: B
        # end of block inside #1
# end of block inside #2
}               # end of block
'''

        def expected = '''# before description
extend type Query { # beginning of block
  a: A
  b: B
  # end of block inside #1
  # end of block inside #2
} # end of block
'''
        when:
        def result = print(input)

        then:
        result == expected
    }


    def "can print input type with comments"() {
        given:
        def input = '''
# before description
""" Description """
# before def #1
# before def #2
input MyInput { # beginning of block
a: A b: B
        # end of block inside #1
# end of block inside #2
}               # end of block
'''

        def expected = '''# before description
"""
 Description 
"""
# before def #1
# before def #2
input MyInput { # beginning of block
  a: A
  b: B
  # end of block inside #1
  # end of block inside #2
} # end of block
'''
        when:
        def result = print(input)

        then:
        result == expected
    }

    def "can use print with tab indent"() {
        given:
        def input = '''
type Query {
field(
# comment
a: A, b: B): Type
}
'''

        def expected = '''type Query {
\tfield(
\t\t# comment
\t\ta: A
\t\tb: B
\t): Type
}
'''
        when:
        def options = PrettyAstPrinter.PrettyPrinterOptions
                .builder()
                .indentType(PrettyAstPrinter.PrettyPrinterOptions.IndentType.TAB)
                .indentWith(1)
                .build()

        def result = PrettyAstPrinter.print(input, options)

        then:
        result == expected
    }

    private static String print(String input) {
        return PrettyAstPrinter.print(input, PrettyAstPrinter.PrettyPrinterOptions.defaultOptions())
    }
}

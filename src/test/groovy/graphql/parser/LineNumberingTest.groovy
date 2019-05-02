package graphql.parser

import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import spock.lang.Specification
import spock.lang.Unroll

/*
   See https://github.com/graphql-java/graphql-java/issues/1512
 */

class LineNumberingTest extends Specification {

    def "basic line numbering works as expected with string input"() {
        def query = '''query X {
   field1
   field2
   field3
   field4
}

fragment X on Book {
   frag1
}

'''

        when:
        def document = new Parser().parseDocument(query)
        def queryOp = document.getDefinitionsOfType(OperationDefinition.class)[0]
        then:
        queryOp.getSourceLocation().getLine() == 1

        when:
        def field1 = queryOp.getSelectionSet().getSelectionsOfType(Field.class)[0]
        then:
        field1.getSourceLocation().getLine() == 2

        when:
        def field2 = queryOp.getSelectionSet().getSelectionsOfType(Field.class)[1]
        then:
        field2.getSourceLocation().getLine() == 3

    }

    def "multi source line numbering works as expected"() {
        def queryBit1 = '''query X {
   field1
    field2  # note the extra space here
   field3
   field4
}'''
        def queryBit2 = '''fragment X on Book {
   frag1
    frag2 # note the extra space here
   frag2
}'''

        MultiSourceReader msr = MultiSourceReader.newMultiSourceReader()
                .string(queryBit1, "bit1")
                .string(queryBit2, "bit2")
                .build()

        when:
        def document = new Parser().parseDocument(msr)
        then:
        document.getSourceLocation().getLine() == 1
        document.getSourceLocation().getColumn() == 1
        document.getSourceLocation().getSourceName() == "bit1"

        when:
        def queryOp = document.getDefinitionsOfType(OperationDefinition.class)[0]
        then:
        queryOp.getSourceLocation().getLine() == 1
        queryOp.getSourceLocation().getColumn() == 1
        queryOp.getSourceLocation().getSourceName() == "bit1"

        when:
        def field1 = queryOp.getSelectionSet().getSelectionsOfType(Field.class)[0]
        then:
        field1.getSourceLocation().getLine() == 2
        field1.getSourceLocation().getColumn() == 4
        field1.getSourceLocation().getSourceName() == "bit1"

        when:
        def field2 = queryOp.getSelectionSet().getSelectionsOfType(Field.class)[1]
        then:
        field2.getSourceLocation().getLine() == 3
        field2.getSourceLocation().getColumn() == 5
        field2.getSourceLocation().getSourceName() == "bit1"

        when:
        def fragment = document.getDefinitionsOfType(FragmentDefinition.class)[0]
        then:
        fragment.getSourceLocation().getLine() == 1
        fragment.getSourceLocation().getColumn() == 2
        fragment.getSourceLocation().getSourceName() == "bit2"

        when:
        def fragField1 = fragment.getSelectionSet().getSelectionsOfType(Field.class)[0]
        then:
        fragField1.getSourceLocation().getLine() == 2
        fragField1.getSourceLocation().getColumn() == 4
        fragField1.getSourceLocation().getSourceName() == "bit2"

        when:
        def fragField2 = fragment.getSelectionSet().getSelectionsOfType(Field.class)[1]
        then:
        fragField2.getSourceLocation().getLine() == 3
        fragField2.getSourceLocation().getColumn() == 5
        fragField2.getSourceLocation().getSourceName() == "bit2"
    }

    @SuppressWarnings("GroovyVariableNotAssigned")
    @Unroll
    def "error is on the right line with string input"() {
        expect:
        def e
        try {
            new Parser().parseDocument(badQuery)
            assert false, "Should have barfed"
        } catch (InvalidSyntaxException ise) {
            e = ise
        }
        e.getLocation().getLine() == line
        e.getLocation().getColumn() == column
        e.getLocation().getSourceName() == null

        where:
        badQuery                                | line | column
        '''query X { field() field2'''          | 1    | 17
        '''query X { field()\nfield2'''         | 1    | 17
        '''query X { field\nfield2()\nfield3''' | 2    | 8
    }
}

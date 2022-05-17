package graphql.parser

import graphql.AssertException
import graphql.ExecutionInput
import graphql.TestUtil
import graphql.language.Argument
import graphql.language.ArrayValue
import graphql.language.AstComparator
import graphql.language.BooleanValue
import graphql.language.Description
import graphql.language.Directive
import graphql.language.DirectiveDefinition
import graphql.language.Document
import graphql.language.EnumTypeDefinition
import graphql.language.Field
import graphql.language.FloatValue
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.IgnoredChar
import graphql.language.IgnoredChars
import graphql.language.InlineFragment
import graphql.language.InputObjectTypeDefinition
import graphql.language.IntValue
import graphql.language.InterfaceTypeDefinition
import graphql.language.ListType
import graphql.language.Node
import graphql.language.NodeBuilder
import graphql.language.NonNullType
import graphql.language.NullValue
import graphql.language.ObjectField
import graphql.language.ObjectTypeDefinition
import graphql.language.ObjectValue
import graphql.language.OperationDefinition
import graphql.language.ScalarTypeDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.language.SourceLocation
import graphql.language.StringValue
import graphql.language.Type
import graphql.language.TypeName
import graphql.language.UnionTypeDefinition
import graphql.language.VariableDefinition
import graphql.language.VariableReference
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

class TypeParserTest extends Specification {




    def "can parse simple type"() {

        when:
        def result = TypeParser.parse(typeString)

        then:
        result.isEqualTo(outputType)

        where:
        typeString    | outputType
        "Foo"         | new TypeName("Foo")
        "String"      | new TypeName("String")
        "[String]"    | new ListType(new TypeName("String"))
        "Boolean!"    | new NonNullType(new TypeName("Boolean"))
        "[Int]!"      | new NonNullType(new ListType(new TypeName("Int")))
        "[[String!]]" | new ListType(new ListType(new NonNullType(new TypeName("String"))))
    }


    def "can handle invalid type"() {

        when:
        def result = TypeParser.parse(typeString)

        then:
        def e = thrown(AssertException)
        e.message.contains(typeString)

        where:
        typeString | _
//        "[String" | _
//        "Boolean !"    | _
//        "[Int!"      | _
        "[String]]" | _
    }

}

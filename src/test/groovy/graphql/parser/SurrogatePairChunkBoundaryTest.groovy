package graphql.parser

import graphql.language.Argument
import graphql.language.Document
import graphql.language.Field
import graphql.language.ObjectTypeDefinition
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.language.StringValue
import graphql.schema.idl.SchemaParser
import spock.lang.Specification
import spock.lang.Unroll

/**
 * ANTLR's {@link org.antlr.v4.runtime.CharStreams#fromReader(java.io.Reader)} reads a document in fixed
 * size chunks and hands each one to {@link org.antlr.v4.runtime.CodePointBuffer.Builder#append}. When a
 * surrogate pair (e.g. an emoji outside the Basic Multilingual Plane) is split across two of those chunks,
 * ANTLR mis-decodes it into two separate code points instead of one - see
 * <a href="https://github.com/antlr/antlr4/pull/4943">antlr/antlr4#4943</a>.
 * <p>
 * Parser.setupCharStream() avoids this by holding back a trailing unpaired high surrogate so it is carried
 * into the next chunk. This test proves it by placing an astral character at every offset around the chunk
 * boundary.
 */
class SurrogatePairChunkBoundaryTest extends Specification {

    // the emoji U+1F600 (😀), expressed as a Java surrogate pair
    static final String EMOJI = "😀"

    static String queryWithEmojiAt(int highSurrogateIndex) {
        def prefix = 'query { f(arg: "'
        def pad = highSurrogateIndex - prefix.length()
        assert pad >= 0
        return prefix + ('a' * pad) + EMOJI + '") }'
    }

    @Unroll
    def "an astral character surviving a document of length #docLength with the high surrogate at index #highSurrogateIndex"() {
        given:
        def query = queryWithEmojiAt(highSurrogateIndex)
        assert query.indexOf(0xD83D as int) == highSurrogateIndex

        when:
        Document viaString = Parser.parse(query)
        Document viaReader = Parser.parse(ParserEnvironment.newParserEnvironment().document(query).build())

        then:
        [viaString, viaReader].each { Document document ->
            def field = ((document.definitions[0] as OperationDefinition).selectionSet as SelectionSet).selections[0] as Field
            def argument = field.arguments[0] as Argument
            def value = argument.value as StringValue
            assert value.value == ('a' * (highSurrogateIndex - 'query { f(arg: "'.length())) + "😀"
        }

        where:
        highSurrogateIndex << (4085..4100)
        docLength = queryWithEmojiAt(highSurrogateIndex).length()
    }

    def "a document ending in an unpaired high surrogate is not silently dropped"() {
        given:
        // deliberately malformed: a lone high surrogate with nothing after it, inside a string that never closes.
        // this asserts today's (pre-existing) behaviour is preserved, not that new semantics are introduced.
        def query = 'query { f(arg: "' + ('a' * 4080) + "\uD83D"

        when:
        Parser.parse(query)

        then:
        thrown(InvalidSyntaxException)
    }

    def "an astral character in an SDL description survives a chunk boundary"() {
        given:
        def pad = 'a' * (4096 - '"""'.length())
        def sdl = '"""' + pad + EMOJI + '"""' + '\ntype Query { f: String }'

        when:
        def registry = new SchemaParser().parse(sdl)
        def objectType = registry.getType("Query", ObjectTypeDefinition).get()

        then:
        objectType.description.content == pad + "😀"
    }
}

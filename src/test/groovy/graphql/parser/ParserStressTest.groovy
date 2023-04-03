package graphql.parser

import graphql.ExecutionInput
import graphql.TestUtil
import graphql.language.Document
import graphql.parser.exceptions.ParseCancelledException
import graphql.parser.exceptions.ParseCancelledTooDeepException
import graphql.parser.exceptions.ParseCancelledTooManyCharsException
import spock.lang.Specification

import static graphql.parser.ParserEnvironment.newParserEnvironment

/**
 * Tests related to how the Parser can be stress tested
 */
class ParserStressTest extends Specification {
    static defaultOptions = ParserOptions.getDefaultParserOptions()
    static defaultOperationOptions = ParserOptions.getDefaultOperationParserOptions()
    static defaultSdlOptions = ParserOptions.getDefaultSdlParserOptions()

    void setup() {
        ParserOptions.setDefaultParserOptions(defaultOptions)
        ParserOptions.setDefaultOperationParserOptions(defaultOperationOptions)
        ParserOptions.setDefaultSdlParserOptions(defaultSdlOptions)
    }

    void cleanup() {
        ParserOptions.setDefaultParserOptions(defaultOptions)
        ParserOptions.setDefaultOperationParserOptions(defaultOperationOptions)
        ParserOptions.setDefaultSdlParserOptions(defaultSdlOptions)
    }


    def "a billion laughs attack will be prevented by default"() {
        def lol = "@lol" * 10000 // two tokens = 20000+ tokens
        def text = "query { f $lol }"
        when:
        Parser.parse(text)

        then:
        def e = thrown(ParseCancelledException)
        e.getMessage().contains("parsing has been cancelled")

        when: "integration test to prove it cancels by default"

        def sdl = """type Query { f : ID} """
        def graphQL = TestUtil.graphQL(sdl).build()
        def er = graphQL.execute(text)
        then:
        er.errors.size() == 1
        er.errors[0].message.contains("parsing has been cancelled")
    }

    def "a large whitespace laughs attack will be prevented by default"() {
        def spaces = " " * 300_000
        def text = "query { f $spaces }"
        when:
        Parser.parse(text)

        then:
        def e = thrown(ParseCancelledException)
        e.getMessage().contains("parsing has been cancelled")

        when: "integration test to prove it cancels by default"

        def sdl = """type Query { f : ID} """
        def graphQL = TestUtil.graphQL(sdl).build()
        def er = graphQL.execute(text)
        then:
        er.errors.size() == 1
        er.errors[0].message.contains("parsing has been cancelled")
    }

    def "they can shoot themselves if they want to with large documents"() {
        def lol = "@lol" * 10000 // two tokens = 20000+ tokens
        def text = "query { f $lol }"

        def options = ParserOptions.newParserOptions().maxTokens(30000).build()
        def parserEnvironment = newParserEnvironment().document(text).parserOptions(options).build()

        when:
        def doc = new Parser().parseDocument(parserEnvironment)

        then:
        doc != null
    }

    def "they can shoot themselves if they want to with large documents with lots of whitespace"() {
        def spaces = " " * 300_000
        def text = "query { f $spaces }"

        def options = ParserOptions.newParserOptions().maxWhitespaceTokens(Integer.MAX_VALUE).build()
        def parserEnvironment = newParserEnvironment().document(text).parserOptions(options).build()
        when:
        def doc = new Parser().parseDocument(parserEnvironment)

        then:
        doc != null
    }

    def "they can set their own listener into action"() {
        def queryText = "query { f(arg : 1) }"

        def count = 0
        def tokens = []
        ParsingListener listener = { count++; tokens.add(it.getText()) }
        def parserOptions = ParserOptions.newParserOptions().parsingListener(listener).build()
        def parserEnvironment = newParserEnvironment().document(queryText).parserOptions(parserOptions).build()

        when:
        def doc = new Parser().parseDocument(parserEnvironment)

        then:
        doc != null
        count == 9
        tokens == ["query", "{", "f", "(", "arg", ":", "1", ")", "}"]

        when: "integration test to prove it be supplied via EI"

        def sdl = """type Query { f(arg : Int) : ID} """
        def graphQL = TestUtil.graphQL(sdl).build()


        def context = [:]
        context.put(ParserOptions.class, parserOptions)
        def executionInput = ExecutionInput.newExecutionInput()
                .query(queryText)
                .graphQLContext(context).build()

        count = 0
        tokens = []
        def er = graphQL.execute(executionInput)
        then:
        er.errors.size() == 0
        count == 9
        tokens == ["query", "{", "f", "(", "arg", ":", "1", ")", "}"]

    }

    def "deep query stack overflows are prevented by limiting the depth of rules"() {
        String text = mkDeepQuery(10000)

        when:
        def parserEnvironment = newParserEnvironment().document(text).parserOptions(defaultOperationOptions).build()
        Parser.parse(parserEnvironment)

        then:
        thrown(ParseCancelledTooDeepException)
    }

    def "wide queries are prevented by max token counts"() {
        String text = mkWideQuery(10000)

        when:

        def parserEnvironment = newParserEnvironment().document(text).parserOptions(defaultOperationOptions).build()
        Parser.parse(parserEnvironment)

        then:
        thrown(ParseCancelledException) // too many tokens will catch this wide queries
    }

    def "large single token attack parse can be prevented"() {
        String text = "q" * 10_000_000
        text = "query " + text + " {f}"

        when:
        def parserEnvironment = newParserEnvironment().document(text).parserOptions(defaultOperationOptions).build()
        Parser.parse(parserEnvironment)

        then:
        thrown(ParseCancelledTooManyCharsException)
    }

    def "inside limits single token attack parse will be accepted"() {
        String text = "q" * 900_000
        text = "query " + text + " {f}"

        when:
        def parserEnvironment = newParserEnvironment().document(text).parserOptions(defaultOperationOptions).build()
        def document = Parser.parse(parserEnvironment)

        then:
        document != null // its parsed - its invalid of course but parsed
    }

    String mkDeepQuery(int howMany) {
        def field = 'f(a:"")'
        StringBuilder sb = new StringBuilder("query q{")
        for (int i = 0; i < howMany; i++) {
            sb.append(field)
            if (i < howMany - 1) {
                sb.append("{")
            }
        }
        for (int i = 0; i < howMany - 1; i++) {
            sb.append("}")
        }
        sb.append("}")
        return sb.toString()
    }

    String mkWideQuery(int howMany) {
        StringBuilder sb = new StringBuilder("query q{f(")
        for (int i = 0; i < howMany; i++) {
            sb.append('a:1,')
        }
        sb.append(")}")
        return sb.toString()
    }
}

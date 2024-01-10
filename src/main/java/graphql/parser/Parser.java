package graphql.parser;

import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.Document;
import graphql.language.Node;
import graphql.language.SourceLocation;
import graphql.language.Type;
import graphql.language.Value;
import graphql.parser.antlr.GraphqlBaseListener;
import graphql.parser.antlr.GraphqlLexer;
import graphql.parser.antlr.GraphqlParser;
import graphql.parser.exceptions.ParseCancelledException;
import graphql.parser.exceptions.ParseCancelledTooDeepException;
import graphql.parser.exceptions.ParseCancelledTooManyCharsException;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * This can parse graphql syntax, both Query syntax and Schema Definition Language (SDL) syntax, into an
 * Abstract Syntax Tree (AST) represented by a {@link Document}
 * <p>
 * You should not generally need to call this class as the {@link graphql.GraphQL} code sets this up for you
 * but if you are doing specific graphql utilities this class is essential.
 * <p>
 * Graphql syntax has a series of characters, such as spaces, new lines and commas that are not considered relevant
 * to the syntax.  However they can be captured and associated with the AST elements they belong to.
 * <p>
 * This costs more memory but for certain use cases (like editors) this maybe be useful.  We have chosen to no capture
 * ignored characters by default but you can turn this on, either per parse or statically for the whole JVM
 * via {@link ParserOptions#setDefaultParserOptions(ParserOptions)} ()}}
 *
 * @see graphql.language.IgnoredChar
 */
@PublicApi
public class Parser {

    @Internal
    public static final int CHANNEL_COMMENTS = 2;
    @Internal
    public static final int CHANNEL_WHITESPACE = 3;

    /**
     * Parses a string input into a graphql AST {@link Document}
     *
     * @param environment the parser environment to use
     *
     * @return an AST {@link Document}
     *
     * @throws InvalidSyntaxException if the document    is not valid graphql syntax
     */
    public static Document parse(ParserEnvironment environment) throws InvalidSyntaxException {
        return new Parser().parseDocument(environment);
    }

    /**
     * Parses a string input into a graphql AST {@link Document}
     *
     * @param input the input to parse
     *
     * @return an AST {@link Document}
     *
     * @throws InvalidSyntaxException if the input is not valid graphql syntax
     */
    public static Document parse(String input) throws InvalidSyntaxException {
        return new Parser().parseDocument(input);
    }


    /**
     * Parses a string input into a graphql AST {@link Value}
     *
     * @param input the input to parse
     *
     * @return an AST {@link Value}
     *
     * @throws InvalidSyntaxException if the input is not valid graphql syntax
     */
    public static Value<?> parseValue(String input) throws InvalidSyntaxException {
        return new Parser().parseValueImpl(input);
    }

    /**
     * Parses a string input into a graphql AST Type
     *
     * @param input the input to parse
     *
     * @return an AST {@link Type}
     *
     * @throws InvalidSyntaxException if the input is not valid graphql syntax
     */
    public static Type<?> parseType(String input) throws InvalidSyntaxException {
        return new Parser().parseTypeImpl(input);
    }

    /**
     * Parses document text into a graphql AST {@link Document}
     *
     * @param environment the parser environment to sue
     *
     * @return an AST {@link Document}
     *
     * @throws InvalidSyntaxException if the input is not valid graphql syntax
     */
    public Document parseDocument(ParserEnvironment environment) throws InvalidSyntaxException {
        return parseDocumentImpl(environment);
    }

    /**
     * Parses a string input into a graphql AST {@link Document}
     *
     * @param input the input to parse
     *
     * @return an AST {@link Document}
     *
     * @throws InvalidSyntaxException if the input is not valid graphql syntax
     */
    public Document parseDocument(String input) throws InvalidSyntaxException {
        return parseDocument(input, (ParserOptions) null);
    }

    /**
     * Parses reader  input into a graphql AST {@link Document}
     *
     * @param reader the reader input to parse
     *
     * @return an AST {@link Document}
     *
     * @throws InvalidSyntaxException if the input is not valid graphql syntax
     */
    public Document parseDocument(Reader reader) throws InvalidSyntaxException {
        ParserEnvironment parserEnvironment = ParserEnvironment.newParserEnvironment()
                .document(reader)
                .build();
        return parseDocumentImpl(parserEnvironment);
    }

    /**
     * Parses a string input into a graphql AST {@link Document}
     *
     * @param input      the input to parse
     * @param sourceName - the name to attribute to the input text in {@link SourceLocation#getSourceName()}
     *
     * @return an AST {@link Document}
     *
     * @throws InvalidSyntaxException if the input is not valid graphql syntax
     * @deprecated use {#{@link #parse(ParserEnvironment)}} instead
     */
    @Deprecated(since = "2022-08-31")
    public Document parseDocument(String input, String sourceName) throws InvalidSyntaxException {
        MultiSourceReader multiSourceReader = MultiSourceReader.newMultiSourceReader()
                .string(input, sourceName)
                .trackData(true)
                .build();
        return parseDocument(multiSourceReader);
    }

    /**
     * Parses a string input into a graphql AST {@link Document}
     *
     * @param input         the input to parse
     * @param parserOptions the parser options
     *
     * @return an AST {@link Document}
     *
     * @throws InvalidSyntaxException if the input is not valid graphql syntax
     * @deprecated use {#{@link #parse(ParserEnvironment)}} instead
     */
    @Deprecated(since = "2022-08-31")
    public Document parseDocument(String input, ParserOptions parserOptions) throws InvalidSyntaxException {
        MultiSourceReader multiSourceReader = MultiSourceReader.newMultiSourceReader()
                .string(input, null)
                .trackData(true)
                .build();
        return parseDocument(multiSourceReader, parserOptions);
    }

    /**
     * Parses reader  input into a graphql AST {@link Document}
     *
     * @param reader        the reader input to parse
     * @param parserOptions the parser options
     *
     * @return an AST {@link Document}
     *
     * @throws InvalidSyntaxException if the input is not valid graphql syntax
     * @deprecated use {#{@link #parse(ParserEnvironment)}} instead
     */
    @Deprecated(since = "2022-08-31")
    public Document parseDocument(Reader reader, ParserOptions parserOptions) throws InvalidSyntaxException {
        ParserEnvironment parserEnvironment = ParserEnvironment.newParserEnvironment()
                .document(reader)
                .parserOptions(parserOptions)
                .build();
        return parseDocumentImpl(parserEnvironment);
    }

    private Document parseDocumentImpl(ParserEnvironment environment) throws InvalidSyntaxException {
        BiFunction<GraphqlParser, GraphqlAntlrToLanguage, Object[]> nodeFunction = (parser, toLanguage) -> {
            GraphqlParser.DocumentContext documentContext = parser.document();
            Document doc = toLanguage.createDocument(documentContext);
            return new Object[]{documentContext, doc};
        };
        return (Document) parseImpl(environment, nodeFunction);
    }

    private Value<?> parseValueImpl(String input) throws InvalidSyntaxException {
        BiFunction<GraphqlParser, GraphqlAntlrToLanguage, Object[]> nodeFunction = (parser, toLanguage) -> {
            GraphqlParser.ValueContext documentContext = parser.value();
            Value<?> value = toLanguage.createValue(documentContext);
            return new Object[]{documentContext, value};
        };
        MultiSourceReader multiSourceReader = MultiSourceReader.newMultiSourceReader()
                .string(input, null)
                .trackData(true)
                .build();
        ParserEnvironment parserEnvironment = ParserEnvironment.newParserEnvironment().document(multiSourceReader).build();
        return (Value<?>) parseImpl(parserEnvironment, nodeFunction);
    }

    private Type<?> parseTypeImpl(String input) throws InvalidSyntaxException {
        BiFunction<GraphqlParser, GraphqlAntlrToLanguage, Object[]> nodeFunction = (parser, toLanguage) -> {
            final GraphqlParser.TypeContext documentContext = parser.type();
            Type<?> value = toLanguage.createType(documentContext);
            return new Object[]{documentContext, value};
        };
        MultiSourceReader multiSourceReader = MultiSourceReader.newMultiSourceReader()
                .string(input, null)
                .trackData(true)
                .build();

        ParserEnvironment parserEnvironment = ParserEnvironment.newParserEnvironment().document(multiSourceReader).build();
        return (Type<?>) parseImpl(parserEnvironment, nodeFunction);
    }

    private Node<?> parseImpl(ParserEnvironment environment, BiFunction<GraphqlParser, GraphqlAntlrToLanguage, Object[]> nodeFunction) throws InvalidSyntaxException {
        // default in the parser options if they are not set
        ParserOptions parserOptions = environment.getParserOptions();
        parserOptions = Optional.ofNullable(parserOptions).orElse(ParserOptions.getDefaultParserOptions());

        MultiSourceReader multiSourceReader = setupMultiSourceReader(environment, parserOptions);

        SafeTokenReader safeTokenReader = setupSafeTokenReader(environment, parserOptions, multiSourceReader);

        CodePointCharStream charStream = setupCharStream(safeTokenReader);

        GraphqlLexer lexer = setupGraphqlLexer(environment, multiSourceReader, charStream);

        // this lexer wrapper allows us to stop lexing when too many tokens are in place.  This prevents DOS attacks.
        SafeTokenSource safeTokenSource = getSafeTokenSource(environment, parserOptions, multiSourceReader, lexer);

        CommonTokenStream tokens = new CommonTokenStream(safeTokenSource);

        GraphqlParser parser = new GraphqlParser(tokens);
        parser.removeErrorListeners();
        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);

        ExtendedBailStrategy bailStrategy = new ExtendedBailStrategy(multiSourceReader, environment);
        parser.setErrorHandler(bailStrategy);

        // preserve old protected call semantics - remove at some point
        GraphqlAntlrToLanguage toLanguage = getAntlrToLanguage(tokens, multiSourceReader, environment);

        setupParserListener(environment, multiSourceReader, parser, toLanguage);


        //
        // parsing starts ...... now!
        //
        Object[] contextAndNode = nodeFunction.apply(parser, toLanguage);
        ParserRuleContext parserRuleContext = (ParserRuleContext) contextAndNode[0];
        Node<?> node = (Node<?>) contextAndNode[1];

        Token stop = parserRuleContext.getStop();
        List<Token> allTokens = tokens.getTokens();
        if (stop != null && allTokens != null && !allTokens.isEmpty()) {
            Token last = allTokens.get(allTokens.size() - 1);
            //
            // do we have more tokens in the stream than we consumed in the parse?
            // if yes then it's invalid.  We make sure it's the same channel
            boolean notEOF = last.getType() != Token.EOF;
            boolean lastGreaterThanDocument = last.getTokenIndex() > stop.getTokenIndex();
            boolean sameChannel = last.getChannel() == stop.getChannel();
            if (notEOF && lastGreaterThanDocument && sameChannel) {
                throw bailStrategy.mkMoreTokensException(last);
            }
        }
        return node;
    }

    private static MultiSourceReader setupMultiSourceReader(ParserEnvironment environment, ParserOptions parserOptions) {
        MultiSourceReader multiSourceReader;
        Reader reader = environment.getDocument();
        if (reader instanceof MultiSourceReader) {
            multiSourceReader = (MultiSourceReader) reader;
        } else {
            multiSourceReader = MultiSourceReader.newMultiSourceReader()
                    .reader(reader, null)
                    .trackData(parserOptions.isReaderTrackData())
                    .build();
        }
        return multiSourceReader;
    }

    @NotNull
    private static SafeTokenReader setupSafeTokenReader(ParserEnvironment environment, ParserOptions parserOptions, MultiSourceReader multiSourceReader) {
        int maxCharacters = parserOptions.getMaxCharacters();
        Consumer<Integer> onTooManyCharacters = it -> {
            throw new ParseCancelledTooManyCharsException(environment.getI18N(), maxCharacters);
        };
        return new SafeTokenReader(multiSourceReader, maxCharacters, onTooManyCharacters);
    }

    @NotNull
    private static CodePointCharStream setupCharStream(SafeTokenReader safeTokenReader) {
        CodePointCharStream charStream;
        try {
            charStream = CharStreams.fromReader(safeTokenReader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return charStream;
    }

    @NotNull
    private static GraphqlLexer setupGraphqlLexer(ParserEnvironment environment, MultiSourceReader multiSourceReader, CodePointCharStream charStream) {
        GraphqlLexer lexer = new GraphqlLexer(charStream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String antlerMsg, RecognitionException e) {
                SourceLocation sourceLocation = AntlrHelper.createSourceLocation(multiSourceReader, line, charPositionInLine);
                String preview = AntlrHelper.createPreview(multiSourceReader, line);
                String msgKey;
                List<Object> args;
                if (antlerMsg == null) {
                    msgKey = "InvalidSyntax.noMessage";
                    args = ImmutableList.of(sourceLocation.getLine(), sourceLocation.getColumn());
                } else {
                    msgKey = "InvalidSyntax.full";
                    args = ImmutableList.of(antlerMsg, sourceLocation.getLine(), sourceLocation.getColumn());
                }
                String msg = environment.getI18N().msg(msgKey, args);
                throw new InvalidSyntaxException(msg, sourceLocation, null, preview, null);
            }
        });
        return lexer;
    }

    @NotNull
    private SafeTokenSource getSafeTokenSource(ParserEnvironment environment, ParserOptions parserOptions, MultiSourceReader multiSourceReader, GraphqlLexer lexer) {
        int maxTokens = parserOptions.getMaxTokens();
        int maxWhitespaceTokens = parserOptions.getMaxWhitespaceTokens();
        BiConsumer<Integer, Token> onTooManyTokens = (maxTokenCount, token) -> throwIfTokenProblems(
                environment,
                token,
                maxTokenCount,
                multiSourceReader,
                ParseCancelledException.class);
        return new SafeTokenSource(lexer, maxTokens, maxWhitespaceTokens, onTooManyTokens);
    }

    private void setupParserListener(ParserEnvironment environment, MultiSourceReader multiSourceReader, GraphqlParser parser, GraphqlAntlrToLanguage toLanguage) {
        ParserOptions parserOptions = toLanguage.getParserOptions();
        ParsingListener parsingListener = parserOptions.getParsingListener();
        int maxTokens = parserOptions.getMaxTokens();
        int maxRuleDepth = parserOptions.getMaxRuleDepth();
        // prevent a billion laugh attacks by restricting how many tokens we allow
        ParseTreeListener listener = new GraphqlBaseListener() {
            int count = 0;
            int depth = 0;


            @Override
            public void enterEveryRule(ParserRuleContext ctx) {
                depth++;
                if (depth > maxRuleDepth) {
                    throwIfTokenProblems(
                            environment,
                            ctx.getStart(),
                            maxRuleDepth,
                            multiSourceReader,
                            ParseCancelledTooDeepException.class
                    );
                }
            }

            @Override
            public void exitEveryRule(ParserRuleContext ctx) {
                depth--;
            }

            @Override
            public void visitTerminal(TerminalNode node) {

                final Token token = node.getSymbol();
                parsingListener.onToken(new ParsingListener.Token() {
                    @Override
                    public String getText() {
                        return token == null ? null : token.getText();
                    }

                    @Override
                    public int getLine() {
                        return token == null ? -1 : token.getLine();
                    }

                    @Override
                    public int getCharPositionInLine() {
                        return token == null ? -1 : token.getCharPositionInLine();
                    }
                });

                count++;
                if (count > maxTokens) {
                    throwIfTokenProblems(
                            environment,
                            token,
                            maxTokens,
                            multiSourceReader,
                            ParseCancelledException.class
                    );
                }
            }
        };
        parser.addParseListener(listener);
    }

    private void throwIfTokenProblems(ParserEnvironment environment, Token token, int maxLimit, MultiSourceReader multiSourceReader, Class<? extends InvalidSyntaxException> targetException) throws ParseCancelledException {
        String tokenType = "grammar";
        SourceLocation sourceLocation = null;
        String offendingToken = null;
        if (token != null) {
            int channel = token.getChannel();
            tokenType = channel == CHANNEL_WHITESPACE ? "whitespace" : (channel == CHANNEL_COMMENTS ? "comments" : "grammar");

            offendingToken = token.getText();
            sourceLocation = AntlrHelper.createSourceLocation(multiSourceReader, token.getLine(), token.getCharPositionInLine());
        }
        if (targetException.equals(ParseCancelledTooDeepException.class)) {
            throw new ParseCancelledTooDeepException(environment.getI18N(), sourceLocation, offendingToken, maxLimit, tokenType);
        }
        throw new ParseCancelledException(environment.getI18N(), sourceLocation, offendingToken, maxLimit, tokenType);
    }

    /**
     * Allows you to override the ANTLR to AST code.
     *
     * @param tokens            the token stream
     * @param multiSourceReader the source of the query document
     * @param environment       the parser environment
     *
     * @return a new GraphqlAntlrToLanguage instance
     */
    protected GraphqlAntlrToLanguage getAntlrToLanguage(CommonTokenStream tokens, MultiSourceReader multiSourceReader, ParserEnvironment environment) {
        return new GraphqlAntlrToLanguage(tokens, multiSourceReader, environment.getParserOptions(), environment.getI18N(), null);
    }
}

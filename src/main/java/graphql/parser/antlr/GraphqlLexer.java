// Generated from /Users/andi/dev/projects/graphql-java/src/main/grammar/Graphql.g4 by ANTLR 4.5.1
package graphql.parser.antlr;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.LexerATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class GraphqlLexer extends Lexer {
    static {
        RuntimeMetaData.checkVersion("4.5.1", RuntimeMetaData.VERSION);
    }

    protected static final DFA[] _decisionToDFA;
    protected static final PredictionContextCache _sharedContextCache =
            new PredictionContextCache();
    public static final int
            T__0 = 1, T__1 = 2, T__2 = 3, T__3 = 4, T__4 = 5, T__5 = 6, T__6 = 7, T__7 = 8, T__8 = 9,
            T__9 = 10, T__10 = 11, T__11 = 12, T__12 = 13, T__13 = 14, T__14 = 15, T__15 = 16, BooleanValue = 17,
            NAME = 18, IntValue = 19, FloatValue = 20, Sign = 21, IntegerPart = 22, NonZeroDigit = 23,
            ExponentPart = 24, Digit = 25, StringValue = 26, Ignored = 27;
    public static String[] modeNames = {
            "DEFAULT_MODE"
    };

    public static final String[] ruleNames = {
            "T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8",
            "T__9", "T__10", "T__11", "T__12", "T__13", "T__14", "T__15", "BooleanValue",
            "NAME", "IntValue", "FloatValue", "Sign", "IntegerPart", "NonZeroDigit",
            "ExponentPart", "Digit", "StringValue", "EscapedChar", "Unicode", "Hex",
            "Ignored", "Comment", "LineTerminator", "Whitspace", "Comma"
    };

    private static final String[] _LITERAL_NAMES = {
            null, "'query'", "'mutation'", "'('", "')'", "':'", "'$'", "'='", "'{'",
            "'}'", "'...'", "'on'", "'fragment'", "'['", "']'", "'@'", "'!'", null,
            null, null, null, "'-'"
    };
    private static final String[] _SYMBOLIC_NAMES = {
            null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, "BooleanValue", "NAME", "IntValue", "FloatValue",
            "Sign", "IntegerPart", "NonZeroDigit", "ExponentPart", "Digit", "StringValue",
            "Ignored"
    };
    public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

    /**
     * @deprecated Use {@link #VOCABULARY} instead.
     */
    @Deprecated
    public static final String[] tokenNames;

    static {
        tokenNames = new String[_SYMBOLIC_NAMES.length];
        for (int i = 0; i < tokenNames.length; i++) {
            tokenNames[i] = VOCABULARY.getLiteralName(i);
            if (tokenNames[i] == null) {
                tokenNames[i] = VOCABULARY.getSymbolicName(i);
            }

            if (tokenNames[i] == null) {
                tokenNames[i] = "<INVALID>";
            }
        }
    }

    @Override
    @Deprecated
    public String[] getTokenNames() {
        return tokenNames;
    }

    @Override

    public Vocabulary getVocabulary() {
        return VOCABULARY;
    }


    public GraphqlLexer(CharStream input) {
        super(input);
        _interp = new LexerATNSimulator(this, _ATN, _decisionToDFA, _sharedContextCache);
    }

    @Override
    public String getGrammarFileName() {
        return "Graphql.g4";
    }

    @Override
    public String[] getRuleNames() {
        return ruleNames;
    }

    @Override
    public String getSerializedATN() {
        return _serializedATN;
    }

    @Override
    public String[] getModeNames() {
        return modeNames;
    }

    @Override
    public ATN getATN() {
        return _ATN;
    }

    public static final String _serializedATN =
            "\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\2\35\u00e7\b\1\4\2" +
                    "\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4" +
                    "\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22" +
                    "\t\22\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31" +
                    "\t\31\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t" +
                    " \4!\t!\4\"\t\"\4#\t#\3\2\3\2\3\2\3\2\3\2\3\2\3\3\3\3\3\3\3\3\3\3\3\3" +
                    "\3\3\3\3\3\3\3\4\3\4\3\5\3\5\3\6\3\6\3\7\3\7\3\b\3\b\3\t\3\t\3\n\3\n\3" +
                    "\13\3\13\3\13\3\13\3\f\3\f\3\f\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\16" +
                    "\3\16\3\17\3\17\3\20\3\20\3\21\3\21\3\22\3\22\3\22\3\22\3\22\3\22\3\22" +
                    "\3\22\3\22\5\22\u0086\n\22\3\23\3\23\7\23\u008a\n\23\f\23\16\23\u008d" +
                    "\13\23\3\24\5\24\u0090\n\24\3\24\3\24\3\25\5\25\u0095\n\25\3\25\3\25\3" +
                    "\25\6\25\u009a\n\25\r\25\16\25\u009b\5\25\u009e\n\25\3\25\5\25\u00a1\n" +
                    "\25\3\26\3\26\3\27\3\27\3\27\3\27\6\27\u00a9\n\27\r\27\16\27\u00aa\5\27" +
                    "\u00ad\n\27\3\30\3\30\3\31\3\31\5\31\u00b3\n\31\3\31\6\31\u00b6\n\31\r" +
                    "\31\16\31\u00b7\3\32\3\32\3\33\3\33\3\33\7\33\u00bf\n\33\f\33\16\33\u00c2" +
                    "\13\33\3\33\3\33\3\34\3\34\3\34\5\34\u00c9\n\34\3\35\3\35\3\35\3\35\3" +
                    "\35\3\35\3\36\3\36\3\37\3\37\3\37\3\37\5\37\u00d7\n\37\3\37\3\37\3 \3" +
                    " \7 \u00dd\n \f \16 \u00e0\13 \3!\3!\3\"\3\"\3#\3#\2\2$\3\3\5\4\7\5\t" +
                    "\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33\17\35\20\37\21!\22#\23" +
                    "%\24\'\25)\26+\27-\30/\31\61\32\63\33\65\34\67\29\2;\2=\35?\2A\2C\2E\2" +
                    "\3\2\n\5\2C\\aac|\6\2\62;C\\aac|\4\2GGgg\7\2\f\f\17\17$$^^\u202a\u202b" +
                    "\n\2$$\61\61^^ddhhppttvv\5\2\62;CHch\5\2\f\f\17\17\u202a\u202b\6\2\13" +
                    "\13\r\16\"\"\u00a2\u00a2\u00f2\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t" +
                    "\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2\23\3\2\2" +
                    "\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2\2\2\2\35\3\2\2\2\2" +
                    "\37\3\2\2\2\2!\3\2\2\2\2#\3\2\2\2\2%\3\2\2\2\2\'\3\2\2\2\2)\3\2\2\2\2" +
                    "+\3\2\2\2\2-\3\2\2\2\2/\3\2\2\2\2\61\3\2\2\2\2\63\3\2\2\2\2\65\3\2\2\2" +
                    "\2=\3\2\2\2\3G\3\2\2\2\5M\3\2\2\2\7V\3\2\2\2\tX\3\2\2\2\13Z\3\2\2\2\r" +
                    "\\\3\2\2\2\17^\3\2\2\2\21`\3\2\2\2\23b\3\2\2\2\25d\3\2\2\2\27h\3\2\2\2" +
                    "\31k\3\2\2\2\33t\3\2\2\2\35v\3\2\2\2\37x\3\2\2\2!z\3\2\2\2#\u0085\3\2" +
                    "\2\2%\u0087\3\2\2\2\'\u008f\3\2\2\2)\u0094\3\2\2\2+\u00a2\3\2\2\2-\u00ac" +
                    "\3\2\2\2/\u00ae\3\2\2\2\61\u00b0\3\2\2\2\63\u00b9\3\2\2\2\65\u00bb\3\2" +
                    "\2\2\67\u00c5\3\2\2\29\u00ca\3\2\2\2;\u00d0\3\2\2\2=\u00d6\3\2\2\2?\u00da" +
                    "\3\2\2\2A\u00e1\3\2\2\2C\u00e3\3\2\2\2E\u00e5\3\2\2\2GH\7s\2\2HI\7w\2" +
                    "\2IJ\7g\2\2JK\7t\2\2KL\7{\2\2L\4\3\2\2\2MN\7o\2\2NO\7w\2\2OP\7v\2\2PQ" +
                    "\7c\2\2QR\7v\2\2RS\7k\2\2ST\7q\2\2TU\7p\2\2U\6\3\2\2\2VW\7*\2\2W\b\3\2" +
                    "\2\2XY\7+\2\2Y\n\3\2\2\2Z[\7<\2\2[\f\3\2\2\2\\]\7&\2\2]\16\3\2\2\2^_\7" +
                    "?\2\2_\20\3\2\2\2`a\7}\2\2a\22\3\2\2\2bc\7\177\2\2c\24\3\2\2\2de\7\60" +
                    "\2\2ef\7\60\2\2fg\7\60\2\2g\26\3\2\2\2hi\7q\2\2ij\7p\2\2j\30\3\2\2\2k" +
                    "l\7h\2\2lm\7t\2\2mn\7c\2\2no\7i\2\2op\7o\2\2pq\7g\2\2qr\7p\2\2rs\7v\2" +
                    "\2s\32\3\2\2\2tu\7]\2\2u\34\3\2\2\2vw\7_\2\2w\36\3\2\2\2xy\7B\2\2y \3" +
                    "\2\2\2z{\7#\2\2{\"\3\2\2\2|}\7v\2\2}~\7t\2\2~\177\7w\2\2\177\u0086\7g" +
                    "\2\2\u0080\u0081\7h\2\2\u0081\u0082\7c\2\2\u0082\u0083\7n\2\2\u0083\u0084" +
                    "\7u\2\2\u0084\u0086\7g\2\2\u0085|\3\2\2\2\u0085\u0080\3\2\2\2\u0086$\3" +
                    "\2\2\2\u0087\u008b\t\2\2\2\u0088\u008a\t\3\2\2\u0089\u0088\3\2\2\2\u008a" +
                    "\u008d\3\2\2\2\u008b\u0089\3\2\2\2\u008b\u008c\3\2\2\2\u008c&\3\2\2\2" +
                    "\u008d\u008b\3\2\2\2\u008e\u0090\5+\26\2\u008f\u008e\3\2\2\2\u008f\u0090" +
                    "\3\2\2\2\u0090\u0091\3\2\2\2\u0091\u0092\5-\27\2\u0092(\3\2\2\2\u0093" +
                    "\u0095\5+\26\2\u0094\u0093\3\2\2\2\u0094\u0095\3\2\2\2\u0095\u0096\3\2" +
                    "\2\2\u0096\u009d\5-\27\2\u0097\u0099\7\60\2\2\u0098\u009a\5\63\32\2\u0099" +
                    "\u0098\3\2\2\2\u009a\u009b\3\2\2\2\u009b\u0099\3\2\2\2\u009b\u009c\3\2" +
                    "\2\2\u009c\u009e\3\2\2\2\u009d\u0097\3\2\2\2\u009d\u009e\3\2\2\2\u009e" +
                    "\u00a0\3\2\2\2\u009f\u00a1\5\61\31\2\u00a0\u009f\3\2\2\2\u00a0\u00a1\3" +
                    "\2\2\2\u00a1*\3\2\2\2\u00a2\u00a3\7/\2\2\u00a3,\3\2\2\2\u00a4\u00ad\7" +
                    "\62\2\2\u00a5\u00ad\5/\30\2\u00a6\u00a8\5/\30\2\u00a7\u00a9\5\63\32\2" +
                    "\u00a8\u00a7\3\2\2\2\u00a9\u00aa\3\2\2\2\u00aa\u00a8\3\2\2\2\u00aa\u00ab" +
                    "\3\2\2\2\u00ab\u00ad\3\2\2\2\u00ac\u00a4\3\2\2\2\u00ac\u00a5\3\2\2\2\u00ac" +
                    "\u00a6\3\2\2\2\u00ad.\3\2\2\2\u00ae\u00af\4\63;\2\u00af\60\3\2\2\2\u00b0" +
                    "\u00b2\t\4\2\2\u00b1\u00b3\5+\26\2\u00b2\u00b1\3\2\2\2\u00b2\u00b3\3\2" +
                    "\2\2\u00b3\u00b5\3\2\2\2\u00b4\u00b6\5\63\32\2\u00b5\u00b4\3\2\2\2\u00b6" +
                    "\u00b7\3\2\2\2\u00b7\u00b5\3\2\2\2\u00b7\u00b8\3\2\2\2\u00b8\62\3\2\2" +
                    "\2\u00b9\u00ba\4\62;\2\u00ba\64\3\2\2\2\u00bb\u00c0\7$\2\2\u00bc\u00bf" +
                    "\n\5\2\2\u00bd\u00bf\5\67\34\2\u00be\u00bc\3\2\2\2\u00be\u00bd\3\2\2\2" +
                    "\u00bf\u00c2\3\2\2\2\u00c0\u00be\3\2\2\2\u00c0\u00c1\3\2\2\2\u00c1\u00c3" +
                    "\3\2\2\2\u00c2\u00c0\3\2\2\2\u00c3\u00c4\7$\2\2\u00c4\66\3\2\2\2\u00c5" +
                    "\u00c8\7^\2\2\u00c6\u00c9\t\6\2\2\u00c7\u00c9\59\35\2\u00c8\u00c6\3\2" +
                    "\2\2\u00c8\u00c7\3\2\2\2\u00c98\3\2\2\2\u00ca\u00cb\7w\2\2\u00cb\u00cc" +
                    "\5;\36\2\u00cc\u00cd\5;\36\2\u00cd\u00ce\5;\36\2\u00ce\u00cf\5;\36\2\u00cf" +
                    ":\3\2\2\2\u00d0\u00d1\t\7\2\2\u00d1<\3\2\2\2\u00d2\u00d7\5C\"\2\u00d3" +
                    "\u00d7\5E#\2\u00d4\u00d7\5A!\2\u00d5\u00d7\5? \2\u00d6\u00d2\3\2\2\2\u00d6" +
                    "\u00d3\3\2\2\2\u00d6\u00d4\3\2\2\2\u00d6\u00d5\3\2\2\2\u00d7\u00d8\3\2" +
                    "\2\2\u00d8\u00d9\b\37\2\2\u00d9>\3\2\2\2\u00da\u00de\7%\2\2\u00db\u00dd" +
                    "\n\b\2\2\u00dc\u00db\3\2\2\2\u00dd\u00e0\3\2\2\2\u00de\u00dc\3\2\2\2\u00de" +
                    "\u00df\3\2\2\2\u00df@\3\2\2\2\u00e0\u00de\3\2\2\2\u00e1\u00e2\t\b\2\2" +
                    "\u00e2B\3\2\2\2\u00e3\u00e4\t\t\2\2\u00e4D\3\2\2\2\u00e5\u00e6\7.\2\2" +
                    "\u00e6F\3\2\2\2\23\2\u0085\u008b\u008f\u0094\u009b\u009d\u00a0\u00aa\u00ac" +
                    "\u00b2\u00b7\u00be\u00c0\u00c8\u00d6\u00de\3\b\2\2";
    public static final ATN _ATN =
            new ATNDeserializer().deserialize(_serializedATN.toCharArray());

    static {
        _decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
        for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
            _decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
        }
    }
}
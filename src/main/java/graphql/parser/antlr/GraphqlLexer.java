// Generated from /Users/andi/dev/projects/graphql-java/src/main/grammar/Graphql.g4 by ANTLR 4.5
package graphql.parser.antlr;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class GraphqlLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.5", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, BooleanValue=17, 
		NAME=18, IntValue=19, FloatValue=20, Sign=21, IntegerPart=22, NonZeroDigit=23, 
		ExponentPart=24, Digit=25, StringValue=26, StringCharacter=27, WS=28;
	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	public static final String[] ruleNames = {
		"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
		"T__9", "T__10", "T__11", "T__12", "T__13", "T__14", "T__15", "BooleanValue", 
		"NAME", "IntValue", "FloatValue", "Sign", "IntegerPart", "NonZeroDigit", 
		"ExponentPart", "Digit", "StringValue", "StringCharacter", "WS"
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
		"StringCharacter", "WS"
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
	@NotNull
	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}


	public GraphqlLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "Graphql.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\2\36\u00be\b\1\4\2"+
		"\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4"+
		"\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22"+
		"\t\22\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31"+
		"\t\31\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\3\2\3\2\3\2\3\2\3\2\3\2"+
		"\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\4\3\4\3\5\3\5\3\6\3\6\3\7\3\7\3"+
		"\b\3\b\3\t\3\t\3\n\3\n\3\13\3\13\3\13\3\13\3\f\3\f\3\f\3\r\3\r\3\r\3\r"+
		"\3\r\3\r\3\r\3\r\3\r\3\16\3\16\3\17\3\17\3\20\3\20\3\21\3\21\3\22\3\22"+
		"\3\22\3\22\3\22\3\22\3\22\3\22\3\22\5\22z\n\22\3\23\3\23\7\23~\n\23\f"+
		"\23\16\23\u0081\13\23\3\24\5\24\u0084\n\24\3\24\3\24\3\25\5\25\u0089\n"+
		"\25\3\25\3\25\3\25\6\25\u008e\n\25\r\25\16\25\u008f\3\25\5\25\u0093\n"+
		"\25\3\26\3\26\3\27\3\27\3\27\3\27\6\27\u009b\n\27\r\27\16\27\u009c\5\27"+
		"\u009f\n\27\3\30\3\30\3\31\3\31\5\31\u00a5\n\31\3\31\6\31\u00a8\n\31\r"+
		"\31\16\31\u00a9\3\32\3\32\3\33\3\33\6\33\u00b0\n\33\r\33\16\33\u00b1\3"+
		"\33\3\33\3\34\3\34\3\35\6\35\u00b9\n\35\r\35\16\35\u00ba\3\35\3\35\2\2"+
		"\36\3\3\5\4\7\5\t\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33\17\35"+
		"\20\37\21!\22#\23%\24\'\25)\26+\27-\30/\31\61\32\63\33\65\34\67\359\36"+
		"\3\2\6\5\2C\\aac|\6\2\62;C\\aac|\5\2\62;C\\c|\5\2\13\f\17\17\"\"\u00ca"+
		"\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2"+
		"\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2"+
		"\2\31\3\2\2\2\2\33\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2"+
		"\2\2\2%\3\2\2\2\2\'\3\2\2\2\2)\3\2\2\2\2+\3\2\2\2\2-\3\2\2\2\2/\3\2\2"+
		"\2\2\61\3\2\2\2\2\63\3\2\2\2\2\65\3\2\2\2\2\67\3\2\2\2\29\3\2\2\2\3;\3"+
		"\2\2\2\5A\3\2\2\2\7J\3\2\2\2\tL\3\2\2\2\13N\3\2\2\2\rP\3\2\2\2\17R\3\2"+
		"\2\2\21T\3\2\2\2\23V\3\2\2\2\25X\3\2\2\2\27\\\3\2\2\2\31_\3\2\2\2\33h"+
		"\3\2\2\2\35j\3\2\2\2\37l\3\2\2\2!n\3\2\2\2#y\3\2\2\2%{\3\2\2\2\'\u0083"+
		"\3\2\2\2)\u0088\3\2\2\2+\u0094\3\2\2\2-\u009e\3\2\2\2/\u00a0\3\2\2\2\61"+
		"\u00a2\3\2\2\2\63\u00ab\3\2\2\2\65\u00ad\3\2\2\2\67\u00b5\3\2\2\29\u00b8"+
		"\3\2\2\2;<\7s\2\2<=\7w\2\2=>\7g\2\2>?\7t\2\2?@\7{\2\2@\4\3\2\2\2AB\7o"+
		"\2\2BC\7w\2\2CD\7v\2\2DE\7c\2\2EF\7v\2\2FG\7k\2\2GH\7q\2\2HI\7p\2\2I\6"+
		"\3\2\2\2JK\7*\2\2K\b\3\2\2\2LM\7+\2\2M\n\3\2\2\2NO\7<\2\2O\f\3\2\2\2P"+
		"Q\7&\2\2Q\16\3\2\2\2RS\7?\2\2S\20\3\2\2\2TU\7}\2\2U\22\3\2\2\2VW\7\177"+
		"\2\2W\24\3\2\2\2XY\7\60\2\2YZ\7\60\2\2Z[\7\60\2\2[\26\3\2\2\2\\]\7q\2"+
		"\2]^\7p\2\2^\30\3\2\2\2_`\7h\2\2`a\7t\2\2ab\7c\2\2bc\7i\2\2cd\7o\2\2d"+
		"e\7g\2\2ef\7p\2\2fg\7v\2\2g\32\3\2\2\2hi\7]\2\2i\34\3\2\2\2jk\7_\2\2k"+
		"\36\3\2\2\2lm\7B\2\2m \3\2\2\2no\7#\2\2o\"\3\2\2\2pq\7v\2\2qr\7t\2\2r"+
		"s\7w\2\2sz\7g\2\2tu\7h\2\2uv\7c\2\2vw\7n\2\2wx\7u\2\2xz\7g\2\2yp\3\2\2"+
		"\2yt\3\2\2\2z$\3\2\2\2{\177\t\2\2\2|~\t\3\2\2}|\3\2\2\2~\u0081\3\2\2\2"+
		"\177}\3\2\2\2\177\u0080\3\2\2\2\u0080&\3\2\2\2\u0081\177\3\2\2\2\u0082"+
		"\u0084\5+\26\2\u0083\u0082\3\2\2\2\u0083\u0084\3\2\2\2\u0084\u0085\3\2"+
		"\2\2\u0085\u0086\5-\27\2\u0086(\3\2\2\2\u0087\u0089\5+\26\2\u0088\u0087"+
		"\3\2\2\2\u0088\u0089\3\2\2\2\u0089\u008a\3\2\2\2\u008a\u008b\5-\27\2\u008b"+
		"\u008d\7\60\2\2\u008c\u008e\5\63\32\2\u008d\u008c\3\2\2\2\u008e\u008f"+
		"\3\2\2\2\u008f\u008d\3\2\2\2\u008f\u0090\3\2\2\2\u0090\u0092\3\2\2\2\u0091"+
		"\u0093\5\61\31\2\u0092\u0091\3\2\2\2\u0092\u0093\3\2\2\2\u0093*\3\2\2"+
		"\2\u0094\u0095\7/\2\2\u0095,\3\2\2\2\u0096\u009f\7\62\2\2\u0097\u009f"+
		"\5/\30\2\u0098\u009a\5/\30\2\u0099\u009b\5\63\32\2\u009a\u0099\3\2\2\2"+
		"\u009b\u009c\3\2\2\2\u009c\u009a\3\2\2\2\u009c\u009d\3\2\2\2\u009d\u009f"+
		"\3\2\2\2\u009e\u0096\3\2\2\2\u009e\u0097\3\2\2\2\u009e\u0098\3\2\2\2\u009f"+
		".\3\2\2\2\u00a0\u00a1\4\63;\2\u00a1\60\3\2\2\2\u00a2\u00a4\7g\2\2\u00a3"+
		"\u00a5\5+\26\2\u00a4\u00a3\3\2\2\2\u00a4\u00a5\3\2\2\2\u00a5\u00a7\3\2"+
		"\2\2\u00a6\u00a8\5\63\32\2\u00a7\u00a6\3\2\2\2\u00a8\u00a9\3\2\2\2\u00a9"+
		"\u00a7\3\2\2\2\u00a9\u00aa\3\2\2\2\u00aa\62\3\2\2\2\u00ab\u00ac\4\62;"+
		"\2\u00ac\64\3\2\2\2\u00ad\u00af\7$\2\2\u00ae\u00b0\5\67\34\2\u00af\u00ae"+
		"\3\2\2\2\u00b0\u00b1\3\2\2\2\u00b1\u00af\3\2\2\2\u00b1\u00b2\3\2\2\2\u00b2"+
		"\u00b3\3\2\2\2\u00b3\u00b4\7$\2\2\u00b4\66\3\2\2\2\u00b5\u00b6\t\4\2\2"+
		"\u00b68\3\2\2\2\u00b7\u00b9\t\5\2\2\u00b8\u00b7\3\2\2\2\u00b9\u00ba\3"+
		"\2\2\2\u00ba\u00b8\3\2\2\2\u00ba\u00bb\3\2\2\2\u00bb\u00bc\3\2\2\2\u00bc"+
		"\u00bd\b\35\2\2\u00bd:\3\2\2\2\17\2y\177\u0083\u0088\u008f\u0092\u009c"+
		"\u009e\u00a4\u00a9\u00b1\u00ba\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
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
		ExponentPart=24, Digit=25, StringValue=26, StringCharacter=27;
	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	public static final String[] ruleNames = {
		"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
		"T__9", "T__10", "T__11", "T__12", "T__13", "T__14", "T__15", "BooleanValue", 
		"NAME", "IntValue", "FloatValue", "Sign", "IntegerPart", "NonZeroDigit", 
		"ExponentPart", "Digit", "StringValue", "StringCharacter"
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
		"StringCharacter"
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
		"\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\2\35\u00b5\b\1\4\2"+
		"\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4"+
		"\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22"+
		"\t\22\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31"+
		"\t\31\4\32\t\32\4\33\t\33\4\34\t\34\3\2\3\2\3\2\3\2\3\2\3\2\3\3\3\3\3"+
		"\3\3\3\3\3\3\3\3\3\3\3\3\3\3\4\3\4\3\5\3\5\3\6\3\6\3\7\3\7\3\b\3\b\3\t"+
		"\3\t\3\n\3\n\3\13\3\13\3\13\3\13\3\f\3\f\3\f\3\r\3\r\3\r\3\r\3\r\3\r\3"+
		"\r\3\r\3\r\3\16\3\16\3\17\3\17\3\20\3\20\3\21\3\21\3\22\3\22\3\22\3\22"+
		"\3\22\3\22\3\22\3\22\3\22\5\22x\n\22\3\23\3\23\7\23|\n\23\f\23\16\23\177"+
		"\13\23\3\24\5\24\u0082\n\24\3\24\3\24\3\25\5\25\u0087\n\25\3\25\3\25\3"+
		"\25\6\25\u008c\n\25\r\25\16\25\u008d\3\25\5\25\u0091\n\25\3\26\3\26\3"+
		"\27\3\27\3\27\3\27\6\27\u0099\n\27\r\27\16\27\u009a\5\27\u009d\n\27\3"+
		"\30\3\30\3\31\3\31\5\31\u00a3\n\31\3\31\6\31\u00a6\n\31\r\31\16\31\u00a7"+
		"\3\32\3\32\3\33\3\33\6\33\u00ae\n\33\r\33\16\33\u00af\3\33\3\33\3\34\3"+
		"\34\2\2\35\3\3\5\4\7\5\t\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33"+
		"\17\35\20\37\21!\22#\23%\24\'\25)\26+\27-\30/\31\61\32\63\33\65\34\67"+
		"\35\3\2\5\5\2C\\aac|\6\2\62;C\\aac|\5\2\62;C\\c|\u00c0\2\3\3\2\2\2\2\5"+
		"\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2"+
		"\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33"+
		"\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2\2\2\2%\3\2\2\2\2"+
		"\'\3\2\2\2\2)\3\2\2\2\2+\3\2\2\2\2-\3\2\2\2\2/\3\2\2\2\2\61\3\2\2\2\2"+
		"\63\3\2\2\2\2\65\3\2\2\2\2\67\3\2\2\2\39\3\2\2\2\5?\3\2\2\2\7H\3\2\2\2"+
		"\tJ\3\2\2\2\13L\3\2\2\2\rN\3\2\2\2\17P\3\2\2\2\21R\3\2\2\2\23T\3\2\2\2"+
		"\25V\3\2\2\2\27Z\3\2\2\2\31]\3\2\2\2\33f\3\2\2\2\35h\3\2\2\2\37j\3\2\2"+
		"\2!l\3\2\2\2#w\3\2\2\2%y\3\2\2\2\'\u0081\3\2\2\2)\u0086\3\2\2\2+\u0092"+
		"\3\2\2\2-\u009c\3\2\2\2/\u009e\3\2\2\2\61\u00a0\3\2\2\2\63\u00a9\3\2\2"+
		"\2\65\u00ab\3\2\2\2\67\u00b3\3\2\2\29:\7s\2\2:;\7w\2\2;<\7g\2\2<=\7t\2"+
		"\2=>\7{\2\2>\4\3\2\2\2?@\7o\2\2@A\7w\2\2AB\7v\2\2BC\7c\2\2CD\7v\2\2DE"+
		"\7k\2\2EF\7q\2\2FG\7p\2\2G\6\3\2\2\2HI\7*\2\2I\b\3\2\2\2JK\7+\2\2K\n\3"+
		"\2\2\2LM\7<\2\2M\f\3\2\2\2NO\7&\2\2O\16\3\2\2\2PQ\7?\2\2Q\20\3\2\2\2R"+
		"S\7}\2\2S\22\3\2\2\2TU\7\177\2\2U\24\3\2\2\2VW\7\60\2\2WX\7\60\2\2XY\7"+
		"\60\2\2Y\26\3\2\2\2Z[\7q\2\2[\\\7p\2\2\\\30\3\2\2\2]^\7h\2\2^_\7t\2\2"+
		"_`\7c\2\2`a\7i\2\2ab\7o\2\2bc\7g\2\2cd\7p\2\2de\7v\2\2e\32\3\2\2\2fg\7"+
		"]\2\2g\34\3\2\2\2hi\7_\2\2i\36\3\2\2\2jk\7B\2\2k \3\2\2\2lm\7#\2\2m\""+
		"\3\2\2\2no\7v\2\2op\7t\2\2pq\7w\2\2qx\7g\2\2rs\7h\2\2st\7c\2\2tu\7n\2"+
		"\2uv\7u\2\2vx\7g\2\2wn\3\2\2\2wr\3\2\2\2x$\3\2\2\2y}\t\2\2\2z|\t\3\2\2"+
		"{z\3\2\2\2|\177\3\2\2\2}{\3\2\2\2}~\3\2\2\2~&\3\2\2\2\177}\3\2\2\2\u0080"+
		"\u0082\5+\26\2\u0081\u0080\3\2\2\2\u0081\u0082\3\2\2\2\u0082\u0083\3\2"+
		"\2\2\u0083\u0084\5-\27\2\u0084(\3\2\2\2\u0085\u0087\5+\26\2\u0086\u0085"+
		"\3\2\2\2\u0086\u0087\3\2\2\2\u0087\u0088\3\2\2\2\u0088\u0089\5-\27\2\u0089"+
		"\u008b\7\60\2\2\u008a\u008c\5\63\32\2\u008b\u008a\3\2\2\2\u008c\u008d"+
		"\3\2\2\2\u008d\u008b\3\2\2\2\u008d\u008e\3\2\2\2\u008e\u0090\3\2\2\2\u008f"+
		"\u0091\5\61\31\2\u0090\u008f\3\2\2\2\u0090\u0091\3\2\2\2\u0091*\3\2\2"+
		"\2\u0092\u0093\7/\2\2\u0093,\3\2\2\2\u0094\u009d\7\62\2\2\u0095\u009d"+
		"\5/\30\2\u0096\u0098\5/\30\2\u0097\u0099\5\63\32\2\u0098\u0097\3\2\2\2"+
		"\u0099\u009a\3\2\2\2\u009a\u0098\3\2\2\2\u009a\u009b\3\2\2\2\u009b\u009d"+
		"\3\2\2\2\u009c\u0094\3\2\2\2\u009c\u0095\3\2\2\2\u009c\u0096\3\2\2\2\u009d"+
		".\3\2\2\2\u009e\u009f\4\63;\2\u009f\60\3\2\2\2\u00a0\u00a2\7g\2\2\u00a1"+
		"\u00a3\5+\26\2\u00a2\u00a1\3\2\2\2\u00a2\u00a3\3\2\2\2\u00a3\u00a5\3\2"+
		"\2\2\u00a4\u00a6\5\63\32\2\u00a5\u00a4\3\2\2\2\u00a6\u00a7\3\2\2\2\u00a7"+
		"\u00a5\3\2\2\2\u00a7\u00a8\3\2\2\2\u00a8\62\3\2\2\2\u00a9\u00aa\4\62;"+
		"\2\u00aa\64\3\2\2\2\u00ab\u00ad\7$\2\2\u00ac\u00ae\5\67\34\2\u00ad\u00ac"+
		"\3\2\2\2\u00ae\u00af\3\2\2\2\u00af\u00ad\3\2\2\2\u00af\u00b0\3\2\2\2\u00b0"+
		"\u00b1\3\2\2\2\u00b1\u00b2\7$\2\2\u00b2\66\3\2\2\2\u00b3\u00b4\t\4\2\2"+
		"\u00b48\3\2\2\2\16\2w}\u0081\u0086\u008d\u0090\u009a\u009c\u00a2\u00a7"+
		"\u00af\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
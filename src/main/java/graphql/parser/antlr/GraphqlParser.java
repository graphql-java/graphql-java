// Generated from /Users/andi/dev/projects/graphql-java/src/main/grammar/Graphql.g4 by ANTLR 4.5.1
package graphql.parser.antlr;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class GraphqlParser extends Parser {
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
    public static final int
            RULE_document = 0, RULE_definition = 1, RULE_operationDefinition = 2,
            RULE_operationType = 3, RULE_variableDefinitions = 4, RULE_variableDefinition = 5,
            RULE_variable = 6, RULE_defaultValue = 7, RULE_selectionSet = 8, RULE_selection = 9,
            RULE_field = 10, RULE_alias = 11, RULE_arguments = 12, RULE_argument = 13,
            RULE_fragmentSpread = 14, RULE_inlineFragment = 15, RULE_fragmentDefinition = 16,
            RULE_fragmentName = 17, RULE_typeCondition = 18, RULE_value = 19, RULE_valueWithVariable = 20,
            RULE_enumValue = 21, RULE_arrayValue = 22, RULE_arrayValueWithVariable = 23,
            RULE_objectValue = 24, RULE_objectValueWithVariable = 25, RULE_objectField = 26,
            RULE_objectFieldWithVariable = 27, RULE_directives = 28, RULE_directive = 29,
            RULE_type = 30, RULE_typeName = 31, RULE_listType = 32, RULE_nonNullType = 33;
    public static final String[] ruleNames = {
            "document", "definition", "operationDefinition", "operationType", "variableDefinitions",
            "variableDefinition", "variable", "defaultValue", "selectionSet", "selection",
            "field", "alias", "arguments", "argument", "fragmentSpread", "inlineFragment",
            "fragmentDefinition", "fragmentName", "typeCondition", "value", "valueWithVariable",
            "enumValue", "arrayValue", "arrayValueWithVariable", "objectValue", "objectValueWithVariable",
            "objectField", "objectFieldWithVariable", "directives", "directive", "type",
            "typeName", "listType", "nonNullType"
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
    public ATN getATN() {
        return _ATN;
    }

    public GraphqlParser(TokenStream input) {
        super(input);
        _interp = new ParserATNSimulator(this, _ATN, _decisionToDFA, _sharedContextCache);
    }

    public static class DocumentContext extends ParserRuleContext {
        public List<DefinitionContext> definition() {
            return getRuleContexts(DefinitionContext.class);
        }

        public DefinitionContext definition(int i) {
            return getRuleContext(DefinitionContext.class, i);
        }

        public DocumentContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_document;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitDocument(this);
            else return visitor.visitChildren(this);
        }
    }

    public final DocumentContext document() throws RecognitionException {
        DocumentContext _localctx = new DocumentContext(_ctx, getState());
        enterRule(_localctx, 0, RULE_document);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(69);
                _errHandler.sync(this);
                _la = _input.LA(1);
                do {
                    {
                        {
                            setState(68);
                            definition();
                        }
                    }
                    setState(71);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
                while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__1) | (1L << T__7) | (1L << T__11))) != 0));
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class DefinitionContext extends ParserRuleContext {
        public OperationDefinitionContext operationDefinition() {
            return getRuleContext(OperationDefinitionContext.class, 0);
        }

        public FragmentDefinitionContext fragmentDefinition() {
            return getRuleContext(FragmentDefinitionContext.class, 0);
        }

        public DefinitionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_definition;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitDefinition(this);
            else return visitor.visitChildren(this);
        }
    }

    public final DefinitionContext definition() throws RecognitionException {
        DefinitionContext _localctx = new DefinitionContext(_ctx, getState());
        enterRule(_localctx, 2, RULE_definition);
        try {
            setState(75);
            switch (_input.LA(1)) {
                case T__0:
                case T__1:
                case T__7:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(73);
                    operationDefinition();
                }
                break;
                case T__11:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(74);
                    fragmentDefinition();
                }
                break;
                default:
                    throw new NoViableAltException(this);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class OperationDefinitionContext extends ParserRuleContext {
        public SelectionSetContext selectionSet() {
            return getRuleContext(SelectionSetContext.class, 0);
        }

        public OperationTypeContext operationType() {
            return getRuleContext(OperationTypeContext.class, 0);
        }

        public TerminalNode NAME() {
            return getToken(GraphqlParser.NAME, 0);
        }

        public VariableDefinitionsContext variableDefinitions() {
            return getRuleContext(VariableDefinitionsContext.class, 0);
        }

        public DirectivesContext directives() {
            return getRuleContext(DirectivesContext.class, 0);
        }

        public OperationDefinitionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_operationDefinition;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitOperationDefinition(this);
            else return visitor.visitChildren(this);
        }
    }

    public final OperationDefinitionContext operationDefinition() throws RecognitionException {
        OperationDefinitionContext _localctx = new OperationDefinitionContext(_ctx, getState());
        enterRule(_localctx, 4, RULE_operationDefinition);
        int _la;
        try {
            setState(90);
            switch (_input.LA(1)) {
                case T__7:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(77);
                    selectionSet();
                }
                break;
                case T__0:
                case T__1:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(78);
                    operationType();
                    setState(80);
                    _la = _input.LA(1);
                    if (_la == NAME) {
                        {
                            setState(79);
                            match(NAME);
                        }
                    }

                    setState(83);
                    _la = _input.LA(1);
                    if (_la == T__2) {
                        {
                            setState(82);
                            variableDefinitions();
                        }
                    }

                    setState(86);
                    _la = _input.LA(1);
                    if (_la == T__14) {
                        {
                            setState(85);
                            directives();
                        }
                    }

                    setState(88);
                    selectionSet();
                }
                break;
                default:
                    throw new NoViableAltException(this);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class OperationTypeContext extends ParserRuleContext {
        public OperationTypeContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_operationType;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitOperationType(this);
            else return visitor.visitChildren(this);
        }
    }

    public final OperationTypeContext operationType() throws RecognitionException {
        OperationTypeContext _localctx = new OperationTypeContext(_ctx, getState());
        enterRule(_localctx, 6, RULE_operationType);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(92);
                _la = _input.LA(1);
                if (!(_la == T__0 || _la == T__1)) {
                    _errHandler.recoverInline(this);
                } else {
                    consume();
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class VariableDefinitionsContext extends ParserRuleContext {
        public List<VariableDefinitionContext> variableDefinition() {
            return getRuleContexts(VariableDefinitionContext.class);
        }

        public VariableDefinitionContext variableDefinition(int i) {
            return getRuleContext(VariableDefinitionContext.class, i);
        }

        public VariableDefinitionsContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_variableDefinitions;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitVariableDefinitions(this);
            else return visitor.visitChildren(this);
        }
    }

    public final VariableDefinitionsContext variableDefinitions() throws RecognitionException {
        VariableDefinitionsContext _localctx = new VariableDefinitionsContext(_ctx, getState());
        enterRule(_localctx, 8, RULE_variableDefinitions);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(94);
                match(T__2);
                setState(96);
                _errHandler.sync(this);
                _la = _input.LA(1);
                do {
                    {
                        {
                            setState(95);
                            variableDefinition();
                        }
                    }
                    setState(98);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                } while (_la == T__5);
                setState(100);
                match(T__3);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class VariableDefinitionContext extends ParserRuleContext {
        public VariableContext variable() {
            return getRuleContext(VariableContext.class, 0);
        }

        public TypeContext type() {
            return getRuleContext(TypeContext.class, 0);
        }

        public DefaultValueContext defaultValue() {
            return getRuleContext(DefaultValueContext.class, 0);
        }

        public VariableDefinitionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_variableDefinition;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitVariableDefinition(this);
            else return visitor.visitChildren(this);
        }
    }

    public final VariableDefinitionContext variableDefinition() throws RecognitionException {
        VariableDefinitionContext _localctx = new VariableDefinitionContext(_ctx, getState());
        enterRule(_localctx, 10, RULE_variableDefinition);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(102);
                variable();
                setState(103);
                match(T__4);
                setState(104);
                type();
                setState(106);
                _la = _input.LA(1);
                if (_la == T__6) {
                    {
                        setState(105);
                        defaultValue();
                    }
                }

            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class VariableContext extends ParserRuleContext {
        public TerminalNode NAME() {
            return getToken(GraphqlParser.NAME, 0);
        }

        public VariableContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_variable;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitVariable(this);
            else return visitor.visitChildren(this);
        }
    }

    public final VariableContext variable() throws RecognitionException {
        VariableContext _localctx = new VariableContext(_ctx, getState());
        enterRule(_localctx, 12, RULE_variable);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(108);
                match(T__5);
                setState(109);
                match(NAME);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class DefaultValueContext extends ParserRuleContext {
        public ValueContext value() {
            return getRuleContext(ValueContext.class, 0);
        }

        public DefaultValueContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_defaultValue;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitDefaultValue(this);
            else return visitor.visitChildren(this);
        }
    }

    public final DefaultValueContext defaultValue() throws RecognitionException {
        DefaultValueContext _localctx = new DefaultValueContext(_ctx, getState());
        enterRule(_localctx, 14, RULE_defaultValue);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(111);
                match(T__6);
                setState(112);
                value();
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class SelectionSetContext extends ParserRuleContext {
        public List<SelectionContext> selection() {
            return getRuleContexts(SelectionContext.class);
        }

        public SelectionContext selection(int i) {
            return getRuleContext(SelectionContext.class, i);
        }

        public SelectionSetContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_selectionSet;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitSelectionSet(this);
            else return visitor.visitChildren(this);
        }
    }

    public final SelectionSetContext selectionSet() throws RecognitionException {
        SelectionSetContext _localctx = new SelectionSetContext(_ctx, getState());
        enterRule(_localctx, 16, RULE_selectionSet);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(114);
                match(T__7);
                setState(116);
                _errHandler.sync(this);
                _la = _input.LA(1);
                do {
                    {
                        {
                            setState(115);
                            selection();
                        }
                    }
                    setState(118);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                } while (_la == T__9 || _la == NAME);
                setState(120);
                match(T__8);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class SelectionContext extends ParserRuleContext {
        public FieldContext field() {
            return getRuleContext(FieldContext.class, 0);
        }

        public FragmentSpreadContext fragmentSpread() {
            return getRuleContext(FragmentSpreadContext.class, 0);
        }

        public InlineFragmentContext inlineFragment() {
            return getRuleContext(InlineFragmentContext.class, 0);
        }

        public SelectionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_selection;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitSelection(this);
            else return visitor.visitChildren(this);
        }
    }

    public final SelectionContext selection() throws RecognitionException {
        SelectionContext _localctx = new SelectionContext(_ctx, getState());
        enterRule(_localctx, 18, RULE_selection);
        try {
            setState(125);
            switch (getInterpreter().adaptivePredict(_input, 9, _ctx)) {
                case 1:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(122);
                    field();
                }
                break;
                case 2:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(123);
                    fragmentSpread();
                }
                break;
                case 3:
                    enterOuterAlt(_localctx, 3);
                {
                    setState(124);
                    inlineFragment();
                }
                break;
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class FieldContext extends ParserRuleContext {
        public TerminalNode NAME() {
            return getToken(GraphqlParser.NAME, 0);
        }

        public AliasContext alias() {
            return getRuleContext(AliasContext.class, 0);
        }

        public ArgumentsContext arguments() {
            return getRuleContext(ArgumentsContext.class, 0);
        }

        public DirectivesContext directives() {
            return getRuleContext(DirectivesContext.class, 0);
        }

        public SelectionSetContext selectionSet() {
            return getRuleContext(SelectionSetContext.class, 0);
        }

        public FieldContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_field;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitField(this);
            else return visitor.visitChildren(this);
        }
    }

    public final FieldContext field() throws RecognitionException {
        FieldContext _localctx = new FieldContext(_ctx, getState());
        enterRule(_localctx, 20, RULE_field);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(128);
                switch (getInterpreter().adaptivePredict(_input, 10, _ctx)) {
                    case 1: {
                        setState(127);
                        alias();
                    }
                    break;
                }
                setState(130);
                match(NAME);
                setState(132);
                _la = _input.LA(1);
                if (_la == T__2) {
                    {
                        setState(131);
                        arguments();
                    }
                }

                setState(135);
                _la = _input.LA(1);
                if (_la == T__14) {
                    {
                        setState(134);
                        directives();
                    }
                }

                setState(138);
                _la = _input.LA(1);
                if (_la == T__7) {
                    {
                        setState(137);
                        selectionSet();
                    }
                }

            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class AliasContext extends ParserRuleContext {
        public TerminalNode NAME() {
            return getToken(GraphqlParser.NAME, 0);
        }

        public AliasContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_alias;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitAlias(this);
            else return visitor.visitChildren(this);
        }
    }

    public final AliasContext alias() throws RecognitionException {
        AliasContext _localctx = new AliasContext(_ctx, getState());
        enterRule(_localctx, 22, RULE_alias);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(140);
                match(NAME);
                setState(141);
                match(T__4);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class ArgumentsContext extends ParserRuleContext {
        public List<ArgumentContext> argument() {
            return getRuleContexts(ArgumentContext.class);
        }

        public ArgumentContext argument(int i) {
            return getRuleContext(ArgumentContext.class, i);
        }

        public ArgumentsContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_arguments;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitArguments(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ArgumentsContext arguments() throws RecognitionException {
        ArgumentsContext _localctx = new ArgumentsContext(_ctx, getState());
        enterRule(_localctx, 24, RULE_arguments);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(143);
                match(T__2);
                setState(145);
                _errHandler.sync(this);
                _la = _input.LA(1);
                do {
                    {
                        {
                            setState(144);
                            argument();
                        }
                    }
                    setState(147);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                } while (_la == NAME);
                setState(149);
                match(T__3);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class ArgumentContext extends ParserRuleContext {
        public TerminalNode NAME() {
            return getToken(GraphqlParser.NAME, 0);
        }

        public ValueWithVariableContext valueWithVariable() {
            return getRuleContext(ValueWithVariableContext.class, 0);
        }

        public ArgumentContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_argument;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitArgument(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ArgumentContext argument() throws RecognitionException {
        ArgumentContext _localctx = new ArgumentContext(_ctx, getState());
        enterRule(_localctx, 26, RULE_argument);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(151);
                match(NAME);
                setState(152);
                match(T__4);
                setState(153);
                valueWithVariable();
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class FragmentSpreadContext extends ParserRuleContext {
        public FragmentNameContext fragmentName() {
            return getRuleContext(FragmentNameContext.class, 0);
        }

        public DirectivesContext directives() {
            return getRuleContext(DirectivesContext.class, 0);
        }

        public FragmentSpreadContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_fragmentSpread;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitFragmentSpread(this);
            else return visitor.visitChildren(this);
        }
    }

    public final FragmentSpreadContext fragmentSpread() throws RecognitionException {
        FragmentSpreadContext _localctx = new FragmentSpreadContext(_ctx, getState());
        enterRule(_localctx, 28, RULE_fragmentSpread);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(155);
                match(T__9);
                setState(156);
                fragmentName();
                setState(158);
                _la = _input.LA(1);
                if (_la == T__14) {
                    {
                        setState(157);
                        directives();
                    }
                }

            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class InlineFragmentContext extends ParserRuleContext {
        public TypeConditionContext typeCondition() {
            return getRuleContext(TypeConditionContext.class, 0);
        }

        public SelectionSetContext selectionSet() {
            return getRuleContext(SelectionSetContext.class, 0);
        }

        public DirectivesContext directives() {
            return getRuleContext(DirectivesContext.class, 0);
        }

        public InlineFragmentContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_inlineFragment;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitInlineFragment(this);
            else return visitor.visitChildren(this);
        }
    }

    public final InlineFragmentContext inlineFragment() throws RecognitionException {
        InlineFragmentContext _localctx = new InlineFragmentContext(_ctx, getState());
        enterRule(_localctx, 30, RULE_inlineFragment);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(160);
                match(T__9);
                setState(161);
                match(T__10);
                setState(162);
                typeCondition();
                setState(164);
                _la = _input.LA(1);
                if (_la == T__14) {
                    {
                        setState(163);
                        directives();
                    }
                }

                setState(166);
                selectionSet();
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class FragmentDefinitionContext extends ParserRuleContext {
        public FragmentNameContext fragmentName() {
            return getRuleContext(FragmentNameContext.class, 0);
        }

        public TypeConditionContext typeCondition() {
            return getRuleContext(TypeConditionContext.class, 0);
        }

        public SelectionSetContext selectionSet() {
            return getRuleContext(SelectionSetContext.class, 0);
        }

        public DirectivesContext directives() {
            return getRuleContext(DirectivesContext.class, 0);
        }

        public FragmentDefinitionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_fragmentDefinition;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitFragmentDefinition(this);
            else return visitor.visitChildren(this);
        }
    }

    public final FragmentDefinitionContext fragmentDefinition() throws RecognitionException {
        FragmentDefinitionContext _localctx = new FragmentDefinitionContext(_ctx, getState());
        enterRule(_localctx, 32, RULE_fragmentDefinition);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(168);
                match(T__11);
                setState(169);
                fragmentName();
                setState(170);
                match(T__10);
                setState(171);
                typeCondition();
                setState(173);
                _la = _input.LA(1);
                if (_la == T__14) {
                    {
                        setState(172);
                        directives();
                    }
                }

                setState(175);
                selectionSet();
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class FragmentNameContext extends ParserRuleContext {
        public TerminalNode NAME() {
            return getToken(GraphqlParser.NAME, 0);
        }

        public FragmentNameContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_fragmentName;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitFragmentName(this);
            else return visitor.visitChildren(this);
        }
    }

    public final FragmentNameContext fragmentName() throws RecognitionException {
        FragmentNameContext _localctx = new FragmentNameContext(_ctx, getState());
        enterRule(_localctx, 34, RULE_fragmentName);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(177);
                match(NAME);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class TypeConditionContext extends ParserRuleContext {
        public TypeNameContext typeName() {
            return getRuleContext(TypeNameContext.class, 0);
        }

        public TypeConditionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_typeCondition;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitTypeCondition(this);
            else return visitor.visitChildren(this);
        }
    }

    public final TypeConditionContext typeCondition() throws RecognitionException {
        TypeConditionContext _localctx = new TypeConditionContext(_ctx, getState());
        enterRule(_localctx, 36, RULE_typeCondition);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(179);
                typeName();
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class ValueContext extends ParserRuleContext {
        public TerminalNode IntValue() {
            return getToken(GraphqlParser.IntValue, 0);
        }

        public TerminalNode FloatValue() {
            return getToken(GraphqlParser.FloatValue, 0);
        }

        public TerminalNode StringValue() {
            return getToken(GraphqlParser.StringValue, 0);
        }

        public TerminalNode BooleanValue() {
            return getToken(GraphqlParser.BooleanValue, 0);
        }

        public EnumValueContext enumValue() {
            return getRuleContext(EnumValueContext.class, 0);
        }

        public ArrayValueContext arrayValue() {
            return getRuleContext(ArrayValueContext.class, 0);
        }

        public ObjectValueContext objectValue() {
            return getRuleContext(ObjectValueContext.class, 0);
        }

        public ValueContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_value;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitValue(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ValueContext value() throws RecognitionException {
        ValueContext _localctx = new ValueContext(_ctx, getState());
        enterRule(_localctx, 38, RULE_value);
        try {
            setState(188);
            switch (_input.LA(1)) {
                case IntValue:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(181);
                    match(IntValue);
                }
                break;
                case FloatValue:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(182);
                    match(FloatValue);
                }
                break;
                case StringValue:
                    enterOuterAlt(_localctx, 3);
                {
                    setState(183);
                    match(StringValue);
                }
                break;
                case BooleanValue:
                    enterOuterAlt(_localctx, 4);
                {
                    setState(184);
                    match(BooleanValue);
                }
                break;
                case NAME:
                    enterOuterAlt(_localctx, 5);
                {
                    setState(185);
                    enumValue();
                }
                break;
                case T__12:
                    enterOuterAlt(_localctx, 6);
                {
                    setState(186);
                    arrayValue();
                }
                break;
                case T__7:
                    enterOuterAlt(_localctx, 7);
                {
                    setState(187);
                    objectValue();
                }
                break;
                default:
                    throw new NoViableAltException(this);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class ValueWithVariableContext extends ParserRuleContext {
        public VariableContext variable() {
            return getRuleContext(VariableContext.class, 0);
        }

        public TerminalNode IntValue() {
            return getToken(GraphqlParser.IntValue, 0);
        }

        public TerminalNode FloatValue() {
            return getToken(GraphqlParser.FloatValue, 0);
        }

        public TerminalNode StringValue() {
            return getToken(GraphqlParser.StringValue, 0);
        }

        public TerminalNode BooleanValue() {
            return getToken(GraphqlParser.BooleanValue, 0);
        }

        public EnumValueContext enumValue() {
            return getRuleContext(EnumValueContext.class, 0);
        }

        public ArrayValueWithVariableContext arrayValueWithVariable() {
            return getRuleContext(ArrayValueWithVariableContext.class, 0);
        }

        public ObjectValueWithVariableContext objectValueWithVariable() {
            return getRuleContext(ObjectValueWithVariableContext.class, 0);
        }

        public ValueWithVariableContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_valueWithVariable;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitValueWithVariable(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ValueWithVariableContext valueWithVariable() throws RecognitionException {
        ValueWithVariableContext _localctx = new ValueWithVariableContext(_ctx, getState());
        enterRule(_localctx, 40, RULE_valueWithVariable);
        try {
            setState(198);
            switch (_input.LA(1)) {
                case T__5:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(190);
                    variable();
                }
                break;
                case IntValue:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(191);
                    match(IntValue);
                }
                break;
                case FloatValue:
                    enterOuterAlt(_localctx, 3);
                {
                    setState(192);
                    match(FloatValue);
                }
                break;
                case StringValue:
                    enterOuterAlt(_localctx, 4);
                {
                    setState(193);
                    match(StringValue);
                }
                break;
                case BooleanValue:
                    enterOuterAlt(_localctx, 5);
                {
                    setState(194);
                    match(BooleanValue);
                }
                break;
                case NAME:
                    enterOuterAlt(_localctx, 6);
                {
                    setState(195);
                    enumValue();
                }
                break;
                case T__12:
                    enterOuterAlt(_localctx, 7);
                {
                    setState(196);
                    arrayValueWithVariable();
                }
                break;
                case T__7:
                    enterOuterAlt(_localctx, 8);
                {
                    setState(197);
                    objectValueWithVariable();
                }
                break;
                default:
                    throw new NoViableAltException(this);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class EnumValueContext extends ParserRuleContext {
        public TerminalNode NAME() {
            return getToken(GraphqlParser.NAME, 0);
        }

        public EnumValueContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_enumValue;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitEnumValue(this);
            else return visitor.visitChildren(this);
        }
    }

    public final EnumValueContext enumValue() throws RecognitionException {
        EnumValueContext _localctx = new EnumValueContext(_ctx, getState());
        enterRule(_localctx, 42, RULE_enumValue);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(200);
                match(NAME);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class ArrayValueContext extends ParserRuleContext {
        public List<ValueContext> value() {
            return getRuleContexts(ValueContext.class);
        }

        public ValueContext value(int i) {
            return getRuleContext(ValueContext.class, i);
        }

        public ArrayValueContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_arrayValue;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitArrayValue(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ArrayValueContext arrayValue() throws RecognitionException {
        ArrayValueContext _localctx = new ArrayValueContext(_ctx, getState());
        enterRule(_localctx, 44, RULE_arrayValue);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(202);
                match(T__12);
                setState(206);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__7) | (1L << T__12) | (1L << BooleanValue) | (1L << NAME) | (1L << IntValue) | (1L << FloatValue) | (1L << StringValue))) != 0)) {
                    {
                        {
                            setState(203);
                            value();
                        }
                    }
                    setState(208);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
                setState(209);
                match(T__13);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class ArrayValueWithVariableContext extends ParserRuleContext {
        public List<ValueWithVariableContext> valueWithVariable() {
            return getRuleContexts(ValueWithVariableContext.class);
        }

        public ValueWithVariableContext valueWithVariable(int i) {
            return getRuleContext(ValueWithVariableContext.class, i);
        }

        public ArrayValueWithVariableContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_arrayValueWithVariable;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitArrayValueWithVariable(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ArrayValueWithVariableContext arrayValueWithVariable() throws RecognitionException {
        ArrayValueWithVariableContext _localctx = new ArrayValueWithVariableContext(_ctx, getState());
        enterRule(_localctx, 46, RULE_arrayValueWithVariable);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(211);
                match(T__12);
                setState(215);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__5) | (1L << T__7) | (1L << T__12) | (1L << BooleanValue) | (1L << NAME) | (1L << IntValue) | (1L << FloatValue) | (1L << StringValue))) != 0)) {
                    {
                        {
                            setState(212);
                            valueWithVariable();
                        }
                    }
                    setState(217);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
                setState(218);
                match(T__13);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class ObjectValueContext extends ParserRuleContext {
        public List<ObjectFieldContext> objectField() {
            return getRuleContexts(ObjectFieldContext.class);
        }

        public ObjectFieldContext objectField(int i) {
            return getRuleContext(ObjectFieldContext.class, i);
        }

        public ObjectValueContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_objectValue;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitObjectValue(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ObjectValueContext objectValue() throws RecognitionException {
        ObjectValueContext _localctx = new ObjectValueContext(_ctx, getState());
        enterRule(_localctx, 48, RULE_objectValue);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(220);
                match(T__7);
                setState(224);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == NAME) {
                    {
                        {
                            setState(221);
                            objectField();
                        }
                    }
                    setState(226);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
                setState(227);
                match(T__8);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class ObjectValueWithVariableContext extends ParserRuleContext {
        public List<ObjectFieldWithVariableContext> objectFieldWithVariable() {
            return getRuleContexts(ObjectFieldWithVariableContext.class);
        }

        public ObjectFieldWithVariableContext objectFieldWithVariable(int i) {
            return getRuleContext(ObjectFieldWithVariableContext.class, i);
        }

        public ObjectValueWithVariableContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_objectValueWithVariable;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitObjectValueWithVariable(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ObjectValueWithVariableContext objectValueWithVariable() throws RecognitionException {
        ObjectValueWithVariableContext _localctx = new ObjectValueWithVariableContext(_ctx, getState());
        enterRule(_localctx, 50, RULE_objectValueWithVariable);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(229);
                match(T__7);
                setState(233);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == NAME) {
                    {
                        {
                            setState(230);
                            objectFieldWithVariable();
                        }
                    }
                    setState(235);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
                setState(236);
                match(T__8);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class ObjectFieldContext extends ParserRuleContext {
        public TerminalNode NAME() {
            return getToken(GraphqlParser.NAME, 0);
        }

        public ValueContext value() {
            return getRuleContext(ValueContext.class, 0);
        }

        public ObjectFieldContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_objectField;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitObjectField(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ObjectFieldContext objectField() throws RecognitionException {
        ObjectFieldContext _localctx = new ObjectFieldContext(_ctx, getState());
        enterRule(_localctx, 52, RULE_objectField);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(238);
                match(NAME);
                setState(239);
                match(T__4);
                setState(240);
                value();
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class ObjectFieldWithVariableContext extends ParserRuleContext {
        public TerminalNode NAME() {
            return getToken(GraphqlParser.NAME, 0);
        }

        public ValueWithVariableContext valueWithVariable() {
            return getRuleContext(ValueWithVariableContext.class, 0);
        }

        public ObjectFieldWithVariableContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_objectFieldWithVariable;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitObjectFieldWithVariable(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ObjectFieldWithVariableContext objectFieldWithVariable() throws RecognitionException {
        ObjectFieldWithVariableContext _localctx = new ObjectFieldWithVariableContext(_ctx, getState());
        enterRule(_localctx, 54, RULE_objectFieldWithVariable);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(242);
                match(NAME);
                setState(243);
                match(T__4);
                setState(244);
                valueWithVariable();
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class DirectivesContext extends ParserRuleContext {
        public List<DirectiveContext> directive() {
            return getRuleContexts(DirectiveContext.class);
        }

        public DirectiveContext directive(int i) {
            return getRuleContext(DirectiveContext.class, i);
        }

        public DirectivesContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_directives;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitDirectives(this);
            else return visitor.visitChildren(this);
        }
    }

    public final DirectivesContext directives() throws RecognitionException {
        DirectivesContext _localctx = new DirectivesContext(_ctx, getState());
        enterRule(_localctx, 56, RULE_directives);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(247);
                _errHandler.sync(this);
                _la = _input.LA(1);
                do {
                    {
                        {
                            setState(246);
                            directive();
                        }
                    }
                    setState(249);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                } while (_la == T__14);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class DirectiveContext extends ParserRuleContext {
        public TerminalNode NAME() {
            return getToken(GraphqlParser.NAME, 0);
        }

        public ArgumentsContext arguments() {
            return getRuleContext(ArgumentsContext.class, 0);
        }

        public DirectiveContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_directive;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitDirective(this);
            else return visitor.visitChildren(this);
        }
    }

    public final DirectiveContext directive() throws RecognitionException {
        DirectiveContext _localctx = new DirectiveContext(_ctx, getState());
        enterRule(_localctx, 58, RULE_directive);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(251);
                match(T__14);
                setState(252);
                match(NAME);
                setState(254);
                _la = _input.LA(1);
                if (_la == T__2) {
                    {
                        setState(253);
                        arguments();
                    }
                }

            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class TypeContext extends ParserRuleContext {
        public TypeNameContext typeName() {
            return getRuleContext(TypeNameContext.class, 0);
        }

        public ListTypeContext listType() {
            return getRuleContext(ListTypeContext.class, 0);
        }

        public NonNullTypeContext nonNullType() {
            return getRuleContext(NonNullTypeContext.class, 0);
        }

        public TypeContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_type;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitType(this);
            else return visitor.visitChildren(this);
        }
    }

    public final TypeContext type() throws RecognitionException {
        TypeContext _localctx = new TypeContext(_ctx, getState());
        enterRule(_localctx, 60, RULE_type);
        try {
            setState(259);
            switch (getInterpreter().adaptivePredict(_input, 26, _ctx)) {
                case 1:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(256);
                    typeName();
                }
                break;
                case 2:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(257);
                    listType();
                }
                break;
                case 3:
                    enterOuterAlt(_localctx, 3);
                {
                    setState(258);
                    nonNullType();
                }
                break;
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class TypeNameContext extends ParserRuleContext {
        public TerminalNode NAME() {
            return getToken(GraphqlParser.NAME, 0);
        }

        public TypeNameContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_typeName;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitTypeName(this);
            else return visitor.visitChildren(this);
        }
    }

    public final TypeNameContext typeName() throws RecognitionException {
        TypeNameContext _localctx = new TypeNameContext(_ctx, getState());
        enterRule(_localctx, 62, RULE_typeName);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(261);
                match(NAME);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class ListTypeContext extends ParserRuleContext {
        public TypeContext type() {
            return getRuleContext(TypeContext.class, 0);
        }

        public ListTypeContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_listType;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor) return ((GraphqlVisitor<? extends T>) visitor).visitListType(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ListTypeContext listType() throws RecognitionException {
        ListTypeContext _localctx = new ListTypeContext(_ctx, getState());
        enterRule(_localctx, 64, RULE_listType);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(263);
                match(T__12);
                setState(264);
                type();
                setState(265);
                match(T__13);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class NonNullTypeContext extends ParserRuleContext {
        public TypeNameContext typeName() {
            return getRuleContext(TypeNameContext.class, 0);
        }

        public ListTypeContext listType() {
            return getRuleContext(ListTypeContext.class, 0);
        }

        public NonNullTypeContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_nonNullType;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof GraphqlVisitor)
                return ((GraphqlVisitor<? extends T>) visitor).visitNonNullType(this);
            else return visitor.visitChildren(this);
        }
    }

    public final NonNullTypeContext nonNullType() throws RecognitionException {
        NonNullTypeContext _localctx = new NonNullTypeContext(_ctx, getState());
        enterRule(_localctx, 66, RULE_nonNullType);
        try {
            setState(273);
            switch (_input.LA(1)) {
                case NAME:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(267);
                    typeName();
                    setState(268);
                    match(T__15);
                }
                break;
                case T__12:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(270);
                    listType();
                    setState(271);
                    match(T__15);
                }
                break;
                default:
                    throw new NoViableAltException(this);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static final String _serializedATN =
            "\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\3\35\u0116\4\2\t\2" +
                    "\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13" +
                    "\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22" +
                    "\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31" +
                    "\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!" +
                    "\t!\4\"\t\"\4#\t#\3\2\6\2H\n\2\r\2\16\2I\3\3\3\3\5\3N\n\3\3\4\3\4\3\4" +
                    "\5\4S\n\4\3\4\5\4V\n\4\3\4\5\4Y\n\4\3\4\3\4\5\4]\n\4\3\5\3\5\3\6\3\6\6" +
                    "\6c\n\6\r\6\16\6d\3\6\3\6\3\7\3\7\3\7\3\7\5\7m\n\7\3\b\3\b\3\b\3\t\3\t" +
                    "\3\t\3\n\3\n\6\nw\n\n\r\n\16\nx\3\n\3\n\3\13\3\13\3\13\5\13\u0080\n\13" +
                    "\3\f\5\f\u0083\n\f\3\f\3\f\5\f\u0087\n\f\3\f\5\f\u008a\n\f\3\f\5\f\u008d" +
                    "\n\f\3\r\3\r\3\r\3\16\3\16\6\16\u0094\n\16\r\16\16\16\u0095\3\16\3\16" +
                    "\3\17\3\17\3\17\3\17\3\20\3\20\3\20\5\20\u00a1\n\20\3\21\3\21\3\21\3\21" +
                    "\5\21\u00a7\n\21\3\21\3\21\3\22\3\22\3\22\3\22\3\22\5\22\u00b0\n\22\3" +
                    "\22\3\22\3\23\3\23\3\24\3\24\3\25\3\25\3\25\3\25\3\25\3\25\3\25\5\25\u00bf" +
                    "\n\25\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\5\26\u00c9\n\26\3\27\3\27" +
                    "\3\30\3\30\7\30\u00cf\n\30\f\30\16\30\u00d2\13\30\3\30\3\30\3\31\3\31" +
                    "\7\31\u00d8\n\31\f\31\16\31\u00db\13\31\3\31\3\31\3\32\3\32\7\32\u00e1" +
                    "\n\32\f\32\16\32\u00e4\13\32\3\32\3\32\3\33\3\33\7\33\u00ea\n\33\f\33" +
                    "\16\33\u00ed\13\33\3\33\3\33\3\34\3\34\3\34\3\34\3\35\3\35\3\35\3\35\3" +
                    "\36\6\36\u00fa\n\36\r\36\16\36\u00fb\3\37\3\37\3\37\5\37\u0101\n\37\3" +
                    " \3 \3 \5 \u0106\n \3!\3!\3\"\3\"\3\"\3\"\3#\3#\3#\3#\3#\3#\5#\u0114\n" +
                    "#\3#\2\2$\2\4\6\b\n\f\16\20\22\24\26\30\32\34\36 \"$&(*,.\60\62\64\66" +
                    "8:<>@BD\2\3\3\2\3\4\u011c\2G\3\2\2\2\4M\3\2\2\2\6\\\3\2\2\2\b^\3\2\2\2" +
                    "\n`\3\2\2\2\fh\3\2\2\2\16n\3\2\2\2\20q\3\2\2\2\22t\3\2\2\2\24\177\3\2" +
                    "\2\2\26\u0082\3\2\2\2\30\u008e\3\2\2\2\32\u0091\3\2\2\2\34\u0099\3\2\2" +
                    "\2\36\u009d\3\2\2\2 \u00a2\3\2\2\2\"\u00aa\3\2\2\2$\u00b3\3\2\2\2&\u00b5" +
                    "\3\2\2\2(\u00be\3\2\2\2*\u00c8\3\2\2\2,\u00ca\3\2\2\2.\u00cc\3\2\2\2\60" +
                    "\u00d5\3\2\2\2\62\u00de\3\2\2\2\64\u00e7\3\2\2\2\66\u00f0\3\2\2\28\u00f4" +
                    "\3\2\2\2:\u00f9\3\2\2\2<\u00fd\3\2\2\2>\u0105\3\2\2\2@\u0107\3\2\2\2B" +
                    "\u0109\3\2\2\2D\u0113\3\2\2\2FH\5\4\3\2GF\3\2\2\2HI\3\2\2\2IG\3\2\2\2" +
                    "IJ\3\2\2\2J\3\3\2\2\2KN\5\6\4\2LN\5\"\22\2MK\3\2\2\2ML\3\2\2\2N\5\3\2" +
                    "\2\2O]\5\22\n\2PR\5\b\5\2QS\7\24\2\2RQ\3\2\2\2RS\3\2\2\2SU\3\2\2\2TV\5" +
                    "\n\6\2UT\3\2\2\2UV\3\2\2\2VX\3\2\2\2WY\5:\36\2XW\3\2\2\2XY\3\2\2\2YZ\3" +
                    "\2\2\2Z[\5\22\n\2[]\3\2\2\2\\O\3\2\2\2\\P\3\2\2\2]\7\3\2\2\2^_\t\2\2\2" +
                    "_\t\3\2\2\2`b\7\5\2\2ac\5\f\7\2ba\3\2\2\2cd\3\2\2\2db\3\2\2\2de\3\2\2" +
                    "\2ef\3\2\2\2fg\7\6\2\2g\13\3\2\2\2hi\5\16\b\2ij\7\7\2\2jl\5> \2km\5\20" +
                    "\t\2lk\3\2\2\2lm\3\2\2\2m\r\3\2\2\2no\7\b\2\2op\7\24\2\2p\17\3\2\2\2q" +
                    "r\7\t\2\2rs\5(\25\2s\21\3\2\2\2tv\7\n\2\2uw\5\24\13\2vu\3\2\2\2wx\3\2" +
                    "\2\2xv\3\2\2\2xy\3\2\2\2yz\3\2\2\2z{\7\13\2\2{\23\3\2\2\2|\u0080\5\26" +
                    "\f\2}\u0080\5\36\20\2~\u0080\5 \21\2\177|\3\2\2\2\177}\3\2\2\2\177~\3" +
                    "\2\2\2\u0080\25\3\2\2\2\u0081\u0083\5\30\r\2\u0082\u0081\3\2\2\2\u0082" +
                    "\u0083\3\2\2\2\u0083\u0084\3\2\2\2\u0084\u0086\7\24\2\2\u0085\u0087\5" +
                    "\32\16\2\u0086\u0085\3\2\2\2\u0086\u0087\3\2\2\2\u0087\u0089\3\2\2\2\u0088" +
                    "\u008a\5:\36\2\u0089\u0088\3\2\2\2\u0089\u008a\3\2\2\2\u008a\u008c\3\2" +
                    "\2\2\u008b\u008d\5\22\n\2\u008c\u008b\3\2\2\2\u008c\u008d\3\2\2\2\u008d" +
                    "\27\3\2\2\2\u008e\u008f\7\24\2\2\u008f\u0090\7\7\2\2\u0090\31\3\2\2\2" +
                    "\u0091\u0093\7\5\2\2\u0092\u0094\5\34\17\2\u0093\u0092\3\2\2\2\u0094\u0095" +
                    "\3\2\2\2\u0095\u0093\3\2\2\2\u0095\u0096\3\2\2\2\u0096\u0097\3\2\2\2\u0097" +
                    "\u0098\7\6\2\2\u0098\33\3\2\2\2\u0099\u009a\7\24\2\2\u009a\u009b\7\7\2" +
                    "\2\u009b\u009c\5*\26\2\u009c\35\3\2\2\2\u009d\u009e\7\f\2\2\u009e\u00a0" +
                    "\5$\23\2\u009f\u00a1\5:\36\2\u00a0\u009f\3\2\2\2\u00a0\u00a1\3\2\2\2\u00a1" +
                    "\37\3\2\2\2\u00a2\u00a3\7\f\2\2\u00a3\u00a4\7\r\2\2\u00a4\u00a6\5&\24" +
                    "\2\u00a5\u00a7\5:\36\2\u00a6\u00a5\3\2\2\2\u00a6\u00a7\3\2\2\2\u00a7\u00a8" +
                    "\3\2\2\2\u00a8\u00a9\5\22\n\2\u00a9!\3\2\2\2\u00aa\u00ab\7\16\2\2\u00ab" +
                    "\u00ac\5$\23\2\u00ac\u00ad\7\r\2\2\u00ad\u00af\5&\24\2\u00ae\u00b0\5:" +
                    "\36\2\u00af\u00ae\3\2\2\2\u00af\u00b0\3\2\2\2\u00b0\u00b1\3\2\2\2\u00b1" +
                    "\u00b2\5\22\n\2\u00b2#\3\2\2\2\u00b3\u00b4\7\24\2\2\u00b4%\3\2\2\2\u00b5" +
                    "\u00b6\5@!\2\u00b6\'\3\2\2\2\u00b7\u00bf\7\25\2\2\u00b8\u00bf\7\26\2\2" +
                    "\u00b9\u00bf\7\34\2\2\u00ba\u00bf\7\23\2\2\u00bb\u00bf\5,\27\2\u00bc\u00bf" +
                    "\5.\30\2\u00bd\u00bf\5\62\32\2\u00be\u00b7\3\2\2\2\u00be\u00b8\3\2\2\2" +
                    "\u00be\u00b9\3\2\2\2\u00be\u00ba\3\2\2\2\u00be\u00bb\3\2\2\2\u00be\u00bc" +
                    "\3\2\2\2\u00be\u00bd\3\2\2\2\u00bf)\3\2\2\2\u00c0\u00c9\5\16\b\2\u00c1" +
                    "\u00c9\7\25\2\2\u00c2\u00c9\7\26\2\2\u00c3\u00c9\7\34\2\2\u00c4\u00c9" +
                    "\7\23\2\2\u00c5\u00c9\5,\27\2\u00c6\u00c9\5\60\31\2\u00c7\u00c9\5\64\33" +
                    "\2\u00c8\u00c0\3\2\2\2\u00c8\u00c1\3\2\2\2\u00c8\u00c2\3\2\2\2\u00c8\u00c3" +
                    "\3\2\2\2\u00c8\u00c4\3\2\2\2\u00c8\u00c5\3\2\2\2\u00c8\u00c6\3\2\2\2\u00c8" +
                    "\u00c7\3\2\2\2\u00c9+\3\2\2\2\u00ca\u00cb\7\24\2\2\u00cb-\3\2\2\2\u00cc" +
                    "\u00d0\7\17\2\2\u00cd\u00cf\5(\25\2\u00ce\u00cd\3\2\2\2\u00cf\u00d2\3" +
                    "\2\2\2\u00d0\u00ce\3\2\2\2\u00d0\u00d1\3\2\2\2\u00d1\u00d3\3\2\2\2\u00d2" +
                    "\u00d0\3\2\2\2\u00d3\u00d4\7\20\2\2\u00d4/\3\2\2\2\u00d5\u00d9\7\17\2" +
                    "\2\u00d6\u00d8\5*\26\2\u00d7\u00d6\3\2\2\2\u00d8\u00db\3\2\2\2\u00d9\u00d7" +
                    "\3\2\2\2\u00d9\u00da\3\2\2\2\u00da\u00dc\3\2\2\2\u00db\u00d9\3\2\2\2\u00dc" +
                    "\u00dd\7\20\2\2\u00dd\61\3\2\2\2\u00de\u00e2\7\n\2\2\u00df\u00e1\5\66" +
                    "\34\2\u00e0\u00df\3\2\2\2\u00e1\u00e4\3\2\2\2\u00e2\u00e0\3\2\2\2\u00e2" +
                    "\u00e3\3\2\2\2\u00e3\u00e5\3\2\2\2\u00e4\u00e2\3\2\2\2\u00e5\u00e6\7\13" +
                    "\2\2\u00e6\63\3\2\2\2\u00e7\u00eb\7\n\2\2\u00e8\u00ea\58\35\2\u00e9\u00e8" +
                    "\3\2\2\2\u00ea\u00ed\3\2\2\2\u00eb\u00e9\3\2\2\2\u00eb\u00ec\3\2\2\2\u00ec" +
                    "\u00ee\3\2\2\2\u00ed\u00eb\3\2\2\2\u00ee\u00ef\7\13\2\2\u00ef\65\3\2\2" +
                    "\2\u00f0\u00f1\7\24\2\2\u00f1\u00f2\7\7\2\2\u00f2\u00f3\5(\25\2\u00f3" +
                    "\67\3\2\2\2\u00f4\u00f5\7\24\2\2\u00f5\u00f6\7\7\2\2\u00f6\u00f7\5*\26" +
                    "\2\u00f79\3\2\2\2\u00f8\u00fa\5<\37\2\u00f9\u00f8\3\2\2\2\u00fa\u00fb" +
                    "\3\2\2\2\u00fb\u00f9\3\2\2\2\u00fb\u00fc\3\2\2\2\u00fc;\3\2\2\2\u00fd" +
                    "\u00fe\7\21\2\2\u00fe\u0100\7\24\2\2\u00ff\u0101\5\32\16\2\u0100\u00ff" +
                    "\3\2\2\2\u0100\u0101\3\2\2\2\u0101=\3\2\2\2\u0102\u0106\5@!\2\u0103\u0106" +
                    "\5B\"\2\u0104\u0106\5D#\2\u0105\u0102\3\2\2\2\u0105\u0103\3\2\2\2\u0105" +
                    "\u0104\3\2\2\2\u0106?\3\2\2\2\u0107\u0108\7\24\2\2\u0108A\3\2\2\2\u0109" +
                    "\u010a\7\17\2\2\u010a\u010b\5> \2\u010b\u010c\7\20\2\2\u010cC\3\2\2\2" +
                    "\u010d\u010e\5@!\2\u010e\u010f\7\22\2\2\u010f\u0114\3\2\2\2\u0110\u0111" +
                    "\5B\"\2\u0111\u0112\7\22\2\2\u0112\u0114\3\2\2\2\u0113\u010d\3\2\2\2\u0113" +
                    "\u0110\3\2\2\2\u0114E\3\2\2\2\36IMRUX\\dlx\177\u0082\u0086\u0089\u008c" +
                    "\u0095\u00a0\u00a6\u00af\u00be\u00c8\u00d0\u00d9\u00e2\u00eb\u00fb\u0100" +
                    "\u0105\u0113";
    public static final ATN _ATN =
            new ATNDeserializer().deserialize(_serializedATN.toCharArray());

    static {
        _decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
        for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
            _decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
        }
    }
}
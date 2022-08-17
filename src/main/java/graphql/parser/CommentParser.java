package graphql.parser;

import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.language.AbstractDescribedNode;
import graphql.language.Comment;
import graphql.language.Document;
import graphql.language.Node;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Contains methods for extracting {@link Comment} in various positions within and around {@link Node}s
 */
@Internal
public class CommentParser {
    private final Map<Node<?>, ParserRuleContext> nodeToRuleMap;
    private CommonTokenStream tokens;
    private static final int CHANNEL_COMMENTS = 2;

    public CommentParser(NodeToRuleCapturingParser.ParserContext parserContext) {
        nodeToRuleMap = parserContext.getNodeToRuleMap();
        tokens = parserContext.getTokens();
    }

    /*
     *  type MyType { # beginning of type block
     *    field( # beginning of field args block
     *      arg1: String
     *      arg2: String
     *    )
     *  }
     */
    public Optional<Comment> getBeginningOfBlockComment(Node<?> node, String prefix) {
        final ParserRuleContext ctx = nodeToRuleMap.get(node);

        final Token start = ctx.start;

        if (start != null) {
            return this.tokens.getTokens(start.getTokenIndex(), ctx.stop.getTokenIndex()).stream()
                    .filter(token -> token.getText().equals(prefix))
                    .findFirst()
                    .map(token -> tokens.getHiddenTokensToRight(token.getTokenIndex(), CHANNEL_COMMENTS))
                    .map(commentTokens -> getCommentOnChannel(commentTokens, isNotPrecededByLineBreak))
                    .flatMap(comments -> comments.stream().findFirst());
        }

        return Optional.empty();
    }

    /*
     * type MyType {
     *   a(
     *     arg1: A
     *     arg2: B
     *     # end of field args block comment
     *   ): A
     *   # end of type block comment #1
     *   # end of type block comment #2
     * }
     */
    public List<Comment> getEndOfBlockComments(Node<?> node, String blockSuffix) {
        final ParserRuleContext ctx = nodeToRuleMap.get(node);

        return searchTokenToLeft(ctx.stop, blockSuffix)
                .map(suffixToken -> tokens.getHiddenTokensToLeft(suffixToken.getTokenIndex(), CHANNEL_COMMENTS))
                .map(commentTokens -> getCommentOnChannel(commentTokens, isPrecededByLineBreak))
                .orElse(Collections.emptyList());
    }

    /*
     * type MyType {
     *   a: A # field trailing comment
     * } # type trailing comment
     */
    public Optional<Comment> getTrailingComment(Node<?> node) {
        // Only nodes that can hold descriptions can have trailing comments
        if (!(node instanceof AbstractDescribedNode)) {
            return Optional.empty();
        }
        final ParserRuleContext ctx = nodeToRuleMap.get(node);

        List<Token> rightRefChannel = this.tokens.getHiddenTokensToRight(ctx.stop.getTokenIndex(), CHANNEL_COMMENTS);

        if (rightRefChannel != null) {
            List<Comment> comments = getCommentOnChannel(rightRefChannel, isNotPrecededByLineBreak);

            return comments.stream().findFirst();
        }

        return Optional.empty();
    }

    /*
     * # type leading comment #1
     * # type leading comment #2
     * type MyType {
     *   # field leading comment #1
     *   # field leading comment #2
     *   a: A
     * }
     */
    public List<Comment> getLeadingComments(Node<?> node) {
        final ParserRuleContext ctx = nodeToRuleMap.get(node);

        final Token start = ctx.start;
        if (start != null) {
            int tokPos = start.getTokenIndex();
            List<Token> leftRefChannel = this.tokens.getHiddenTokensToLeft(tokPos, CHANNEL_COMMENTS);
            if (leftRefChannel != null) {
                return getCommentOnChannel(leftRefChannel, isPrecededByLineBreak);
            }
        }

        return Collections.emptyList();
    }

    /*
     * """ Description """
     * # comment after description #1
     * # comment after description #2
     * type MyType {
     *   a: A
     * }
     */
    public List<Comment> getCommentsAfterDescription(Node<?> node) {
        // Early return if node doesn't have a description
        if (!(node instanceof AbstractDescribedNode) ||
                (node instanceof AbstractDescribedNode && ((AbstractDescribedNode) node).getDescription() == null)
        ) {
            return Collections.emptyList();
        }

        final ParserRuleContext ctx = nodeToRuleMap.get(node);

        final Token start = ctx.start;
        if (start != null) {
            List<Token> commentTokens = tokens.getHiddenTokensToRight(start.getTokenIndex(), CHANNEL_COMMENTS);

            if (commentTokens != null) {
                return getCommentOnChannel(commentTokens, isPrecededByLineBreak);
            }
        }

        return Collections.emptyList();
    }

    public Optional<Comment> getCommentOnFirstLineOfDocument(Document node) {
        final ParserRuleContext ctx = nodeToRuleMap.get(node);

        final Token start = ctx.start;
        if (start != null) {
            int tokPos = start.getTokenIndex();
            List<Token> leftRefChannel = this.tokens.getHiddenTokensToLeft(tokPos, CHANNEL_COMMENTS);
            if (leftRefChannel != null) {
                List<Comment> comments = getCommentOnChannel(leftRefChannel, isFirstToken.or(isPrecededOnlyBySpaces));

                return comments.stream().findFirst();
            }
        }

        return Optional.empty();
    }

    public List<Comment> getCommentsAfterAllDefinitions(Document node) {
        final ParserRuleContext ctx = nodeToRuleMap.get(node);

        final Token start = ctx.start;
        if (start != null) {
            List<Token> leftRefChannel = this.tokens.getHiddenTokensToRight(ctx.stop.getTokenIndex(), CHANNEL_COMMENTS);
            if (leftRefChannel != null) {
                return getCommentOnChannel(leftRefChannel,
                        refToken -> Optional.ofNullable(this.tokens.getHiddenTokensToLeft(refToken.getTokenIndex(), -1))
                                .map(hiddenTokens -> hiddenTokens.stream().anyMatch(token -> token.getText().equals("\n")))
                                .orElse(false)
                );
            }
        }

        return Collections.emptyList();
    }

    protected List<Comment> getCommentOnChannel(List<Token> refChannel, Predicate<Token> shouldIncludePredicate) {
        ImmutableList.Builder<Comment> comments = ImmutableList.builder();
        for (Token refTok : refChannel) {
            String text = refTok.getText();
            // we strip the leading hash # character but we don't trim because we don't
            // know the "comment markup".  Maybe it's space sensitive, maybe it's not.  So
            // consumers can decide that
            if (text == null) {
                continue;
            }

            boolean shouldIncludeComment = shouldIncludePredicate.test(refTok);

            if (!shouldIncludeComment) {
                continue;
            }

            text = text.replaceFirst("^#", "");

            comments.add(new Comment(text, null));
        }
        return comments.build();
    }

    private Optional<Token> searchTokenToLeft(Token token, String text) {
        int i = token.getTokenIndex();

        while (i > 0) {
            Token t = tokens.get(i);
            if (t.getText().equals(text)) {
                return Optional.of(t);
            }
            i--;
        }

        return Optional.empty();
    }

    private final Predicate<Token> alwaysTrue = token -> true;

    private final Predicate<Token> isNotPrecededByLineBreak = refToken ->
            Optional.ofNullable(tokens.getHiddenTokensToLeft(refToken.getTokenIndex(), -1))
                    .map(hiddenTokens -> hiddenTokens.stream().noneMatch(token -> token.getText().equals("\n")))
                    .orElse(false);

    private final Predicate<Token> isPrecededByLineBreak = refToken ->
            Optional.ofNullable(this.tokens.getHiddenTokensToLeft(refToken.getTokenIndex(), -1))
                    .map(hiddenTokens -> hiddenTokens.stream().anyMatch(token -> token.getText().equals("\n")))
                    .orElse(false);

    private final Predicate<Token> isFirstToken = refToken -> refToken.getTokenIndex() == 0;

    private final Predicate<Token> isPrecededOnlyBySpaces = refToken ->
            Optional.ofNullable(this.tokens.getTokens(0, refToken.getTokenIndex() - 1))
                    .map(beforeTokens -> beforeTokens.stream().allMatch(token -> token.getText().equals(" ")))
                    .orElse(false);

}

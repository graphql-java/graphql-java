package graphql.parser;

import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.parser.antlr.GraphqlLexer;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;

import static graphql.Assert.assertShouldNeverHappen;

/**
 * This is a trimmed down version of {@link Parser}. Rather than parsing a entire document, this will only parse type name
 * See Also:
 *
 * @see {@link graphql.parser.Parser}
 */
public class TypeParser {

    public static Type createType(String typeName) {

        CodePointCharStream charStream = CharStreams.fromString(typeName);

        GraphqlLexer lexer = new GraphqlLexer(charStream);

        TokenStream tokenStream = new CommonTokenStream(lexer);
        GraphqlParser graphqlParser = new GraphqlParser(tokenStream);
        GraphqlParser.TypeContext typeContext = graphqlParser.type();
        return createType(typeContext);
    }

    private static Type createType(GraphqlParser.TypeContext ctx) {
        if (ctx.typeName() != null) {
            return createTypeName(ctx.typeName());
        } else if (ctx.nonNullType() != null) {
            return createNonNullType(ctx.nonNullType());
        } else if (ctx.listType() != null) {
            return createListType(ctx.listType());
        } else {
            return assertShouldNeverHappen();
        }
    }

    private static TypeName createTypeName(GraphqlParser.TypeNameContext ctx) {
        TypeName.Builder builder = TypeName.newTypeName();
        builder.name(ctx.name().getText());
        return builder.build();
    }

    private static NonNullType createNonNullType(GraphqlParser.NonNullTypeContext ctx) {
        NonNullType.Builder builder = NonNullType.newNonNullType();
        if (ctx.listType() != null) {
            builder.type(createListType(ctx.listType()));
        } else if (ctx.typeName() != null) {
            builder.type(createTypeName(ctx.typeName()));
        } else {
            return assertShouldNeverHappen();
        }
        return builder.build();
    }

    private static ListType createListType(GraphqlParser.ListTypeContext ctx) {
        ListType.Builder builder = ListType.newListType();
        builder.type(createType(ctx.type()));
        return builder.build();
    }
}

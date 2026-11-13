package graphql.language;

import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

/**
 * The result of creating an input-aware AST signature.
 */
@PublicApi
@NullMarked
public class AstSignatureWithInputResult {

    private final Document document;
    private final List<String> fieldCoordinates;
    private final List<String> usedDirectives;
    private final List<String> fieldArgumentCoordinates;
    private final List<String> directiveArgumentCoordinates;
    private final List<String> inputObjectFieldCoordinates;

    public AstSignatureWithInputResult(
            Document document,
            List<String> fieldCoordinates,
            List<String> usedDirectives,
            List<String> fieldArgumentCoordinates,
            List<String> directiveArgumentCoordinates,
            List<String> inputObjectFieldCoordinates
    ) {
        this.document = assertNotNull(document);
        this.fieldCoordinates = ImmutableKit.nonNullCopyOf(fieldCoordinates);
        this.usedDirectives = ImmutableKit.nonNullCopyOf(usedDirectives);
        this.fieldArgumentCoordinates = ImmutableKit.nonNullCopyOf(fieldArgumentCoordinates);
        this.directiveArgumentCoordinates = ImmutableKit.nonNullCopyOf(directiveArgumentCoordinates);
        this.inputObjectFieldCoordinates = ImmutableKit.nonNullCopyOf(inputObjectFieldCoordinates);
    }

    public Document getDocument() {
        return document;
    }

    public List<String> getFieldCoordinates() {
        return fieldCoordinates;
    }

    public List<String> getUsedDirectives() {
        return usedDirectives;
    }

    public List<String> getFieldArgumentCoordinates() {
        return fieldArgumentCoordinates;
    }

    public List<String> getDirectiveArgumentCoordinates() {
        return directiveArgumentCoordinates;
    }

    public List<String> getInputObjectFieldCoordinates() {
        return inputObjectFieldCoordinates;
    }

    public AstSignatureWithInputResult transform(Consumer<Builder> builderConsumer) {
        Builder builder = newAstSignatureWithInputResult().from(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newAstSignatureWithInputResult() {
        return new Builder();
    }

    @NullUnmarked
    public static class Builder {
        private @Nullable Document document;
        private List<String> fieldCoordinates = ImmutableKit.emptyList();
        private List<String> usedDirectives = ImmutableKit.emptyList();
        private List<String> fieldArgumentCoordinates = ImmutableKit.emptyList();
        private List<String> directiveArgumentCoordinates = ImmutableKit.emptyList();
        private List<String> inputObjectFieldCoordinates = ImmutableKit.emptyList();

        public Builder document(Document document) {
            this.document = document;
            return this;
        }

        public Builder fieldCoordinates(List<String> fieldCoordinates) {
            this.fieldCoordinates = fieldCoordinates;
            return this;
        }

        public Builder usedDirectives(List<String> usedDirectives) {
            this.usedDirectives = usedDirectives;
            return this;
        }

        public Builder fieldArgumentCoordinates(List<String> fieldArgumentCoordinates) {
            this.fieldArgumentCoordinates = fieldArgumentCoordinates;
            return this;
        }

        public Builder directiveArgumentCoordinates(List<String> directiveArgumentCoordinates) {
            this.directiveArgumentCoordinates = directiveArgumentCoordinates;
            return this;
        }

        public Builder inputObjectFieldCoordinates(List<String> inputObjectFieldCoordinates) {
            this.inputObjectFieldCoordinates = inputObjectFieldCoordinates;
            return this;
        }

        public Builder from(AstSignatureWithInputResult result) {
            return document(result.getDocument())
                    .fieldCoordinates(result.getFieldCoordinates())
                    .usedDirectives(result.getUsedDirectives())
                    .fieldArgumentCoordinates(result.getFieldArgumentCoordinates())
                    .directiveArgumentCoordinates(result.getDirectiveArgumentCoordinates())
                    .inputObjectFieldCoordinates(result.getInputObjectFieldCoordinates());
        }

        public AstSignatureWithInputResult build() {
            return new AstSignatureWithInputResult(
                    assertNotNull(document),
                    fieldCoordinates,
                    usedDirectives,
                    fieldArgumentCoordinates,
                    directiveArgumentCoordinates,
                    inputObjectFieldCoordinates
            );
        }
    }
}

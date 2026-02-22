package graphql.execution.instrumentation;

import graphql.PublicApi;
import graphql.collect.ImmutableMapWithNullValues;
import graphql.language.Document;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

@PublicApi
@NullMarked
public class DocumentAndVariables {
    private final Document document;
    private final ImmutableMapWithNullValues<String, Object> variables;

    private DocumentAndVariables(Document document, Map<String, Object> variables) {
        this.document = assertNotNull(document);
        this.variables = ImmutableMapWithNullValues.copyOf(assertNotNull(variables));
    }

    public Document getDocument() {
        return document;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public DocumentAndVariables transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder().document(this.document).variables(this.variables);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newDocumentAndVariables() {
        return new Builder();
    }

    @NullUnmarked
    public static class Builder {
        private Document document;
        private Map<String, Object> variables;

        public Builder document(Document document) {
            this.document = document;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public DocumentAndVariables build() {
            return new DocumentAndVariables(document, variables);
        }
    }
}

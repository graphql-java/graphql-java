package graphql.normalized;

import graphql.PublicApi;
import graphql.language.Document;
import graphql.language.Field;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

/**
 * An immutable, variable-independent selection graph produced during validation.
 * IDs index {@link #getSelectionSets()}; named fragment bodies are stored once.
 * This object is valid only for the document and schema against which it was validated.
 * It contains no coerced arguments or execution-specific state.
 */
@PublicApi
@NullMarked
public final class NormalizedDocument implements Serializable {

    /**
     * Boolean GraphQLContext key enabling construction during validation. Default: false.
     * Preparsed document caches must distinguish enabled and disabled entries and retain this artifact.
     */
    public static final String ENABLED = "graphql.normalizedDocument.enabled";

    private final Document document;
    private final List<NormalizedOperation> operations;
    private final List<NormalizedSelectionSet> selectionSets;
    private final Map<Field, NormalizedField> fields;

    private NormalizedDocument(Builder builder) {
        this.document = assertNotNull(builder.document);
        this.operations = List.copyOf(builder.operations);
        this.selectionSets = List.copyOf(builder.selectionSets);
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(builder.fields));
    }

    /** @return the validated source document */
    public Document getDocument() {
        return document;
    }

    /** @return all operations in document order */
    public List<NormalizedOperation> getOperations() {
        return operations;
    }

    /** @return the shared selection sets indexed by ID */
    public List<NormalizedSelectionSet> getSelectionSets() {
        return selectionSets;
    }

    /** @return stored fields indexed by their original AST nodes */
    public Map<Field, NormalizedField> getFields() {
        return fields;
    }

    public static Builder newNormalizedDocument() {
        return new Builder();
    }

    public NormalizedDocument transform(Consumer<Builder> consumer) {
        Builder builder = new Builder();
        builder.document = document;
        builder.operations = operations;
        builder.selectionSets = selectionSets;
        builder.fields = fields;
        consumer.accept(builder);
        return builder.build();
    }

    @NullUnmarked
    public static class Builder {
        private Document document;
        private List<NormalizedOperation> operations = List.of();
        private List<NormalizedSelectionSet> selectionSets = List.of();
        private Map<Field, NormalizedField> fields = Collections.emptyMap();

        public Builder document(Document document) {
            this.document = document;
            return this;
        }

        public Builder operations(List<NormalizedOperation> operations) {
            this.operations = operations;
            return this;
        }

        public Builder selectionSets(List<NormalizedSelectionSet> selectionSets) {
            this.selectionSets = selectionSets;
            return this;
        }

        public Builder fields(Map<Field, NormalizedField> fields) {
            this.fields = fields;
            return this;
        }

        public NormalizedDocument build() {
            return new NormalizedDocument(this);
        }
    }
}

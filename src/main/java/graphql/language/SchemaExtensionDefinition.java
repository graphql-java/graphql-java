package graphql.language;

import com.google.common.collect.ImmutableList;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;

@PublicApi
@NullMarked
public class SchemaExtensionDefinition extends SchemaDefinition implements SDLExtensionDefinition {

    protected SchemaExtensionDefinition(List<Directive> directives,
                                        List<OperationTypeDefinition> operationTypeDefinitions,
                                        @Nullable SourceLocation sourceLocation,
                                        List<Comment> comments,
                                        IgnoredChars ignoredChars,
                                        Map<String, String> additionalData) {
        super(directives, operationTypeDefinitions, sourceLocation, comments, ignoredChars, additionalData, null);
    }

    @Override
    public SchemaExtensionDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transformExtension(builder -> builder
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
                .operationTypeDefinitions(newChildren.getChildren(CHILD_OPERATION_TYPE_DEFINITIONS))
        );
    }

    @Override
    public SchemaExtensionDefinition deepCopy() {
        return new SchemaExtensionDefinition(assertNotNull(deepCopy(getDirectives())), assertNotNull(deepCopy(getOperationTypeDefinitions())), getSourceLocation(), getComments(),
                getIgnoredChars(), getAdditionalData());
    }

    @Override
    public String toString() {
        return "SchemaExtensionDefinition{" +
                "directives=" + getDirectives() +
                ", operationTypeDefinitions=" + getOperationTypeDefinitions() +
                "}";
    }

    public SchemaExtensionDefinition transformExtension(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newSchemaExtensionDefinition() {
        return new Builder();
    }

    @NullUnmarked
    public static final class Builder implements NodeDirectivesBuilder {
        private SourceLocation sourceLocation;
        private ImmutableList<Comment> comments = emptyList();
        private ImmutableList<Directive> directives = emptyList();
        private ImmutableList<OperationTypeDefinition> operationTypeDefinitions = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();


        protected Builder() {
        }

        protected Builder(SchemaDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.operationTypeDefinitions = ImmutableList.copyOf(existing.getOperationTypeDefinitions());
            this.ignoredChars = existing.getIgnoredChars();
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
        }


        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = ImmutableList.copyOf(comments);
            return this;
        }

        @Override
        public Builder directives(List<Directive> directives) {
            this.directives = ImmutableList.copyOf(directives);
            return this;
        }

        public Builder directive(Directive directive) {
            this.directives = ImmutableKit.addToList(directives, directive);
            return this;
        }

        public Builder operationTypeDefinitions(List<OperationTypeDefinition> operationTypeDefinitions) {
            this.operationTypeDefinitions = ImmutableList.copyOf(operationTypeDefinitions);
            return this;
        }

        public Builder operationTypeDefinition(OperationTypeDefinition operationTypeDefinition) {
            this.operationTypeDefinitions = ImmutableKit.addToList(operationTypeDefinitions, operationTypeDefinition);
            return this;
        }

        public Builder ignoredChars(IgnoredChars ignoredChars) {
            this.ignoredChars = ignoredChars;
            return this;
        }

        public Builder additionalData(Map<String, String> additionalData) {
            this.additionalData = assertNotNull(additionalData);
            return this;
        }

        public Builder additionalData(String key, String value) {
            this.additionalData.put(key, value);
            return this;
        }

        public SchemaExtensionDefinition build() {
            return new SchemaExtensionDefinition(directives,
                    operationTypeDefinitions,
                    sourceLocation,
                    comments,
                    ignoredChars,
                    additionalData);
        }
    }

}

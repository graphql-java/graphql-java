package graphql.language;


import com.google.common.collect.ImmutableList;
import graphql.Internal;
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
import static graphql.collect.ImmutableKit.emptyMap;

@PublicApi
@NullMarked
public class ObjectTypeExtensionDefinition extends ObjectTypeDefinition implements SDLExtensionDefinition {

    @Internal
    protected ObjectTypeExtensionDefinition(String name,
                                            List<Type> implementz,
                                            List<Directive> directives,
                                            List<FieldDefinition> fieldDefinitions,
                                            @Nullable Description description,
                                            @Nullable SourceLocation sourceLocation,
                                            List<Comment> comments,
                                            IgnoredChars ignoredChars,
                                            Map<String, String> additionalData) {
        super(name, implementz, directives, fieldDefinitions,
                description, sourceLocation, comments, ignoredChars, additionalData);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name of the object type extension
     */
    public ObjectTypeExtensionDefinition(String name) {
        this(name, emptyList(), emptyList(), emptyList(), null, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    @Override
    public ObjectTypeExtensionDefinition deepCopy() {
        return new ObjectTypeExtensionDefinition(getName(),
                assertNotNull(deepCopy(getImplements()), "implementz deepCopy should not return null"),
                assertNotNull(deepCopy(getDirectives()), "directives deepCopy should not return null"),
                assertNotNull(deepCopy(getFieldDefinitions()), "fieldDefinitions deepCopy should not return null"),
                getDescription(),
                getSourceLocation(),
                getComments(),
                getIgnoredChars(),
                getAdditionalData());
    }

    @Override
    public ObjectTypeExtensionDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transformExtension(builder -> builder.implementz(newChildren.getChildren(CHILD_IMPLEMENTZ))
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
                .fieldDefinitions(newChildren.getChildren(CHILD_FIELD_DEFINITIONS)));
    }

    @Override
    public String toString() {
        return "ObjectTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", implements=" + getImplements() +
                ", directives=" + getDirectives() +
                ", fieldDefinitions=" + getFieldDefinitions() +
                '}';
    }

    public static Builder newObjectTypeExtensionDefinition() {
        return new Builder();
    }

    public ObjectTypeExtensionDefinition transformExtension(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @NullUnmarked
    public static final class Builder implements NodeDirectivesBuilder {
        private SourceLocation sourceLocation;
        private ImmutableList<Comment> comments = emptyList();
        private String name;
        private Description description;
        private ImmutableList<Type> implementz = emptyList();
        private ImmutableList<Directive> directives = emptyList();
        private ImmutableList<FieldDefinition> fieldDefinitions = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(ObjectTypeDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.implementz = ImmutableList.copyOf(existing.getImplements());
            this.fieldDefinitions = ImmutableList.copyOf(existing.getFieldDefinitions());
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

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(Description description) {
            this.description = description;
            return this;
        }

        public Builder implementz(List<Type> implementz) {
            this.implementz = ImmutableList.copyOf(implementz);
            return this;
        }

        public Builder implementz(Type implement) {
            this.implementz = ImmutableKit.addToList(implementz, implement);
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

        public Builder fieldDefinitions(List<FieldDefinition> fieldDefinitions) {
            this.fieldDefinitions = ImmutableList.copyOf(fieldDefinitions);
            return this;
        }

        public Builder fieldDefinition(FieldDefinition fieldDefinition) {
            this.fieldDefinitions = ImmutableKit.addToList(fieldDefinitions, fieldDefinition);
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

        public ObjectTypeExtensionDefinition build() {
            return new ObjectTypeExtensionDefinition(name,
                    implementz,
                    directives,
                    fieldDefinitions,
                    description,
                    sourceLocation,
                    comments,
                    ignoredChars, additionalData);
        }
    }
}

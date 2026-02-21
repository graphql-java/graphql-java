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

@PublicApi
@NullMarked
public class InterfaceTypeExtensionDefinition extends InterfaceTypeDefinition implements SDLExtensionDefinition {

    @Internal
    protected InterfaceTypeExtensionDefinition(String name,
                                               List<Type> implementz,
                                               List<FieldDefinition> definitions,
                                               List<Directive> directives,
                                               @Nullable Description description,
                                               @Nullable SourceLocation sourceLocation,
                                               List<Comment> comments,
                                               IgnoredChars ignoredChars,
                                               Map<String, String> additionalData) {
        super(name, implementz, definitions, directives, description, sourceLocation, comments, ignoredChars, additionalData);
    }

    @Override
    public InterfaceTypeExtensionDefinition deepCopy() {
        return new InterfaceTypeExtensionDefinition(getName(),
                getImplements(),
                assertNotNull(deepCopy(getFieldDefinitions()), "fieldDefinitions cannot be null"),
                assertNotNull(deepCopy(getDirectives()), "directives cannot be null"),
                getDescription(),
                getSourceLocation(),
                getComments(),
                getIgnoredChars(),
                getAdditionalData());
    }

    @Override
    public String toString() {
        return "InterfaceTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", fieldDefinitions=" + getFieldDefinitions() +
                ", directives=" + getDirectives() +
                '}';

    }

    public static Builder newInterfaceTypeExtensionDefinition() {
        return new Builder();
    }

    @Override
    public InterfaceTypeExtensionDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transformExtension(builder -> builder
                .definitions(newChildren.getChildren(CHILD_DEFINITIONS))
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
        );
    }

    public InterfaceTypeExtensionDefinition transformExtension(Consumer<Builder> builderConsumer) {
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
        private ImmutableList<FieldDefinition> definitions = emptyList();
        private ImmutableList<Directive> directives = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(InterfaceTypeExtensionDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.implementz = ImmutableList.copyOf(existing.getImplements());
            this.definitions = ImmutableList.copyOf(existing.getFieldDefinitions());
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

        public Builder implementz(Type implementz) {
            this.implementz = ImmutableKit.addToList(this.implementz, implementz);
            return this;
        }

        public Builder definitions(List<FieldDefinition> definitions) {
            this.definitions = ImmutableList.copyOf(definitions);
            return this;
        }

        public Builder definition(FieldDefinition definition) {
            this.definitions = ImmutableKit.addToList(definitions, definition);
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


        public InterfaceTypeExtensionDefinition build() {
            return new InterfaceTypeExtensionDefinition(name,
                    implementz,
                    definitions,
                    directives,
                    description,
                    sourceLocation,
                    comments,
                    ignoredChars,
                    additionalData);
        }
    }
}

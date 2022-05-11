package graphql.language;

import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;

@PublicApi
public class EnumTypeExtensionDefinition extends EnumTypeDefinition implements SDLExtensionDefinition {

    @Internal
    protected EnumTypeExtensionDefinition(String name,
                                          List<EnumValueDefinition> enumValueDefinitions,
                                          List<Directive> directives,
                                          Description description,
                                          SourceLocation sourceLocation,
                                          List<Comment> comments,
                                          IgnoredChars ignoredChars,
                                          Map<String, String> additionalData) {
        super(name, enumValueDefinitions, directives, description,
                sourceLocation, comments, ignoredChars, additionalData);
    }

    @Override
    public EnumTypeExtensionDefinition deepCopy() {
        return new EnumTypeExtensionDefinition(getName(),
                deepCopy(getEnumValueDefinitions()),
                deepCopy(getDirectives()),
                getDescription(),
                getSourceLocation(),
                getComments(),
                getIgnoredChars(), getAdditionalData());
    }

    @Override
    public String toString() {
        return "EnumTypeDefinition{" +
                "name='" + getName() + '\'' +
                ", enumValueDefinitions=" + getEnumValueDefinitions() +
                ", directives=" + getDirectives() +
                '}';
    }

    public static Builder newEnumTypeExtensionDefinition() {
        return new Builder();
    }

    @Override
    public EnumTypeExtensionDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transformExtension(builder -> builder
                .enumValueDefinitions(newChildren.getChildren(CHILD_ENUM_VALUE_DEFINITIONS))
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
        );
    }

    public EnumTypeExtensionDefinition transformExtension(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeDirectivesBuilder {
        private SourceLocation sourceLocation;
        private ImmutableList<Comment> comments = emptyList();
        private String name;
        private Description description;
        private ImmutableList<EnumValueDefinition> enumValueDefinitions = emptyList();
        private ImmutableList<Directive> directives = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(EnumTypeExtensionDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.enumValueDefinitions = ImmutableList.copyOf(existing.getEnumValueDefinitions());
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

        public Builder enumValueDefinitions(List<EnumValueDefinition> enumValueDefinitions) {
            this.enumValueDefinitions = ImmutableList.copyOf(enumValueDefinitions);
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


        public EnumTypeExtensionDefinition build() {
            return new EnumTypeExtensionDefinition(name,
                    enumValueDefinitions,
                    directives,
                    description,
                    sourceLocation,
                    comments,
                    ignoredChars, additionalData);
        }
    }

}

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
public class InputObjectTypeExtensionDefinition extends InputObjectTypeDefinition implements SDLExtensionDefinition {

    @Internal
    protected InputObjectTypeExtensionDefinition(String name,
                                                 List<Directive> directives,
                                                 List<InputValueDefinition> inputValueDefinitions,
                                                 @Nullable Description description,
                                                 @Nullable SourceLocation sourceLocation,
                                                 List<Comment> comments,
                                                 IgnoredChars ignoredChars,
                                                 Map<String, String> additionalData) {
        super(name, directives, inputValueDefinitions, description, sourceLocation, comments, ignoredChars, additionalData);
    }

    @Override
    public InputObjectTypeExtensionDefinition deepCopy() {
        return new InputObjectTypeExtensionDefinition(getName(),
                assertNotNull(deepCopy(getDirectives()), "directives cannot be null"),
                assertNotNull(deepCopy(getInputValueDefinitions()), "inputValueDefinitions cannot be null"),
                getDescription(),
                getSourceLocation(),
                getComments(),
                getIgnoredChars(),
                getAdditionalData());
    }

    @Override
    public String toString() {
        return "InputObjectTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", directives=" + getDirectives() +
                ", inputValueDefinitions=" + getInputValueDefinitions() +
                '}';
    }

    public static Builder newInputObjectTypeExtensionDefinition() {
        return new Builder();
    }

    @Override
    public InputObjectTypeExtensionDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transformExtension(builder -> builder
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
                .inputValueDefinitions(newChildren.getChildren(CHILD_INPUT_VALUES_DEFINITIONS))
        );
    }

    public InputObjectTypeExtensionDefinition transformExtension(Consumer<Builder> builderConsumer) {
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
        private ImmutableList<Directive> directives = emptyList();
        private ImmutableList<InputValueDefinition> inputValueDefinitions = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(InputObjectTypeDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.inputValueDefinitions = ImmutableList.copyOf(existing.getInputValueDefinitions());
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

        @Override
        public Builder directives(List<Directive> directives) {
            this.directives = ImmutableList.copyOf(directives);
            return this;
        }

        public Builder directive(Directive directive) {
            this.directives = ImmutableKit.addToList(directives, directive);
            return this;
        }


        public Builder inputValueDefinitions(List<InputValueDefinition> inputValueDefinitions) {
            this.inputValueDefinitions = ImmutableList.copyOf(inputValueDefinitions);
            return this;
        }

        public Builder inputValueDefinition(InputValueDefinition inputValueDefinition) {
            this.inputValueDefinitions = ImmutableKit.addToList(inputValueDefinitions, inputValueDefinition);
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


        public InputObjectTypeExtensionDefinition build() {
            InputObjectTypeExtensionDefinition inputObjectTypeDefinition = new InputObjectTypeExtensionDefinition(name,
                    directives,
                    inputValueDefinitions,
                    description,
                    sourceLocation,
                    comments,
                    ignoredChars, additionalData);
            return inputObjectTypeDefinition;
        }
    }

}

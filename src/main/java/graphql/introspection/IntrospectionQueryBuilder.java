package graphql.introspection;

import com.google.common.collect.ImmutableList;
import graphql.PublicApi;
import graphql.language.AstPrinter;
import graphql.language.BooleanValue;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static graphql.language.Argument.newArgument;
import static graphql.language.Document.newDocument;
import static graphql.language.Field.newField;
import static graphql.language.FragmentDefinition.newFragmentDefinition;
import static graphql.language.FragmentSpread.newFragmentSpread;
import static graphql.language.OperationDefinition.newOperationDefinition;
import static graphql.language.SelectionSet.newSelectionSet;
import static graphql.language.TypeName.newTypeName;
import static java.util.stream.Collectors.toList;

/**
 * {@link IntrospectionQueryBuilder} allows you to build introspection queries controlled
 * by the options you specify
 */
@PublicApi
public class IntrospectionQueryBuilder {
    public static class Options {

        private final boolean descriptions;

        private final boolean specifiedByUrl;
        private final boolean isOneOf;

        private final boolean directiveIsRepeatable;

        private final boolean schemaDescription;

        private final boolean inputValueDeprecation;

        private final int typeRefFragmentDepth;

        private Options(boolean descriptions,
                        boolean specifiedByUrl,
                        boolean isOneOf,
                        boolean directiveIsRepeatable,
                        boolean schemaDescription,
                        boolean inputValueDeprecation,
                        int typeRefFragmentDepth) {
            this.descriptions = descriptions;
            this.specifiedByUrl = specifiedByUrl;
            this.isOneOf = isOneOf;
            this.directiveIsRepeatable = directiveIsRepeatable;
            this.schemaDescription = schemaDescription;
            this.inputValueDeprecation = inputValueDeprecation;
            this.typeRefFragmentDepth = typeRefFragmentDepth;
        }

        public boolean isDescriptions() {
            return descriptions;
        }

        public boolean isSpecifiedByUrl() {
            return specifiedByUrl;
        }

        public boolean isOneOf() {
            return isOneOf;
        }

        public boolean isDirectiveIsRepeatable() {
            return directiveIsRepeatable;
        }

        public boolean isSchemaDescription() {
            return schemaDescription;
        }

        public boolean isInputValueDeprecation() {
            return inputValueDeprecation;
        }

        public int getTypeRefFragmentDepth() {
            return typeRefFragmentDepth;
        }

        public static Options defaultOptions() {
            return new Options(
                    true,
                    false,
                    true,
                    true,
                    false,
                    true,
                    7
            );
        }

        /**
         * This will allow you to include description fields in the introspection query
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options descriptions(boolean flag) {
            return new Options(flag,
                    this.specifiedByUrl,
                    this.isOneOf,
                    this.directiveIsRepeatable,
                    this.schemaDescription,
                    this.inputValueDeprecation,
                    this.typeRefFragmentDepth);
        }

        /**
         * This will allow you to include the `specifiedByURL` field for scalar types in the introspection query.
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options specifiedByUrl(boolean flag) {
            return new Options(this.descriptions,
                    flag,
                    this.isOneOf,
                    this.directiveIsRepeatable,
                    this.schemaDescription,
                    this.inputValueDeprecation,
                    this.typeRefFragmentDepth);
        }

        /**
         * This will allow you to include the `isOneOf` field for one of input types in the introspection query.
         * <p>
         * This option is only needed while `@oneOf` input types are new and in time the reason for this
         * option will go away.
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options isOneOf(boolean flag) {
            return new Options(this.descriptions,
                    this.specifiedByUrl,
                    flag,
                    this.directiveIsRepeatable,
                    this.schemaDescription,
                    this.inputValueDeprecation,
                    this.typeRefFragmentDepth);
        }
        /**
         * This will allow you to include the `isRepeatable` field for directives in the introspection query.
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options directiveIsRepeatable(boolean flag) {
            return new Options(this.descriptions,
                    this.specifiedByUrl,
                    this.isOneOf,
                    flag,
                    this.schemaDescription,
                    this.inputValueDeprecation,
                    this.typeRefFragmentDepth);
        }

        /**
         * This will allow you to include the `description` field for the schema type in the introspection query.
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options schemaDescription(boolean flag) {
            return new Options(this.descriptions,
                    this.specifiedByUrl,
                    this.isOneOf,
                    this.directiveIsRepeatable,
                    flag,
                    this.inputValueDeprecation,
                    this.typeRefFragmentDepth);
        }

        /**
         * This will allow you to include deprecated input fields in the introspection query.
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options inputValueDeprecation(boolean flag) {
            return new Options(this.descriptions,
                    this.specifiedByUrl,
                    this.isOneOf,
                    this.directiveIsRepeatable,
                    this.schemaDescription,
                    flag,
                    this.typeRefFragmentDepth);
        }

        /**
         * This will allow you to control the depth of the `TypeRef` fragment in the introspection query.
         *
         * @param typeRefFragmentDepth the depth of the `TypeRef` fragment.
         *
         * @return options
         */
        public Options typeRefFragmentDepth(int typeRefFragmentDepth) {
            return new Options(this.descriptions,
                    this.specifiedByUrl,
                    this.isOneOf,
                    this.directiveIsRepeatable,
                    this.schemaDescription,
                    this.inputValueDeprecation,
                    typeRefFragmentDepth);
        }
    }

    @SafeVarargs
    private static <T> List<T> filter(T... args) {
        return Arrays.stream(args).filter(Objects::nonNull).collect(toList());
    }

    /**
     * This will build an introspection query in {@link Document} form
     *
     * @param options the options to use
     *
     * @return an introspection query in document form
     */
    public static Document buildDocument(Options options) {
        SelectionSet schemaSelectionSet = newSelectionSet().selections(filter(
                        options.schemaDescription ? newField("description").build() : null,
                        newField("queryType", newSelectionSet()
                                .selection(newField("name").build())
                                .build()
                        )
                                .build(),
                        newField("mutationType", newSelectionSet()
                                .selection(newField("name").build())
                                .build()
                        )
                                .build(),
                        newField("subscriptionType", newSelectionSet()
                                .selection(newField("name").build())
                                .build()
                        )
                                .build(),
                        newField("types", newSelectionSet()
                                .selection(newFragmentSpread("FullType").build())
                                .build()
                        )
                                .build(),
                        newField("directives", newSelectionSet().selections(filter(
                                                newField("name").build(),
                                                options.descriptions ? newField("description").build() : null,
                                                newField("locations").build(),
                                                newField("args")
                                                        .arguments(filter(
                                                                options.inputValueDeprecation ? newArgument("includeDeprecated", BooleanValue.of(true)).build() : null
                                                        ))
                                                        .selectionSet(newSelectionSet()
                                                                .selection(newFragmentSpread("InputValue").build())
                                                                .build()
                                                        )
                                                        .build(),
                                                options.directiveIsRepeatable ? newField("isRepeatable").build() : null
                                        ))
                                        .build()
                        )
                                .build()
                )
        ).build();

        SelectionSet fullTypeSelectionSet = newSelectionSet().selections(filter(
                newField("kind").build(),
                newField("name").build(),
                options.descriptions ? newField("description").build() : null,
                options.specifiedByUrl ? newField("specifiedByURL").build() : null,
                options.isOneOf ? newField("isOneOf").build() : null,
                newField("fields")
                        .arguments(ImmutableList.of(
                                newArgument("includeDeprecated", BooleanValue.of(true)).build()
                        ))
                        .selectionSet(newSelectionSet().selections(filter(
                                        newField("name").build(),
                                        options.descriptions ? newField("description").build() : null,
                                        newField("args")
                                                .arguments(filter(
                                                        options.inputValueDeprecation ? newArgument("includeDeprecated", BooleanValue.of(true)).build() : null
                                                ))
                                                .selectionSet(newSelectionSet()
                                                        .selection(newFragmentSpread("InputValue").build())
                                                        .build()
                                                )
                                                .build(),
                                        newField("type", newSelectionSet()
                                                .selection(newFragmentSpread("TypeRef").build())
                                                .build()
                                        )
                                                .build(),
                                        newField("isDeprecated").build(),
                                        newField("deprecationReason").build()
                                )).build()
                        )
                        .build(),
                newField("inputFields")
                        .arguments(filter(
                                options.inputValueDeprecation ? newArgument("includeDeprecated", BooleanValue.of(true)).build() : null
                        ))
                        .selectionSet(newSelectionSet()
                                .selection(newFragmentSpread("InputValue").build())
                                .build()
                        )
                        .build(),
                newField("interfaces", newSelectionSet()
                        .selection(newFragmentSpread("TypeRef").build())
                        .build()
                )
                        .build(),
                newField("enumValues")
                        .arguments(ImmutableList.of(
                                newArgument("includeDeprecated", BooleanValue.of(true)).build()
                        ))
                        .selectionSet(newSelectionSet().selections(filter(
                                                newField("name").build(),
                                                options.descriptions ? newField("description").build() : null,
                                                newField("isDeprecated").build(),
                                                newField("deprecationReason").build()
                                        ))
                                        .build()
                        )
                        .build(),
                newField("possibleTypes", newSelectionSet()
                        .selection(newFragmentSpread("TypeRef").build())
                        .build()
                )
                        .build()
        )).build();

        SelectionSet inputValueSelectionSet = newSelectionSet().selections(filter(
                newField("name").build(),
                options.descriptions ? newField("description").build() : null,
                newField("type", newSelectionSet()
                        .selection(newFragmentSpread("TypeRef").build())
                        .build()
                )
                        .build(),
                newField("defaultValue").build(),
                options.inputValueDeprecation ? newField("isDeprecated").build() : null,
                options.inputValueDeprecation ? newField("deprecationReason").build() : null
        )).build();

        SelectionSet typeRefSelectionSet = newSelectionSet().selections(filter(
                newField("kind").build(),
                newField("name").build()
        )).build();

        for (int i = options.typeRefFragmentDepth; i > 0; i -= 1) {
            typeRefSelectionSet = newSelectionSet().selections(filter(
                    newField("kind").build(),
                    newField("name").build(),
                    newField("ofType", typeRefSelectionSet).build()
            )).build();
        }

        return newDocument()
                .definition(newOperationDefinition()
                        .operation(OperationDefinition.Operation.QUERY)
                        .name("IntrospectionQuery")
                        .selectionSet(newSelectionSet()
                                .selection(newField("__schema", schemaSelectionSet).build())
                                .build()
                        )
                        .build()
                )
                .definition(newFragmentDefinition()
                        .name("FullType")
                        .typeCondition(newTypeName().name("__Type").build())
                        .selectionSet(fullTypeSelectionSet)
                        .build()
                )
                .definition(newFragmentDefinition()
                        .name("InputValue")
                        .typeCondition(newTypeName().name("__InputValue").build())
                        .selectionSet(inputValueSelectionSet)
                        .build()
                )
                .definition(newFragmentDefinition()
                        .name("TypeRef")
                        .typeCondition(newTypeName().name("__Type").build())
                        .selectionSet(typeRefSelectionSet)
                        .build()
                )
                .build();
    }

    /**
     * This will build an introspection query in {@link String} form based on the options you provide
     *
     * @param options the options to use
     *
     * @return an introspection query in string form
     */

    public static String build(Options options) {
        return AstPrinter.printAst(buildDocument(options));
    }

    /**
     * This will build a default introspection query in {@link String} form
     *
     * @return an introspection query in string form
     */
    public static String build() {
        return build(Options.defaultOptions());
    }
}
package graphql.introspection;

import com.google.common.collect.ImmutableList;
import graphql.language.Argument;
import graphql.language.AstPrinter;
import graphql.language.BooleanValue;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.TypeName;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class IntrospectionQueryBuilder {
    public static class Options {

        private final boolean descriptions;

        private final boolean specifiedByUrl;

        private final boolean directiveIsRepeatable;

        private final boolean schemaDescription;

        private final boolean inputValueDeprecation;

        private final int typeRefFragmentDepth;

        private Options(boolean descriptions,
                        boolean specifiedByUrl,
                        boolean directiveIsRepeatable,
                        boolean schemaDescription,
                        boolean inputValueDeprecation,
                        int typeRefFragmentDepth) {
            this.descriptions = descriptions;
            this.specifiedByUrl = specifiedByUrl;
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
                this.directiveIsRepeatable,
                this.schemaDescription,
                this.inputValueDeprecation,
                typeRefFragmentDepth);
        }
    }

    private static <T> List<T> filter(T... args) {
        return Arrays.stream(args).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public static String build() {
        return build(Options.defaultOptions());
    }

    public static String build(Options options) {
        SelectionSet schemaSelectionSet = SelectionSet.newSelectionSet().selections( filter(
            options.schemaDescription ? Field.newField("description").build() : null,
            Field.newField("queryType", SelectionSet.newSelectionSet()
                    .selection( Field.newField("name").build() )
                    .build()
                )
                .build(),
            Field.newField("mutationType", SelectionSet.newSelectionSet()
                    .selection( Field.newField("name").build() )
                    .build()
                )
                .build(),
            Field.newField("subscriptionType", SelectionSet.newSelectionSet()
                    .selection( Field.newField("name").build() )
                    .build()
                )
                .build(),
            Field.newField("types", SelectionSet.newSelectionSet()
                    .selection( FragmentSpread.newFragmentSpread("FullType").build() )
                    .build()
                )
                .build(),
            Field.newField("directives", SelectionSet.newSelectionSet().selections( filter(
                        Field.newField("name").build(),
                        options.descriptions ? Field.newField("description").build() : null,
                        Field.newField("locations").build(),
                        Field.newField("args")
                            .arguments( filter(
                                options.inputValueDeprecation ? Argument.newArgument("includeDeprecated", BooleanValue.of(true)).build() : null
                            ) )
                            .selectionSet( SelectionSet.newSelectionSet()
                                .selection( FragmentSpread.newFragmentSpread("InputValue").build() )
                                .build()
                            )
                            .build(),
                        options.directiveIsRepeatable ? Field.newField("isRepeatable").build() : null
                    ) )
                    .build()
                )
                .build()
            )
        ).build();

        SelectionSet fullTypeSelectionSet = SelectionSet.newSelectionSet().selections( filter(
            Field.newField("kind").build(),
            Field.newField("name").build(),
            options.descriptions ? Field.newField("description").build() : null,
            options.specifiedByUrl ? Field.newField("specifiedByURL").build() : null,
            Field.newField("fields")
                .arguments( ImmutableList.of(
                    Argument.newArgument("includeDeprecated", BooleanValue.of(true)).build()
                ) )
                .selectionSet( SelectionSet.newSelectionSet().selections( filter(
                        Field.newField("name").build(),
                        options.descriptions ? Field.newField("description").build() : null,
                        Field.newField("args")
                            .arguments( filter(
                                options.inputValueDeprecation ? Argument.newArgument("includeDeprecated", BooleanValue.of(true)).build() : null
                            ) )
                            .selectionSet( SelectionSet.newSelectionSet()
                                .selection( FragmentSpread.newFragmentSpread("InputValue").build() )
                                .build()
                            )
                            .build(),
                        Field.newField("type", SelectionSet.newSelectionSet()
                                .selection( FragmentSpread.newFragmentSpread("TypeRef").build() )
                                .build()
                            )
                            .build(),
                        Field.newField("isDeprecated").build(),
                        Field.newField("deprecationReason").build()
                    ) ).build()
                )
                .build(),
            Field.newField("inputFields")
                .arguments( filter(
                    options.inputValueDeprecation ? Argument.newArgument("includeDeprecated", BooleanValue.of(true)).build() : null
                ) )
                .selectionSet( SelectionSet.newSelectionSet()
                    .selection( FragmentSpread.newFragmentSpread("InputValue").build() )
                    .build()
                )
                .build(),
            Field.newField("interfaces", SelectionSet.newSelectionSet()
                    .selection( FragmentSpread.newFragmentSpread("TypeRef").build() )
                    .build()
                )
                .build(),
            Field.newField("enumValues")
                .arguments( ImmutableList.of(
                    Argument.newArgument("includeDeprecated", BooleanValue.of(true)).build()
                ) )
                .selectionSet( SelectionSet.newSelectionSet().selections( filter(
                            Field.newField("name").build(),
                            options.descriptions ? Field.newField("description").build() : null,
                            Field.newField("isDeprecated").build(),
                            Field.newField("deprecationReason").build()
                        ) )
                        .build()
                )
                .build(),
            Field.newField("possibleTypes", SelectionSet.newSelectionSet()
                    .selection( FragmentSpread.newFragmentSpread("TypeRef").build() )
                    .build()
                )
                .build()
        ) ).build();

        SelectionSet inputValueSelectionSet = SelectionSet.newSelectionSet().selections( filter(
            Field.newField("name").build(),
            options.descriptions ? Field.newField("description").build() : null,
            Field.newField("type", SelectionSet.newSelectionSet()
                    .selection( FragmentSpread.newFragmentSpread("TypeRef").build() )
                    .build()
                )
                .build(),
            Field.newField("defaultValue").build(),
            options.inputValueDeprecation ? Field.newField("isDeprecated").build() : null,
            options.inputValueDeprecation ? Field.newField("deprecationReason").build() : null
        ) ).build();

        SelectionSet typeRefSelectionSet = SelectionSet.newSelectionSet().selections( filter(
            Field.newField("kind").build(),
            Field.newField("name").build()
        ) ).build();

        for(int i=options.typeRefFragmentDepth; i>0; i-=1) {
            typeRefSelectionSet = SelectionSet.newSelectionSet().selections( filter(
                Field.newField("kind").build(),
                Field.newField("name").build(),
                Field.newField("ofType", typeRefSelectionSet).build()
            ) ).build();
        }

        Document query = Document.newDocument()
            .definition( OperationDefinition.newOperationDefinition()
                .operation(OperationDefinition.Operation.QUERY)
                .name("IntrospectionQuery")
                .selectionSet( SelectionSet.newSelectionSet()
                    .selection( Field.newField("__schema", schemaSelectionSet).build() )
                    .build()
                )
                .build()
            )
            .definition( FragmentDefinition.newFragmentDefinition()
                .name("FullType")
                .typeCondition( TypeName.newTypeName().name("__Type").build() )
                .selectionSet(fullTypeSelectionSet)
                .build()
            )
            .definition( FragmentDefinition.newFragmentDefinition()
                .name("InputValue")
                .typeCondition( TypeName.newTypeName().name("__InputValue").build() )
                .selectionSet(inputValueSelectionSet)
                .build()
            )
            .definition( FragmentDefinition.newFragmentDefinition()
                .name("TypeRef")
                .typeCondition( TypeName.newTypeName().name("__Type").build() )
                .selectionSet(typeRefSelectionSet)
                .build()
            )
            .build();

        return AstPrinter.printAst(query);
    }
}

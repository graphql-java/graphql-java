package graphql.introspection;

import com.google.common.collect.ImmutableList;
import graphql.PublicApi;
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

@PublicApi
public interface IntrospectionQuery { // todo iwds support
    static <T> List<T> filter(T... args) {
        return Arrays.stream(args).filter(Objects::nonNull).collect(Collectors.toList());
    }

    static String getIntrospectionQuery(
        boolean descriptions,
        boolean specifiedByUrl,
        boolean directiveIsRepeatable,
        boolean schemaDescription,
        boolean inputValueDeprecation,
        int typeRefFragmentDepth
    ) {
        SelectionSet schemaSelectionSet = SelectionSet.newSelectionSet().selections( filter(
            schemaDescription ? Field.newField("description").build() : null,
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
                        descriptions ? Field.newField("description").build() : null,
                        Field.newField("locations").build(),
                        Field.newField("args")
                            .arguments( filter(
                                inputValueDeprecation ? Argument.newArgument("includeDeprecated", BooleanValue.of(true)).build() : null
                            ) )
                            .selectionSet( SelectionSet.newSelectionSet()
                                .selection( FragmentSpread.newFragmentSpread("InputValue").build() )
                                .build()
                            )
                            .build(),
                        directiveIsRepeatable ? Field.newField("isRepeatable").build() : null
                    ) )
                    .build()
                )
                .build()
            )
        ).build();

        SelectionSet fullTypeSelectionSet = SelectionSet.newSelectionSet().selections( filter(
            Field.newField("kind").build(),
            Field.newField("name").build(),
            descriptions ? Field.newField("description").build() : null,
            specifiedByUrl ? Field.newField("specifiedByUrl").build() : null,
            Field.newField("fields")
                .arguments( ImmutableList.of(
                    Argument.newArgument("includeDeprecated", BooleanValue.of(true)).build()
                ) )
                .selectionSet( SelectionSet.newSelectionSet().selections( filter(
                        Field.newField("name").build(),
                        descriptions ? Field.newField("description").build() : null,
                        Field.newField("args")
                            .arguments( filter(
                                inputValueDeprecation ? Argument.newArgument("includeDeprecated", BooleanValue.of(true)).build() : null
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
                    inputValueDeprecation ? Argument.newArgument("includeDeprecated", BooleanValue.of(true)).build() : null
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
                        descriptions ? Field.newField("description").build() : null,
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
            descriptions ? Field.newField("description").build() : null,
            Field.newField("type", SelectionSet.newSelectionSet()
                    .selection( FragmentSpread.newFragmentSpread("TypeRef").build() )
                    .build()
                )
                .build(),
            Field.newField("defaultValue").build(),
            inputValueDeprecation ? Field.newField("isDeprecated").build() : null,
            inputValueDeprecation ? Field.newField("deprecationReason").build() : null
        ) ).build();

        SelectionSet typeRefSelectionSet = SelectionSet.newSelectionSet().selections( filter(
            Field.newField("kind").build(),
            Field.newField("name").build()
        ) ).build();

        for(int i=typeRefFragmentDepth; i>0; i-=1) {
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

    String INTROSPECTION_QUERY = getIntrospectionQuery(true, false, true, false, true, 7);
}

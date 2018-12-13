package graphql.language;

import graphql.schema.idl.TypeInfo;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static graphql.language.AstTransformerUtil.changeNode;

public class AstSorter {

    public <T extends Node> T sort(T nodeToBeSorted) {

        NodeVisitorStub visitor = new NodeVisitorStub() {

            @Override
            public TraversalControl visitDocument(Document node, TraverserContext<Node> context) {
                Document changedNode = node.transform(builder -> {
                    List<Definition> definitions = sort(node.getDefinitions(), comparingDefinitions());
                    builder.definitions(definitions);
                });
                return newNode(context, changedNode);
            }

            @Override
            public TraversalControl visitOperationDefinition(OperationDefinition node, TraverserContext<Node> context) {
                OperationDefinition changedNode = node.transform(builder -> {
                    builder.variableDefinitions(sort(node.getVariableDefinitions(), Comparator.comparing(VariableDefinition::getName)));
                    builder.directives(sort(node.getDirectives(), Comparator.comparing(Directive::getName)));
                });
                return newNode(context, changedNode);
            }


            @Override
            public TraversalControl visitField(Field node, TraverserContext<Node> context) {
                Field changedNode = node.transform(builder -> {
                    builder.arguments(sort(node.getArguments(), Comparator.comparing(Argument::getName)));
                    builder.directives(sort(node.getDirectives(), Comparator.comparing(Directive::getName)));
                    builder.selectionSet(sortSelectionSet(node.getSelectionSet()));
                });
                return newNode(context, changedNode);
            }

            @Override
            public TraversalControl visitFragmentDefinition(FragmentDefinition node, TraverserContext<Node> context) {
                FragmentDefinition changedNode = node.transform(builder -> {
                    builder.directives(sort(node.getDirectives(), Comparator.comparing(Directive::getName)));
                    builder.selectionSet(sortSelectionSet(node.getSelectionSet()));
                });
                return newNode(context, changedNode);
            }

            @Override
            public TraversalControl visitInlineFragment(InlineFragment node, TraverserContext<Node> context) {
                InlineFragment changedNode = node.transform(builder -> {
                    builder.directives(sort(node.getDirectives(), Comparator.comparing(Directive::getName)));
                    builder.selectionSet(sortSelectionSet(node.getSelectionSet()));
                });
                return newNode(context, changedNode);
            }

            @Override
            public TraversalControl visitFragmentSpread(FragmentSpread node, TraverserContext<Node> context) {
                FragmentSpread changedNode = node.transform(builder -> {
                    builder.directives(sort(node.getDirectives(), Comparator.comparing(Directive::getName)));
                });
                return newNode(context, changedNode);
            }

            @Override
            public TraversalControl visitDirective(Directive node, TraverserContext<Node> context) {
                Directive changedNode = node.transform(builder -> {
                    builder.arguments(sort(node.getArguments(), Comparator.comparing(Argument::getName)));
                });
                return newNode(context, changedNode);
            }

            // SDL classes here

            @Override
            public TraversalControl visitSchemaDefinition(SchemaDefinition node, TraverserContext<Node> context) {
                SchemaDefinition changedNode = node.transform(builder -> {
                    builder.directives(sort(node.getDirectives(), Comparator.comparing(Directive::getName)));
                    builder.operationTypeDefinitions(sort(node.getOperationTypeDefinitions(), Comparator.comparing(OperationTypeDefinition::getName)));
                });
                return newNode(context, changedNode);
            }

            @Override
            public TraversalControl visitEnumTypeDefinition(EnumTypeDefinition node, TraverserContext<Node> context) {
                EnumTypeDefinition changedNode = node.transform(builder -> {
                    builder.directives(sort(node.getDirectives(), Comparator.comparing(Directive::getName)));
                    builder.enumValueDefinitions(sort(node.getEnumValueDefinitions(), Comparator.comparing(EnumValueDefinition::getName)));
                });
                return newNode(context, changedNode);
            }

            @Override
            public TraversalControl visitScalarTypeDefinition(ScalarTypeDefinition node, TraverserContext<Node> context) {
                ScalarTypeDefinition changedNode = node.transform(builder -> {
                    List<Directive> directives = sort(node.getDirectives(), Comparator.comparing(Directive::getName));
                    builder.directives(directives);
                });
                return newNode(context, changedNode);
            }

            @Override
            public TraversalControl visitInputObjectTypeDefinition(InputObjectTypeDefinition node, TraverserContext<Node> context) {
                InputObjectTypeDefinition changedNode = node.transform(builder -> {
                    builder.directives(sort(node.getDirectives(), Comparator.comparing(Directive::getName)));
                    builder.inputValueDefinitions(sort(node.getInputValueDefinitions(), Comparator.comparing(InputValueDefinition::getName)));
                });
                return newNode(context, changedNode);
            }

            @Override
            public TraversalControl visitObjectTypeDefinition(ObjectTypeDefinition node, TraverserContext<Node> context) {
                ObjectTypeDefinition changedNode = node.transform(builder -> {
                    builder.directives(sort(node.getDirectives(), Comparator.comparing(Directive::getName)));
                    builder.implementz(sort(node.getImplements(), comparingTypes()));
                    builder.fieldDefinitions(sort(node.getFieldDefinitions(), Comparator.comparing(FieldDefinition::getName)));
                });
                return newNode(context, changedNode);
            }

            @Override
            public TraversalControl visitInterfaceTypeDefinition(InterfaceTypeDefinition node, TraverserContext<Node> context) {
                InterfaceTypeDefinition changedNode = node.transform(builder -> {
                    builder.directives(sort(node.getDirectives(), Comparator.comparing(Directive::getName)));
                    builder.definitions(sort(node.getFieldDefinitions(), Comparator.comparing(FieldDefinition::getName)));
                });
                return newNode(context, changedNode);
            }

            @Override
            public TraversalControl visitUnionTypeDefinition(UnionTypeDefinition node, TraverserContext<Node> context) {
                UnionTypeDefinition changedNode = node.transform(builder -> {
                    builder.directives(sort(node.getDirectives(), Comparator.comparing(Directive::getName)));
                    builder.memberTypes(sort(node.getMemberTypes(), comparingTypes()));
                });
                return newNode(context, changedNode);
            }

            @Override
            public TraversalControl visitFieldDefinition(FieldDefinition node, TraverserContext<Node> context) {
                FieldDefinition changedNode = node.transform(builder -> {
                    builder.directives(sort(node.getDirectives(), Comparator.comparing(Directive::getName)));
                    builder.inputValueDefinitions(sort(node.getInputValueDefinitions(), Comparator.comparing(InputValueDefinition::getName)));
                });
                return newNode(context, changedNode);
            }

            @Override
            public TraversalControl visitInputValueDefinition(InputValueDefinition node, TraverserContext<Node> context) {
                InputValueDefinition changedNode = node.transform(builder -> {
                    List<Directive> directives = sort(node.getDirectives(), Comparator.comparing(Directive::getName));
                    builder.directives(directives);
                });
                return newNode(context, changedNode);
            }

            @Override
            public TraversalControl visitDirectiveDefinition(DirectiveDefinition node, TraverserContext<Node> context) {
                DirectiveDefinition changedNode = node.transform(builder -> {
                    builder.inputValueDefinitions(sort(node.getInputValueDefinitions(), Comparator.comparing(InputValueDefinition::getName)));
                    builder.directiveLocations(sort(node.getDirectiveLocations(), Comparator.comparing(DirectiveLocation::getName)));
                });
                return newNode(context, changedNode);
            }
        };

        AstTransformer astTransformer = new AstTransformer();
        Node newDoc = astTransformer.transform(nodeToBeSorted, visitor);
        //noinspection unchecked
        return (T) newDoc;
    }

    private TraversalControl newNode(TraverserContext<Node> context, Node changedNode) {
        changeNode(context, changedNode);
        return TraversalControl.CONTINUE;
    }

    private Comparator<Type> comparingTypes() {
        return Comparator.comparing(type -> TypeInfo.typeInfo(type).getName());
    }

    private Comparator<Selection> comparingSelections() {
        Function<Selection, String> keyFunction = s -> {
            if (s instanceof FragmentSpread) {
                return ((FragmentSpread) s).getName();
            }
            if (s instanceof Field) {
                return ((Field) s).getName();
            }
            if (s instanceof InlineFragment) {
                TypeName typeCondition = ((InlineFragment) s).getTypeCondition();
                return typeCondition == null ? "" : typeCondition.getName();
            }
            return "";
        };
        return Comparator.comparing(keyFunction);
    }

    private Comparator<Definition> comparingDefinitions() {
        Function<Definition, String> keyFunction = d -> {
            if (d instanceof OperationDefinition) {
                return ((OperationDefinition) d).getName();
            }
            if (d instanceof FragmentDefinition) {
                return ((FragmentDefinition) d).getName();
            }
            if (d instanceof DirectiveDefinition) {
                return ((DirectiveDefinition) d).getName();
            }
            if (d instanceof TypeDefinition) {
                return ((TypeDefinition) d).getName();
            }
            return "";
        };
        return Comparator.comparing(keyFunction);
    }

    private SelectionSet sortSelectionSet(SelectionSet selectionSet) {
        if (selectionSet == null) {
            return null;
        }
        List<Selection> selections = sort(selectionSet.getSelections(), comparingSelections());
        return selectionSet.transform(builder -> builder.selections(selections));
    }

    private <T> List<T> sort(List<T> items, Comparator<T> comparing) {
        items = new ArrayList<>(items);
        items.sort(comparing);
        return items;
    }
}

package graphql.language;

import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static graphql.language.AstTransformerUtil.changeNode;

public class AstSorter {

    public Document sortQuery(Document document) {

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
        };

        AstTransformer astTransformer = new AstTransformer();
        Node newDoc = astTransformer.transform(document, visitor);
        return (Document) newDoc;
    }

    private TraversalControl newNode(TraverserContext<Node> context, Node changedNode) {
        changeNode(context, changedNode);
        return TraversalControl.CONTINUE;
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
                return ((InlineFragment) s).getTypeCondition().getName();
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
            if (d instanceof DirectiveDefinition) {
                return ((DirectiveDefinition) d).getName();
            }
            if (d instanceof TypeDefinition) {
                return ((TypeDefinition) d).getName();
            }
            if (d instanceof FragmentDefinition) {
                return ((FragmentDefinition) d).getName();
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

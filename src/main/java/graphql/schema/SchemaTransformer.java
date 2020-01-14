package graphql.schema;

import graphql.PublicApi;
import graphql.introspection.Introspection;
import graphql.util.Breadcrumb;
import graphql.util.FpKit;
import graphql.util.NodeAdapter;
import graphql.util.NodeLocation;
import graphql.util.NodeZipper;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.schema.GraphQLSchemaElementAdapter.SCHEMA_ELEMENT_ADAPTER;
import static graphql.schema.SchemaElementChildrenContainer.newSchemaElementChildrenContainer;
import static graphql.util.NodeZipper.ModificationType.REPLACE;

/**
 * Transforms a {@link GraphQLSchema} object.
 */
@PublicApi
public class SchemaTransformer {

    // artificial schema element which serves as root element for the transformation
    private static class DummyRoot implements GraphQLSchemaElement {

        static final String QUERY = "query";
        static final String MUTATION = "mutation";
        static final String SUBSCRIPTION = "subscription";
        static final String ADD_TYPES = "addTypes";
        static final String DIRECTIVES = "directives";
        static final String INTROSPECTION = "introspection";
        GraphQLSchema schema;

        GraphQLObjectType query;
        GraphQLObjectType mutation;
        GraphQLObjectType subscription;
        Set<GraphQLType> additionalTypes;
        Set<GraphQLDirective> directives;

        DummyRoot(GraphQLSchema schema) {
            this.schema = schema;
            query = schema.getQueryType();
            mutation = schema.isSupportingMutations() ? schema.getMutationType() : null;
            subscription = schema.isSupportingSubscriptions() ? schema.getSubscriptionType() : null;
            additionalTypes = schema.getAdditionalTypes();
            directives = new LinkedHashSet<>(schema.getDirectives());
        }


        @Override
        public List<GraphQLSchemaElement> getChildren() {
            return assertShouldNeverHappen();
        }

        @Override
        public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
            SchemaElementChildrenContainer.Builder builder = newSchemaElementChildrenContainer()
                    .child(QUERY, query);
            if (schema.isSupportingMutations()) {
                builder.child(MUTATION, mutation);
            }
            if (schema.isSupportingSubscriptions()) {
                builder.child(SUBSCRIPTION, subscription);
            }
            builder.children(ADD_TYPES, additionalTypes);
            builder.children(DIRECTIVES, directives);
            builder.child(INTROSPECTION, Introspection.__Schema);
            return builder.build();
        }

        @Override
        public GraphQLSchemaElement withNewChildren(SchemaElementChildrenContainer newChildren) {
            query = newChildren.getChildOrNull(QUERY);
            mutation = newChildren.getChildOrNull(MUTATION);
            subscription = newChildren.getChildOrNull(SUBSCRIPTION);
            additionalTypes = new LinkedHashSet<>(newChildren.getChildren(ADD_TYPES));
            directives = new LinkedHashSet<>(newChildren.getChildren(DIRECTIVES));
            return this;
        }

        @Override
        public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
            return assertShouldNeverHappen();
        }
    }


    /**
     * Transforms a GraphQLSchema and returns a new GraphQLSchema object.
     *
     * @param schema
     * @param visitor
     *
     * @return a new GraphQLSchema instance.
     */
    public static GraphQLSchema transformSchema(GraphQLSchema schema, GraphQLTypeVisitor visitor) {
        SchemaTransformer schemaTransformer = new SchemaTransformer();
        return schemaTransformer.transform(schema, visitor);
    }


    public GraphQLSchema transform(final GraphQLSchema schema, GraphQLTypeVisitor visitor) {


        DummyRoot dummyRoot = new DummyRoot(schema);

        List<NodeZipper<GraphQLSchemaElement>> zippers = new LinkedList<>();
        Map<GraphQLSchemaElement, NodeZipper<GraphQLSchemaElement>> zipperByNodeAfterTraversing = new LinkedHashMap<>();
        Map<GraphQLSchemaElement, NodeZipper<GraphQLSchemaElement>> zipperByOriginalNode = new LinkedHashMap<>();

        Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> breadcrumbsByZipper = new LinkedHashMap<>();

        TraverserVisitor<GraphQLSchemaElement> nodeTraverserVisitor = new TraverserVisitor<GraphQLSchemaElement>() {
            @Override
            public TraversalControl enter(TraverserContext<GraphQLSchemaElement> context) {
                if (context.thisNode() == dummyRoot) {
                    return TraversalControl.CONTINUE;
                }
                NodeZipper<GraphQLSchemaElement> nodeZipper = new NodeZipper<>(context.thisNode(), context.getBreadcrumbs(), SCHEMA_ELEMENT_ADAPTER);
                context.setVar(NodeZipper.class, nodeZipper);
                context.setVar(NodeAdapter.class, SCHEMA_ELEMENT_ADAPTER);

                int zippersBefore = zippers.size();
                TraversalControl result = context.thisNode().accept(context, visitor);
                // detection if the node was changed: TODO make it better: doesn't work for parallel
                if (zippersBefore + 1 == zippers.size()) {
                    nodeZipper = zippers.get(zippers.size() - 1);
                }
                zipperByOriginalNode.put(context.originalThisNode(), nodeZipper);

                if (context.isDeleted()) {
                    zipperByNodeAfterTraversing.put(context.originalThisNode(), nodeZipper);
                } else {
                    zipperByNodeAfterTraversing.put(context.thisNode(), nodeZipper);
                }

                breadcrumbsByZipper.put(nodeZipper, new ArrayList<>());
                breadcrumbsByZipper.get(nodeZipper).add(context.getBreadcrumbs());
                return result;

            }

            @Override
            public TraversalControl leave(TraverserContext<GraphQLSchemaElement> context) {
                return TraversalControl.CONTINUE;
            }

            @Override
            public TraversalControl backRef(TraverserContext<GraphQLSchemaElement> context) {
                NodeZipper<GraphQLSchemaElement> zipper = zipperByOriginalNode.get(context.thisNode());
                breadcrumbsByZipper.get(zipper).add(context.getBreadcrumbs());
                visitor.visitBackRef(context);
                return TraversalControl.CONTINUE;
            }
        };

        Traverser<GraphQLSchemaElement> traverser = Traverser.depthFirstWithNamedChildren(SCHEMA_ELEMENT_ADAPTER::getNamedChildren, zippers, null);
        GraphQLCodeRegistry.Builder builder = GraphQLCodeRegistry.newCodeRegistry(schema.getCodeRegistry());
        traverser.rootVar(GraphQLCodeRegistry.Builder.class, builder);
        traverser.traverse(dummyRoot, nodeTraverserVisitor);

        toRootNode(zippers, breadcrumbsByZipper, zipperByNodeAfterTraversing);

        GraphQLSchema newSchema = GraphQLSchema.newSchema()
                .query(dummyRoot.query)
                .mutation(dummyRoot.mutation)
                .subscription(dummyRoot.subscription)
                .additionalTypes(dummyRoot.additionalTypes)
                .additionalDirectives(dummyRoot.directives)
                .codeRegistry(builder.build())
                .buildImpl(true);
        return newSchema;
    }

    private void toRootNode(List<NodeZipper<GraphQLSchemaElement>> zippers,
                            Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> breadcrumbsByZipper,
                            Map<GraphQLSchemaElement, NodeZipper<GraphQLSchemaElement>> zipperByNodeAfterTraversing) {
        if (zippers.size() == 0) {
            return;
        }

        /*
         * Because every node can have multiple parents (not a tree, but a graph) we have a list of breadcrumbs per zipper.
         * Or to put it differently: there is not one path from a node to the dummyRoot, but multiple ones with a different length
         */

        // we want to preserve the order here
        Set<NodeZipper<GraphQLSchemaElement>> curZippers = new LinkedHashSet<>(zippers);
        Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> curBreadcrumbsByZipper = new LinkedHashMap<>(breadcrumbsByZipper);

        while (curZippers.size() > 1 || !(curZippers.iterator().next().getCurNode() instanceof DummyRoot)) {
            List<NodeZipper<GraphQLSchemaElement>> deepestZippers = new ArrayList<>();
            int depth = getDeepestZippers(curZippers, curBreadcrumbsByZipper, deepestZippers);
            Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> breadcrumbsUsed = getBreadcrumbsUsed(curZippers, curBreadcrumbsByZipper, depth);

            Map<GraphQLSchemaElement, List<NodeZipper<GraphQLSchemaElement>>> zippersByParent = groupBySameParent(deepestZippers, breadcrumbsUsed);

            List<NodeZipper<GraphQLSchemaElement>> newZippers = new ArrayList<>();

            for (Map.Entry<GraphQLSchemaElement, List<NodeZipper<GraphQLSchemaElement>>> entry : zippersByParent.entrySet()) {
                // this is the parenNode we want to replace
                GraphQLSchemaElement parentNode = entry.getKey();
                NodeZipper<GraphQLSchemaElement> newZipper = moveUp(parentNode, entry.getValue(), breadcrumbsUsed);

                // updating curBreadcrumbsByZipper to use the new zipper for parent
                NodeZipper<GraphQLSchemaElement> originalZipperForParent = zipperByNodeAfterTraversing.get(parentNode);
                // the parent might have been changed itself, we can get rid of this zipper because moveUp already
                // used the changed parent
                curZippers.remove(originalZipperForParent);
                List<List<Breadcrumb<GraphQLSchemaElement>>> breadcrumbsForOriginalParent = curBreadcrumbsByZipper.get(originalZipperForParent);
                curBreadcrumbsByZipper.remove(originalZipperForParent);
                curBreadcrumbsByZipper.put(newZipper, breadcrumbsForOriginalParent);

                newZippers.add(newZipper);
            }
            // remove all breadcrumbs we and remove the zipper if no breadcrumbs are left
            for (Map.Entry<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> entry : breadcrumbsUsed.entrySet()) {
                List<List<Breadcrumb<GraphQLSchemaElement>>> all = curBreadcrumbsByZipper.get(entry.getKey());
                all.removeAll(entry.getValue());
                // if we used all breadcrumbs we are done with this zipper
                if (all.size() == 0) {
                    curZippers.remove(entry.getKey());
                }
            }
            curZippers.addAll(newZippers);
        }
    }

    private Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> getBreadcrumbsUsed(
            Set<NodeZipper<GraphQLSchemaElement>> zippers,
            Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> breadcrumbsByZipper,
            int depth) {
        Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> result = new LinkedHashMap<>();
        for (NodeZipper<GraphQLSchemaElement> zipper : zippers) {
            List<List<Breadcrumb<GraphQLSchemaElement>>> breadcrumbsList = breadcrumbsByZipper.get(zipper);
            for (List<Breadcrumb<GraphQLSchemaElement>> breadcrumbs : breadcrumbsList) {
                if (breadcrumbs.size() == depth) {
                    result.computeIfAbsent(zipper, ignored -> new ArrayList<>());
                    result.get(zipper).add(breadcrumbs);
                }
            }
        }
        return result;
    }

    private int getDeepestZippers(
            Set<NodeZipper<GraphQLSchemaElement>> zippers,
            Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> breadcrumbsByZipper,
            List<NodeZipper<GraphQLSchemaElement>> result
    ) {
        Map<Integer, List<NodeZipper<GraphQLSchemaElement>>> grouped = FpKit.groupingBy(zippers, astZipper -> {
            List<List<Breadcrumb<GraphQLSchemaElement>>> breadcrumbsList = breadcrumbsByZipper.get(astZipper);
            List<Integer> sizes = FpKit.map(breadcrumbsList, List::size);
            return Collections.max(sizes);
        });

        Integer maxLevel = Collections.max(grouped.keySet());
        result.addAll(grouped.get(maxLevel));
        return maxLevel;
    }

    private static class ZipperWithOneParent {
        public ZipperWithOneParent(NodeZipper<GraphQLSchemaElement> zipper, Breadcrumb<GraphQLSchemaElement> parent) {
            this.zipper = zipper;
            this.parent = parent;
        }

        public NodeZipper<GraphQLSchemaElement> zipper;
        public Breadcrumb<GraphQLSchemaElement> parent;
    }

    private NodeZipper<GraphQLSchemaElement> moveUp(
            GraphQLSchemaElement parent,
            List<NodeZipper<GraphQLSchemaElement>> sameParent,
            Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> breadcrumbsUsed) {
        assertNotEmpty(sameParent, "expected at least one zipper");

        Map<String, List<GraphQLSchemaElement>> childrenMap = new HashMap<>(SCHEMA_ELEMENT_ADAPTER.getNamedChildren(parent));
        Map<String, Integer> indexCorrection = new HashMap<>();

        List<ZipperWithOneParent> zipperWithOneParents = new ArrayList<>();
        for (NodeZipper<GraphQLSchemaElement> zipper : sameParent) {
            for (List<Breadcrumb<GraphQLSchemaElement>> breadcrumbs : breadcrumbsUsed.get(zipper)) {
                // only consider breadcrumbs pointing the right parent
                if (breadcrumbs.get(0).getNode() != parent) {
                    continue;
                }
                zipperWithOneParents.add(new ZipperWithOneParent(zipper, breadcrumbs.get(0)));
            }
        }

        zipperWithOneParents.sort((zipperWithOneParent1, zipperWithOneParent2) -> {
            NodeZipper<GraphQLSchemaElement> zipper1 = zipperWithOneParent1.zipper;
            NodeZipper<GraphQLSchemaElement> zipper2 = zipperWithOneParent2.zipper;
            Breadcrumb<GraphQLSchemaElement> breadcrumb1 = zipperWithOneParent1.parent;
            Breadcrumb<GraphQLSchemaElement> breadcrumb2 = zipperWithOneParent2.parent;
            int index1 = breadcrumb1.getLocation().getIndex();
            int index2 = breadcrumb2.getLocation().getIndex();
            if (index1 != index2) {
                return Integer.compare(index1, index2);
            }
            NodeZipper.ModificationType modificationType1 = zipper1.getModificationType();
            NodeZipper.ModificationType modificationType2 = zipper2.getModificationType();

            // same index can never be deleted and changed at the same time

            if (modificationType1 == modificationType2) {
                return 0;
            }

            // always first replacing the node
            if (modificationType1 == REPLACE) {
                return -1;
            }
            // and then INSERT_BEFORE before INSERT_AFTER
            return modificationType1 == NodeZipper.ModificationType.INSERT_BEFORE ? -1 : 1;

        });

        for (ZipperWithOneParent zipperWithOneParent : zipperWithOneParents) {
            NodeZipper<GraphQLSchemaElement> zipper = zipperWithOneParent.zipper;
            Breadcrumb<GraphQLSchemaElement> breadcrumb = zipperWithOneParent.parent;
            NodeLocation location = breadcrumb.getLocation();
            Integer ixDiff = indexCorrection.getOrDefault(location.getName(), 0);
            int ix = location.getIndex() + ixDiff;
            String name = location.getName();
            List<GraphQLSchemaElement> childList = new ArrayList<>(childrenMap.get(name));
            switch (zipper.getModificationType()) {
                case REPLACE:
                    childList.set(ix, zipper.getCurNode());
                    break;
                case DELETE:
                    childList.remove(ix);
                    indexCorrection.put(name, ixDiff - 1);
                    break;
                case INSERT_BEFORE:
                    childList.add(ix, zipper.getCurNode());
                    indexCorrection.put(name, ixDiff + 1);
                    break;
                case INSERT_AFTER:
                    childList.add(ix + 1, zipper.getCurNode());
                    indexCorrection.put(name, ixDiff + 1);
                    break;
            }
            childrenMap.put(name, childList);
        }

        GraphQLSchemaElement newNode = SCHEMA_ELEMENT_ADAPTER.withNewChildren(parent, childrenMap);
        List<Breadcrumb<GraphQLSchemaElement>> newBreadcrumbs = sameParent.get(0).getBreadcrumbs().subList(1, sameParent.get(0).getBreadcrumbs().size());
        return new NodeZipper<>(newNode, newBreadcrumbs, SCHEMA_ELEMENT_ADAPTER);
    }

    private Map<GraphQLSchemaElement, List<NodeZipper<GraphQLSchemaElement>>> groupBySameParent
            (List<NodeZipper<GraphQLSchemaElement>> zippers,
             Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> breadcrumbsByZipper) {
        Map<GraphQLSchemaElement, List<NodeZipper<GraphQLSchemaElement>>> result = new LinkedHashMap<>();

        for (NodeZipper<GraphQLSchemaElement> zipper : zippers) {
            for (List<Breadcrumb<GraphQLSchemaElement>> breadcrumbs : breadcrumbsByZipper.get(zipper)) {
                GraphQLSchemaElement parent = breadcrumbs.get(0).getNode();
                result.computeIfAbsent(parent, ignored -> new ArrayList<>());
                result.get(parent).add(zipper);
            }
        }
        return result;
    }


}

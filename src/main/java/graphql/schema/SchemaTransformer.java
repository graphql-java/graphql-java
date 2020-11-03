package graphql.schema;

import graphql.Assert;
import graphql.PublicApi;
import graphql.introspection.Introspection;
import graphql.util.Breadcrumb;
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
import java.util.Iterator;
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
import static java.lang.String.format;

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
            // special hack: we don't create a new dummy root, but we simply update it
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
     * @param schema  the schema to transform
     * @param visitor the visitor call back
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

        Map<GraphQLSchemaElement, List<GraphQLSchemaElement>> reverseDependencies = new LinkedHashMap<>();

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
                if (nodeZipper.getModificationType() != NodeZipper.ModificationType.DELETE) {
                    reverseDependencies.computeIfAbsent(context.thisNode(), ign -> new ArrayList<>()).add(context.getParentNode());
                }
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
                reverseDependencies.get(zipper.getCurNode()).add(context.getParentNode());
                return TraversalControl.CONTINUE;
            }
        };


        Traverser<GraphQLSchemaElement> traverser = Traverser.depthFirstWithNamedChildren(SCHEMA_ELEMENT_ADAPTER::getNamedChildren, zippers, null);
        GraphQLCodeRegistry.Builder builder = GraphQLCodeRegistry.newCodeRegistry(schema.getCodeRegistry());
        traverser.rootVar(GraphQLCodeRegistry.Builder.class, builder);
        traverser.traverse(dummyRoot, nodeTraverserVisitor);


        List<GraphQLSchemaElement> topologicalSort = topologicalSort(zipperByNodeAfterTraversing.keySet(), reverseDependencies);

        zipUpToDummyRoot(zippers, topologicalSort, breadcrumbsByZipper, zipperByNodeAfterTraversing);

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

    private List<GraphQLSchemaElement> topologicalSort(Set<GraphQLSchemaElement> allNodes, Map<GraphQLSchemaElement, List<GraphQLSchemaElement>> reverseDependencies) {
        List<GraphQLSchemaElement> result = new ArrayList<>();
        Set<GraphQLSchemaElement> notPermMarked = new LinkedHashSet<>(allNodes);
        Set<GraphQLSchemaElement> tempMarked = new LinkedHashSet<>();
        Set<GraphQLSchemaElement> permMarked = new LinkedHashSet<>();
        /**
         * Taken from: https://en.wikipedia.org/wiki/Topological_sorting#Depth-first_search
         * while exists nodes without a permanent mark do
         *     select an unmarked node n
         *     visit(n)
         */
        while (true) {
            Iterator<GraphQLSchemaElement> iterator = notPermMarked.iterator();
            if (!iterator.hasNext()) {
                break;
            }
            GraphQLSchemaElement n = iterator.next();
            iterator.remove();
            visit(n, tempMarked, permMarked, notPermMarked, result, reverseDependencies);
        }
        return result;
    }

    private void visit(GraphQLSchemaElement n,
                       Set<GraphQLSchemaElement> tempMarked,
                       Set<GraphQLSchemaElement> permMarked,
                       Set<GraphQLSchemaElement> notPermMarked,
                       List<GraphQLSchemaElement> result,
                       Map<GraphQLSchemaElement, List<GraphQLSchemaElement>> reverseDependencies) {
        /**
         * Taken from: https://en.wikipedia.org/wiki/Topological_sorting#Depth-first_search
         * if n has a permanent mark then
         *         return
         *     if n has a temporary mark then
         *         stop   (not a DAG)
         *
         *     mark n with a temporary mark
         *
         *     for each node m with an edge from n to m do
         *         visit(m)
         *
         *     remove temporary mark from n
         *     mark n with a permanent mark
         *     add n to head of L
         */
        if (permMarked.contains(n)) {
            return;
        }
        if (tempMarked.contains(n)) {
            Assert.assertShouldNeverHappen("NOT A DAG: %s has temp mark", n);
            return;
        }
        tempMarked.add(n);
        if (reverseDependencies.containsKey(n)) {
            for (GraphQLSchemaElement m : reverseDependencies.get(n)) {
                visit(m, tempMarked, permMarked, notPermMarked, result, reverseDependencies);
            }
        }
        tempMarked.remove(n);
        permMarked.add(n);
        notPermMarked.remove(n);
        result.add(n);
    }

    private void zipUpToDummyRoot(List<NodeZipper<GraphQLSchemaElement>> zippers,
                                  List<GraphQLSchemaElement> topSort,
                                  Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> breadcrumbsByZipper,
                                  Map<GraphQLSchemaElement, NodeZipper<GraphQLSchemaElement>> nodeToZipper) {
        if (zippers.size() == 0) {
            return;
        }
        Set<NodeZipper<GraphQLSchemaElement>> curZippers = new LinkedHashSet<>(zippers);

        for (int i = topSort.size() - 1; i >= 0; i--) {
            GraphQLSchemaElement element = topSort.get(i);
            // that the map goes from  zipper -> one List (= one path) is because we know that in a schema one element
            // has never two different edges to another element
            Map<NodeZipper<GraphQLSchemaElement>, List<Breadcrumb<GraphQLSchemaElement>>> zipperWithSameParent = zipperWithSameParent(element, curZippers, breadcrumbsByZipper);
            // this means we have a node which doesn't need to be changed
            if (zipperWithSameParent.size() == 0) {
                continue;
            }
            NodeZipper<GraphQLSchemaElement> newZipper = moveUp(element, zipperWithSameParent);
            if (element instanceof DummyRoot) {
                // this means we have updated the dummy root and we are done (dummy root is a special as it gets updated in place, see Implementation of DummyRoot)
                break;
            }

            // update curZippers
            NodeZipper<GraphQLSchemaElement> curZipperForElement = nodeToZipper.get(element);
            Assert.assertNotNull(curZipperForElement, () -> format("curZipperForElement is null for parentNode %s", element));
            curZippers.remove(curZipperForElement);
            curZippers.add(newZipper);

            // update breadcrumbsByZipper to use the newZipper
            List<List<Breadcrumb<GraphQLSchemaElement>>> breadcrumbsForOriginalParent = breadcrumbsByZipper.get(curZipperForElement);
            Assert.assertNotNull(breadcrumbsForOriginalParent, () -> format("No breadcrumbs found for zipper %s", curZipperForElement));
            breadcrumbsByZipper.remove(curZipperForElement);
            breadcrumbsByZipper.put(newZipper, breadcrumbsForOriginalParent);

        }
    }

    private Map<NodeZipper<GraphQLSchemaElement>, List<Breadcrumb<GraphQLSchemaElement>>> zipperWithSameParent(GraphQLSchemaElement parent,
                                                                                                               Set<NodeZipper<GraphQLSchemaElement>> zippers,
                                                                                                               Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> curBreadcrumbsByZipper) {
        Map<NodeZipper<GraphQLSchemaElement>, List<Breadcrumb<GraphQLSchemaElement>>> result = new LinkedHashMap<>();
        outer:
        for (NodeZipper<GraphQLSchemaElement> zipper : zippers) {
            for (List<Breadcrumb<GraphQLSchemaElement>> path : curBreadcrumbsByZipper.get(zipper)) {
                if (path.get(0).getNode() == parent) {
                    result.put(zipper, path);
                    continue outer;
                }
            }
        }
        return result;
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
            Map<NodeZipper<GraphQLSchemaElement>, List<Breadcrumb<GraphQLSchemaElement>>> sameParentsZipper) {
        Set<NodeZipper<GraphQLSchemaElement>> sameParent = sameParentsZipper.keySet();
        assertNotEmpty(sameParent, () -> "expected at least one zipper");

        Map<String, List<GraphQLSchemaElement>> childrenMap = new HashMap<>(SCHEMA_ELEMENT_ADAPTER.getNamedChildren(parent));
        Map<String, Integer> indexCorrection = new HashMap<>();

        List<ZipperWithOneParent> zipperWithOneParents = new ArrayList<>();
        for (NodeZipper<GraphQLSchemaElement> zipper : sameParent) {
            List<Breadcrumb<GraphQLSchemaElement>> breadcrumbs = sameParentsZipper.get(zipper);
            zipperWithOneParents.add(new ZipperWithOneParent(zipper, breadcrumbs.get(0)));
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
        final List<Breadcrumb<GraphQLSchemaElement>> oldBreadcrumbs = sameParent.iterator().next().getBreadcrumbs();
        List<Breadcrumb<GraphQLSchemaElement>> newBreadcrumbs;
        if (oldBreadcrumbs.size() > 1) {
            newBreadcrumbs = oldBreadcrumbs.subList(1, oldBreadcrumbs.size());
        } else {
            newBreadcrumbs = Collections.emptyList();
        }
        return new NodeZipper<>(newNode, newBreadcrumbs, SCHEMA_ELEMENT_ADAPTER);
    }

}

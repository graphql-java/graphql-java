package graphql.schema;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import graphql.PublicApi;
import graphql.util.Breadcrumb;
import graphql.util.NodeAdapter;
import graphql.util.NodeLocation;
import graphql.util.NodeZipper;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.schema.GraphQLSchemaElementAdapter.SCHEMA_ELEMENT_ADAPTER;
import static graphql.schema.SchemaElementChildrenContainer.newSchemaElementChildrenContainer;
import static graphql.schema.impl.StronglyConnectedComponentsTopologicallySorted.getStronglyConnectedComponentsTopologicallySorted;
import static graphql.util.NodeZipper.ModificationType.DELETE;
import static graphql.util.NodeZipper.ModificationType.REPLACE;
import static graphql.util.TraversalControl.CONTINUE;
import static java.lang.String.format;

/**
 * Transforms a {@link GraphQLSchema} object by calling bac on a provided visitor.
 * <p>
 * To change a {@link GraphQLSchemaElement} node in the schema you need
 * to return {@link GraphQLTypeVisitor#changeNode(TraverserContext, GraphQLSchemaElement)}
 * which instructs the schema transformer to change that element upon leaving that
 * visitor method.
 * <pre>
 * {@code
 *  public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
 *      GraphQLObjectType newObjectType = mkSomeNewNode(objectType);
 *      return changeNode(context, newObjectType);
 *  }
 *  }
 * </pre>
 * <p>
 * To delete an element use {@link GraphQLTypeVisitor#deleteNode(TraverserContext)}
 * <pre>
 * {@code
 *  public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
 *      return deleteNode(context, objectType);
 *  }
 *  }
 * </pre>
 * <p>
 * To insert elements use either {@link GraphQLTypeVisitor#insertAfter(TraverserContext, GraphQLSchemaElement)} or
 * {@link GraphQLTypeVisitor#insertBefore(TraverserContext, GraphQLSchemaElement)}
 * which will insert the new node before or after the current node being visited
 * <pre>
 * {@code
 *  public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
 *      GraphQLObjectType newObjectType = mkSomeNewNode();
 *      return insertAfter(context, newObjectType);
 *  }
 *  }
 * </pre>
 */
@PublicApi
public class SchemaTransformer {

    /**
     * Transforms a GraphQLSchema and returns a new GraphQLSchema object.
     *
     * @param schema  the schema to transform
     * @param visitor the visitor call back
     *
     * @return a new GraphQLSchema instance.
     */
    public static GraphQLSchema transformSchema(GraphQLSchema schema, GraphQLTypeVisitor visitor) {
        SchemaTransformer schemaTransformer = new SchemaTransformer();
        return schemaTransformer.transform(schema, visitor);
    }

    /**
     * Transforms a GraphQLSchema and returns a new GraphQLSchema object.
     *
     * @param schema             the schema to transform
     * @param visitor            the visitor call back
     * @param postTransformation a callback that can be as a final step to the schema
     *
     * @return a new GraphQLSchema instance.
     */
    public static GraphQLSchema transformSchema(GraphQLSchema schema, GraphQLTypeVisitor visitor, Consumer<GraphQLSchema.Builder> postTransformation) {
        SchemaTransformer schemaTransformer = new SchemaTransformer();
        return schemaTransformer.transform(schema, visitor, postTransformation);
    }

    /**
     * Transforms a {@link GraphQLSchemaElement} and returns a new element.
     *
     * @param schemaElement the schema element to transform
     * @param visitor       the visitor call back
     * @param <T>           for two
     *
     * @return a new GraphQLSchemaElement instance.
     */
    public static <T extends GraphQLSchemaElement> T transformSchema(final T schemaElement, GraphQLTypeVisitor visitor) {
        SchemaTransformer schemaTransformer = new SchemaTransformer();
        return schemaTransformer.transform(schemaElement, visitor);
    }

    public GraphQLSchema transform(final GraphQLSchema schema, GraphQLTypeVisitor visitor) {
        return (GraphQLSchema) transformImpl(schema, null, visitor, null);
    }

    public GraphQLSchema transform(final GraphQLSchema schema, GraphQLTypeVisitor visitor, Consumer<GraphQLSchema.Builder> postTransformation) {
        return (GraphQLSchema) transformImpl(schema, null, visitor, postTransformation);
    }

    public <T extends GraphQLSchemaElement> T transform(final T schemaElement, GraphQLTypeVisitor visitor) {
        //noinspection unchecked
        return (T) transformImpl(null, schemaElement, visitor, null);
    }

    private Object transformImpl(final GraphQLSchema schema, GraphQLSchemaElement schemaElement, GraphQLTypeVisitor visitor, Consumer<GraphQLSchema.Builder> postTransformation) {
        DummyRoot dummyRoot;
        GraphQLCodeRegistry.Builder codeRegistry = null;
        if (schema != null) {
            dummyRoot = new DummyRoot(schema);
            codeRegistry = GraphQLCodeRegistry.newCodeRegistry(schema.getCodeRegistry());
        } else {
            dummyRoot = new DummyRoot(schemaElement);
        }

        final Map<String, GraphQLNamedType> changedTypes = new LinkedHashMap<>();
        final Map<String, GraphQLTypeReference> typeReferences = new LinkedHashMap<>();

        // first pass - general transformation
        boolean schemaChanged = traverseAndTransform(dummyRoot, changedTypes, typeReferences, visitor, codeRegistry);

        // if we have changed any named elements AND we have type references referring to them then
        // we need to make a second pass to replace these type references to the new names
        if (!changedTypes.isEmpty()) {
            boolean hasTypeRefsForChangedTypes = changedTypes.keySet().stream().anyMatch(typeReferences::containsKey);
            if (hasTypeRefsForChangedTypes) {
                replaceTypeReferences(dummyRoot, codeRegistry, changedTypes);
            }
        }

        if (schema != null) {

            GraphQLSchema graphQLSchema = schema;
            if (schemaChanged || codeRegistry.hasChanged()) {
                graphQLSchema = dummyRoot.rebuildSchema(codeRegistry);
                if (postTransformation != null) {
                    graphQLSchema = graphQLSchema.transform(postTransformation);
                }
            }
            return graphQLSchema;
        } else {
            return dummyRoot.schemaElement;
        }
    }

    private void replaceTypeReferences(DummyRoot dummyRoot, GraphQLCodeRegistry.Builder codeRegistry, Map<String, GraphQLNamedType> changedTypes) {
        GraphQLTypeVisitor typeRefVisitor = new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLTypeReference(GraphQLTypeReference typeRef, TraverserContext<GraphQLSchemaElement> context) {
                GraphQLNamedType graphQLNamedType = changedTypes.get(typeRef.getName());
                if (graphQLNamedType != null) {
                    typeRef = GraphQLTypeReference.typeRef(graphQLNamedType.getName());
                    return changeNode(context, typeRef);
                }
                return CONTINUE;
            }
        };
        traverseAndTransform(dummyRoot, new HashMap<>(), new HashMap<>(), typeRefVisitor, codeRegistry);
    }

    private boolean traverseAndTransform(DummyRoot dummyRoot, Map<String, GraphQLNamedType> changedTypes, Map<String, GraphQLTypeReference> typeReferences, GraphQLTypeVisitor visitor, GraphQLCodeRegistry.Builder codeRegistry) {
        List<NodeZipper<GraphQLSchemaElement>> zippers = new LinkedList<>();
        Map<GraphQLSchemaElement, NodeZipper<GraphQLSchemaElement>> zipperByNodeAfterTraversing = new LinkedHashMap<>();
        Map<GraphQLSchemaElement, NodeZipper<GraphQLSchemaElement>> zipperByOriginalNode = new LinkedHashMap<>();

        Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> breadcrumbsByZipper = new LinkedHashMap<>();

        Map<GraphQLSchemaElement, List<GraphQLSchemaElement>> reverseDependencies = new LinkedHashMap<>();
        Map<String, List<GraphQLSchemaElement>> typeRefReverseDependencies = new LinkedHashMap<>();

        TraverserVisitor<GraphQLSchemaElement> nodeTraverserVisitor = new TraverserVisitor<GraphQLSchemaElement>() {
            @Override
            public TraversalControl enter(TraverserContext<GraphQLSchemaElement> context) {
                GraphQLSchemaElement currentSchemaElement = context.thisNode();
                if (currentSchemaElement == dummyRoot) {
                    return TraversalControl.CONTINUE;
                }
                if (currentSchemaElement instanceof GraphQLTypeReference) {
                    GraphQLTypeReference typeRef = (GraphQLTypeReference) currentSchemaElement;
                    typeReferences.put(typeRef.getName(), typeRef);
                }
                NodeZipper<GraphQLSchemaElement> nodeZipper = new NodeZipper<>(currentSchemaElement, context.getBreadcrumbs(), SCHEMA_ELEMENT_ADAPTER);
                context.setVar(NodeZipper.class, nodeZipper);
                context.setVar(NodeAdapter.class, SCHEMA_ELEMENT_ADAPTER);

                int zippersBefore = zippers.size();
                TraversalControl result = currentSchemaElement.accept(context, visitor);

                // detection if the node was changed
                if (zippersBefore + 1 == zippers.size()) {
                    nodeZipper = zippers.get(zippers.size() - 1);
                    if (context.originalThisNode() instanceof GraphQLNamedType && context.isChanged()) {
                        GraphQLNamedType originalNamedType = (GraphQLNamedType) context.originalThisNode();
                        GraphQLNamedType changedNamedType = (GraphQLNamedType) context.thisNode();
                        if (!originalNamedType.getName().equals(changedNamedType.getName())) {
                            changedTypes.put(originalNamedType.getName(), changedNamedType);
                        }
                    }
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

                    if (context.originalThisNode() instanceof GraphQLTypeReference) {
                        String typeName = ((GraphQLTypeReference) context.originalThisNode()).getName();
                        typeRefReverseDependencies.computeIfAbsent(typeName, ign -> new ArrayList<>()).add(context.getParentNode());
                    }
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
                if (zipper.getModificationType() == DELETE) {
                    return CONTINUE;
                }
                visitor.visitBackRef(context);
                List<GraphQLSchemaElement> reverseDependenciesForCurNode = reverseDependencies.get(zipper.getCurNode());
                assertNotNull(reverseDependenciesForCurNode);
                reverseDependenciesForCurNode.add(context.getParentNode());
                return TraversalControl.CONTINUE;
            }
        };


        Traverser<GraphQLSchemaElement> traverser = Traverser.depthFirstWithNamedChildren(SCHEMA_ELEMENT_ADAPTER::getNamedChildren, zippers, null);
        if (codeRegistry != null) {
            traverser.rootVar(GraphQLCodeRegistry.Builder.class, codeRegistry);
        }

        traverser.traverse(dummyRoot, nodeTraverserVisitor);

        List<List<GraphQLSchemaElement>> stronglyConnectedTopologicallySorted = getStronglyConnectedComponentsTopologicallySorted(reverseDependencies, typeRefReverseDependencies);

        return zipUpToDummyRoot(zippers, stronglyConnectedTopologicallySorted, breadcrumbsByZipper, zipperByNodeAfterTraversing);
    }

    private static class RelevantZippersAndBreadcrumbs {
        final Multimap<GraphQLSchemaElement, NodeZipper<GraphQLSchemaElement>> zipperByParent = LinkedHashMultimap.create();
        final Set<NodeZipper<GraphQLSchemaElement>> relevantZippers;
        final Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> breadcrumbsByZipper;

        public RelevantZippersAndBreadcrumbs(List<NodeZipper<GraphQLSchemaElement>> relevantZippers,
                                             Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> breadcrumbsByZipper) {
            this.relevantZippers = new LinkedHashSet<>(relevantZippers);
            this.breadcrumbsByZipper = breadcrumbsByZipper;
            for (NodeZipper<GraphQLSchemaElement> zipper : relevantZippers) {
                for (List<Breadcrumb<GraphQLSchemaElement>> breadcrumbs : breadcrumbsByZipper.get(zipper)) {
                    zipperByParent.put(breadcrumbs.get(0).getNode(), zipper);
                }
            }
        }

        public boolean isRelevantZipper(NodeZipper<GraphQLSchemaElement> zipper) {
            return relevantZippers.contains(zipper);
        }

        public Collection<NodeZipper<GraphQLSchemaElement>> zippersWithParent(GraphQLSchemaElement parent) {
            return zipperByParent.get(parent);
        }

        public void removeRelevantZipper(NodeZipper<GraphQLSchemaElement> zipper) {
            relevantZippers.remove(zipper);
        }

        public List<List<Breadcrumb<GraphQLSchemaElement>>> getBreadcrumbs(NodeZipper<GraphQLSchemaElement> zipper) {
            return breadcrumbsByZipper.get(zipper);
        }

        public void updateZipper(NodeZipper<GraphQLSchemaElement> currentZipper,
                                 NodeZipper<GraphQLSchemaElement> newZipper) {
            // the current zipper is not always relevant, meaning this has no effect sometimes
            relevantZippers.remove(currentZipper);
            relevantZippers.add(newZipper);


            List<List<Breadcrumb<GraphQLSchemaElement>>> currentBreadcrumbs = breadcrumbsByZipper.get(currentZipper);
            assertNotNull(currentBreadcrumbs, () -> format("No breadcrumbs found for zipper %s", currentZipper));
            for (List<Breadcrumb<GraphQLSchemaElement>> breadcrumbs : currentBreadcrumbs) {
                GraphQLSchemaElement parent = breadcrumbs.get(0).getNode();
                zipperByParent.remove(parent, currentZipper);
                zipperByParent.put(parent, newZipper);
            }
            breadcrumbsByZipper.remove(currentZipper);
            breadcrumbsByZipper.put(newZipper, currentBreadcrumbs);

        }

    }


    private boolean zipUpToDummyRoot(List<NodeZipper<GraphQLSchemaElement>> zippers,
                                     List<List<GraphQLSchemaElement>> stronglyConnectedTopologicallySorted,
                                     Map<NodeZipper<GraphQLSchemaElement>, List<List<Breadcrumb<GraphQLSchemaElement>>>> breadcrumbsByZipper,
                                     Map<GraphQLSchemaElement, NodeZipper<GraphQLSchemaElement>> nodeToZipper) {
        if (zippers.size() == 0) {
            return false;
        }
        RelevantZippersAndBreadcrumbs relevantZippers = new RelevantZippersAndBreadcrumbs(zippers, breadcrumbsByZipper);

        for (int i = stronglyConnectedTopologicallySorted.size() - 1; i >= 0; i--) {
            List<GraphQLSchemaElement> scc = stronglyConnectedTopologicallySorted.get(i);
            // performance relevant: we avoid calling zipperWithSameParent twice
            // for SCC of size one.
            if (scc.size() > 1) {
                boolean sccChanged = false;
                List<GraphQLSchemaElement> unchangedSccElements = new ArrayList<>();
                for (GraphQLSchemaElement element : scc) {
                    // if the current element itself has a zipper it is changed
                    if (relevantZippers.isRelevantZipper(nodeToZipper.get(element))) {
                        sccChanged = true;
                        continue;
                    }
                    // if the current element is changed via "moveUp" it is changed
                    Map<NodeZipper<GraphQLSchemaElement>, Breadcrumb<GraphQLSchemaElement>> zipperWithSameParent = zipperWithSameParent(element, relevantZippers, false);
                    if (zipperWithSameParent.size() > 0) {
                        sccChanged = true;
                    } else {
                        unchangedSccElements.add(element);
                    }
                }
                if (!sccChanged) {
                    continue;
                }
                // we need to change all elements inside the current SCC
                for (GraphQLSchemaElement element : unchangedSccElements) {
                    NodeZipper<GraphQLSchemaElement> currentZipper = nodeToZipper.get(element);
                    NodeZipper<GraphQLSchemaElement> newZipper = currentZipper.withNewNode(element.copy());
                    nodeToZipper.put(element, newZipper);
                    relevantZippers.updateZipper(currentZipper, newZipper);
                }
            }
            for (int j = scc.size() - 1; j >= 0; j--) {
                GraphQLSchemaElement element = scc.get(j);
                Map<NodeZipper<GraphQLSchemaElement>, Breadcrumb<GraphQLSchemaElement>> zipperWithSameParent = zipperWithSameParent(element, relevantZippers, true);
                // this means we have a node which doesn't need to be changed
                if (zipperWithSameParent.size() == 0) {
                    continue;
                }
                NodeZipper<GraphQLSchemaElement> newZipper = moveUp(element, zipperWithSameParent);

                if (element instanceof DummyRoot) {
                    // this means we have updated the dummy root and we are done (dummy root is a special as it gets updated in place, see Implementation of DummyRoot)
                    break;
                }

                NodeZipper<GraphQLSchemaElement> curZipperForElement = nodeToZipper.get(element);
                assertNotNull(curZipperForElement, () -> format("curZipperForElement is null for parentNode %s", element));
                relevantZippers.updateZipper(curZipperForElement, newZipper);

            }
        }
        return true;
    }

    private Map<NodeZipper<GraphQLSchemaElement>, Breadcrumb<GraphQLSchemaElement>> zipperWithSameParent(
            GraphQLSchemaElement parent,
            RelevantZippersAndBreadcrumbs relevantZippers,
            boolean cleanup) {
        Map<NodeZipper<GraphQLSchemaElement>, Breadcrumb<GraphQLSchemaElement>> result = new LinkedHashMap<>();
        Collection<NodeZipper<GraphQLSchemaElement>> zippersWithParent = relevantZippers.zippersWithParent(parent);
        Iterator<NodeZipper<GraphQLSchemaElement>> zippersIter = zippersWithParent.iterator();
        outer:
        while (zippersIter.hasNext()) {
            NodeZipper<GraphQLSchemaElement> zipper = zippersIter.next();
            List<List<Breadcrumb<GraphQLSchemaElement>>> listOfBreadcrumbsList = assertNotNull(relevantZippers.getBreadcrumbs(zipper));
            for (int i = 0; i < listOfBreadcrumbsList.size(); i++) {
                List<Breadcrumb<GraphQLSchemaElement>> path = listOfBreadcrumbsList.get(i);
                if (path.get(0).getNode() == parent) {
                    result.put(zipper, path.get(0));
                    if (cleanup) {
                        // remove breadcrumb we just used
                        listOfBreadcrumbsList.remove(i);
                        if (listOfBreadcrumbsList.size() == 0) {
                            // if there are no breadcrumbs left for this zipper it is safe to remove
                            relevantZippers.removeRelevantZipper(zipper);
                        }
                    }
                    continue outer;
                }
            }
        }
        return result;
    }

    private NodeZipper<GraphQLSchemaElement> moveUp(
            GraphQLSchemaElement parent,
            Map<NodeZipper<GraphQLSchemaElement>, Breadcrumb<GraphQLSchemaElement>> sameParentsZipper) {
        Set<NodeZipper<GraphQLSchemaElement>> sameParent = sameParentsZipper.keySet();
        assertNotEmpty(sameParent, () -> "expected at least one zipper");

        Map<String, List<GraphQLSchemaElement>> childrenMap = new HashMap<>(SCHEMA_ELEMENT_ADAPTER.getNamedChildren(parent));
        Map<String, Integer> indexCorrection = new HashMap<>();

        List<ZipperWithOneParent> zipperWithOneParents = new ArrayList<>();
        for (NodeZipper<GraphQLSchemaElement> zipper : sameParent) {
            Breadcrumb<GraphQLSchemaElement> breadcrumb = sameParentsZipper.get(zipper);
            zipperWithOneParents.add(new ZipperWithOneParent(zipper, breadcrumb));
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

    private static class ZipperWithOneParent {
        public NodeZipper<GraphQLSchemaElement> zipper;
        public Breadcrumb<GraphQLSchemaElement> parent;

        public ZipperWithOneParent(NodeZipper<GraphQLSchemaElement> zipper, Breadcrumb<GraphQLSchemaElement> parent) {
            this.zipper = zipper;
            this.parent = parent;
        }
    }

    // artificial schema element which serves as root element for the transformation
    private static class DummyRoot implements GraphQLSchemaElement {

        static final String QUERY = "query";
        static final String MUTATION = "mutation";
        static final String SUBSCRIPTION = "subscription";
        static final String ADD_TYPES = "addTypes";
        static final String DIRECTIVES = "directives";
        static final String SCHEMA_DIRECTIVES = "schemaDirectives";
        static final String SCHEMA_APPLIED_DIRECTIVES = "schemaAppliedDirectives";
        static final String INTROSPECTION = "introspection";
        static final String SCHEMA_ELEMENT = "schemaElement";

        GraphQLSchema schema;
        GraphQLObjectType query;
        GraphQLObjectType mutation;
        GraphQLObjectType subscription;
        GraphQLObjectType introspectionSchemaType;
        Set<GraphQLType> additionalTypes;
        Set<GraphQLDirective> directives;
        Set<GraphQLDirective> schemaDirectives;
        Set<GraphQLAppliedDirective> schemaAppliedDirectives;
        GraphQLSchemaElement schemaElement;

        DummyRoot(GraphQLSchema schema) {
            this.schema = schema;
            query = schema.getQueryType();
            mutation = schema.isSupportingMutations() ? schema.getMutationType() : null;
            subscription = schema.isSupportingSubscriptions() ? schema.getSubscriptionType() : null;
            additionalTypes = schema.getAdditionalTypes();
            schemaDirectives = new LinkedHashSet<>(schema.getSchemaDirectives());
            schemaAppliedDirectives = new LinkedHashSet<>(schema.getSchemaAppliedDirectives());
            directives = new LinkedHashSet<>(schema.getDirectives());
            introspectionSchemaType = schema.getIntrospectionSchemaType();
        }

        DummyRoot(GraphQLSchemaElement schemaElement) {
            this.schemaElement = schemaElement;
        }

        @Override
        public GraphQLSchemaElement copy() {
            return assertShouldNeverHappen();
        }

        @Override
        public List<GraphQLSchemaElement> getChildren() {
            return assertShouldNeverHappen();
        }

        @Override
        public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
            SchemaElementChildrenContainer.Builder builder = newSchemaElementChildrenContainer();
            if (schemaElement != null) {
                builder.child(SCHEMA_ELEMENT, schemaElement);
            } else {
                builder.child(QUERY, query);
                if (schema.isSupportingMutations()) {
                    builder.child(MUTATION, mutation);
                }
                if (schema.isSupportingSubscriptions()) {
                    builder.child(SUBSCRIPTION, subscription);
                }
                builder.children(ADD_TYPES, additionalTypes);
                builder.children(DIRECTIVES, directives);
                builder.children(SCHEMA_DIRECTIVES, schemaDirectives);
                builder.children(SCHEMA_APPLIED_DIRECTIVES, schemaAppliedDirectives);
                builder.child(INTROSPECTION, introspectionSchemaType);
            }
            return builder.build();
        }

        @Override
        public GraphQLSchemaElement withNewChildren(SchemaElementChildrenContainer newChildren) {
            if (this.schemaElement != null) {
                this.schemaElement = newChildren.getChildOrNull(SCHEMA_ELEMENT);
                return this;
            }
            // special hack: we don't create a new dummy root, but we simply update it
            query = newChildren.getChildOrNull(QUERY);
            mutation = newChildren.getChildOrNull(MUTATION);
            subscription = newChildren.getChildOrNull(SUBSCRIPTION);
            introspectionSchemaType = newChildren.getChildOrNull(INTROSPECTION);
            additionalTypes = new LinkedHashSet<>(newChildren.getChildren(ADD_TYPES));
            directives = new LinkedHashSet<>(newChildren.getChildren(DIRECTIVES));
            schemaDirectives = new LinkedHashSet<>(newChildren.getChildren(SCHEMA_DIRECTIVES));
            schemaAppliedDirectives = new LinkedHashSet<>(newChildren.getChildren(SCHEMA_APPLIED_DIRECTIVES));
            return this;
        }

        @Override
        public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
            return assertShouldNeverHappen();
        }

        public GraphQLSchema rebuildSchema(GraphQLCodeRegistry.Builder codeRegistry) {
            return GraphQLSchema.newSchema()
                    .query(this.query)
                    .mutation(this.mutation)
                    .subscription(this.subscription)
                    .additionalTypes(this.additionalTypes)
                    .additionalDirectives(this.directives)
                    .introspectionSchemaType(this.introspectionSchemaType)
                    .withSchemaDirectives(this.schemaDirectives)
                    .withSchemaAppliedDirectives(this.schemaAppliedDirectives)
                    .codeRegistry(codeRegistry.build())
                    .description(schema.getDescription())
                    .build();
        }
    }
}

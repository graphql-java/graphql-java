package graphql.validation.rules;


import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import graphql.Internal;
import graphql.language.Definition;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.Node;
import graphql.validation.AbstractRule;
import graphql.validation.DocumentVisitor;
import graphql.validation.LanguageTraversal;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

@Internal
public class NoFragmentCycles extends AbstractRule {

    private final Map<String, Set<String>> fragmentSpreads = new HashMap<>();

    public NoFragmentCycles(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
        prepareFragmentMap();
    }

    private void prepareFragmentMap() {
        List<Definition> definitions = getValidationContext().getDocument().getDefinitions();
        for (Definition definition : definitions) {
            if (definition instanceof FragmentDefinition) {
                FragmentDefinition fragmentDefinition = (FragmentDefinition) definition;
                fragmentSpreads.put(fragmentDefinition.getName(), gatherSpreads(fragmentDefinition));
            }
        }
    }

    private Set<String> gatherSpreads(FragmentDefinition fragmentDefinition) {
        final Set<String> fragmentSpreads = new HashSet<>();
        DocumentVisitor visitor = new DocumentVisitor() {
            @Override
            public void enter(Node node, List<Node> path) {
                if (node instanceof FragmentSpread) {
                    fragmentSpreads.add(((FragmentSpread) node).getName());
                }
            }

            @Override
            public void leave(Node node, List<Node> path) {

            }
        };

        new LanguageTraversal().traverse(fragmentDefinition, visitor);
        return fragmentSpreads;
    }

    @Override
    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {
        LinkedList<String> path = new LinkedList<>();
        path.add(0, fragmentDefinition.getName());
        Map<String, Set<String>> transitiveSpreads = buildTransitiveSpreads(path, new HashMap<>());

        for (Map.Entry<String, Set<String>> entry : transitiveSpreads.entrySet()) {
            if (entry.getValue().contains(entry.getKey())) {
                String message = "Fragment cycles not allowed";
                addError(ValidationErrorType.FragmentCycle, Collections.singletonList(fragmentDefinition), message);
            }
        }
    }

    private Map<String, Set<String>> buildTransitiveSpreads(LinkedList<String> path, Map<String, Set<String>> transitiveSpreads) {
        String name = path.peekFirst();

        if (transitiveSpreads.containsKey(name)) {
            return transitiveSpreads;
        }

        Set<String> spreads = fragmentSpreads.get(name);

        // spreads may be null when there is no corresponding FragmentDefinition for this spread.
        // This will be handled by KnownFragmentNames
        if (spreads == null || spreads.isEmpty()) {
            return transitiveSpreads;
        }

        // Add the current spreads to the transitive spreads of each ancestor in the traversal path
        for (String ancestor : path) {
            Set<String> ancestorSpreads = transitiveSpreads.get(ancestor);
            if (ancestorSpreads == null) {
                ancestorSpreads = new HashSet<>();
            }
            ancestorSpreads.addAll(spreads);
            transitiveSpreads.put(ancestor, ancestorSpreads);
        }

        for (String child : spreads) {
            // don't recurse infinitely, expect the recursion check to happen in checkFragmentDefinition
            if (path.contains(child) || transitiveSpreads.containsKey(child)) {
                continue;
            }

            // descend into each spread in the current fragment
            LinkedList<String> childPath = new LinkedList<>(path);
            childPath.add(0, child);
            buildTransitiveSpreads(childPath, transitiveSpreads);
        }
        return transitiveSpreads;
    }
}

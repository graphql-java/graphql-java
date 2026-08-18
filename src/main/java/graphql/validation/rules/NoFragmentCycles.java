package graphql.validation.rules;


import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

import static graphql.validation.ValidationErrorType.FragmentCycle;

@Internal
public class NoFragmentCycles extends AbstractRule {

    private final Map<String, Set<String>> fragmentSpreads = new LinkedHashMap<>();
    private final Set<String> fragmentsWithCycleErrors = new HashSet<>();

    public NoFragmentCycles(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
        prepareFragmentMap();
        findFragmentCycles();
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
        final Set<String> fragmentSpreads = new LinkedHashSet<>();
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
        if (!fragmentsWithCycleErrors.contains(fragmentDefinition.getName())) {
            return;
        }
        String message = i18n(FragmentCycle, "NoFragmentCycles.cyclesNotAllowed");
        addError(ValidationErrorType.FragmentCycle, Collections.singletonList(fragmentDefinition), message);
    }

    private void findFragmentCycles() {
        Set<String> visitedFragments = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : fragmentSpreads.entrySet()) {
            if (!visitedFragments.add(entry.getKey())) {
                continue;
            }
            findFragmentCycles(entry.getKey(), entry.getValue(), visitedFragments);
        }
    }

    private void findFragmentCycles(String firstFragment, Set<String> firstSpreads, Set<String> visitedFragments) {
        Set<String> visitingFragments = new HashSet<>();
        Deque<String> fragmentStack = new ArrayDeque<>();
        Deque<Iterator<String>> spreadIteratorStack = new ArrayDeque<>();
        visitingFragments.add(firstFragment);
        fragmentStack.push(firstFragment);
        spreadIteratorStack.push(firstSpreads.iterator());

        while (!fragmentStack.isEmpty()) {
            Iterator<String> spreadIterator = spreadIteratorStack.getFirst();
            if (!spreadIterator.hasNext()) {
                visitingFragments.remove(fragmentStack.pop());
                spreadIteratorStack.pop();
                continue;
            }

            String childFragment = spreadIterator.next();
            Set<String> childSpreads = fragmentSpreads.get(childFragment);
            if (childSpreads == null) {
                continue;
            }
            if (visitingFragments.contains(childFragment)) {
                fragmentsWithCycleErrors.add(childFragment);
                fragmentsWithCycleErrors.add(fragmentStack.getFirst());
                continue;
            }
            if (!visitedFragments.add(childFragment)) {
                continue;
            }

            visitingFragments.add(childFragment);
            fragmentStack.push(childFragment);
            spreadIteratorStack.push(childSpreads.iterator());
        }
    }
}

package graphql.validation.rules;


import graphql.language.Definition;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.Node;
import graphql.validation.*;

import java.util.*;

public class NoFragmentCycles extends AbstractRule {

    private Map<String, List<FragmentSpread>> fragmentSpreads = new LinkedHashMap<>();

    private Set<String> foundCycles = new LinkedHashSet<>();

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


    private List<FragmentSpread> gatherSpreads(FragmentDefinition fragmentDefinition) {
        final List<FragmentSpread> fragmentSpreads = new ArrayList<>();
        QueryLanguageVisitor visitor = new QueryLanguageVisitor() {
            @Override
            public void enter(Node node, List<Node> path) {
                if (node instanceof FragmentSpread) {
                    fragmentSpreads.add((FragmentSpread) node);
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
        List<FragmentSpread> spreadPath = new ArrayList<>();
        detectCycleRecursive(fragmentDefinition.getName(), fragmentDefinition.getName(), spreadPath);
    }

    private void detectCycleRecursive(String fragmentName, String initialName, List<FragmentSpread> spreadPath) {
        List<FragmentSpread> fragmentSpreads = this.fragmentSpreads.get(fragmentName);

        outer:
        for (FragmentSpread fragmentSpread : fragmentSpreads) {
            if (foundCycles.contains(fragmentSpread.getName())) {
                continue;
            }
            if (fragmentSpread.getName().equals(initialName)) {
                addError(new ValidationError(ValidationErrorType.FragmentCycle));
                continue;
            }
            for (FragmentSpread spread : spreadPath) {
                if (spread.equals(fragmentSpread)) {
                    continue outer;
                }
            }
            spreadPath.add(fragmentSpread);
            detectCycleRecursive(fragmentSpread.getName(), initialName, spreadPath);
            spreadPath.remove(spreadPath.size() - 1);
        }
    }
}

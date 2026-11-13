package graphql.language;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Internal
@NullMarked
public class AstSignatureReferenceCollector {

    private final AstSignatureInputReferences operationReferences = new AstSignatureInputReferences();
    private final Set<String> operationFragmentSpreads = new LinkedHashSet<>();
    private final Map<String, AstSignatureInputReferences> fragmentReferencesByName = new LinkedHashMap<>();
    private final Map<String, Set<String>> fragmentSpreadsByName = new LinkedHashMap<>();

    public AstSignatureInputReferences getOperationReferences() {
        return operationReferences;
    }

    public Set<String> getOperationFragmentSpreads() {
        return operationFragmentSpreads;
    }

    public AstSignatureInputReferences getFragmentReferences(String fragmentName) {
        return fragmentReferencesByName.computeIfAbsent(fragmentName, ignored -> new AstSignatureInputReferences());
    }

    public Set<String> getFragmentSpreads(String fragmentName) {
        return fragmentSpreadsByName.computeIfAbsent(fragmentName, ignored -> new LinkedHashSet<>());
    }

    public AstSignatureInputReferences toReferences() {
        AstSignatureInputReferences references = new AstSignatureInputReferences();
        references.addAll(operationReferences);
        addFragmentReferences(operationFragmentSpreads, references, new LinkedHashSet<>());
        return references;
    }

    private void addFragmentReferences(Set<String> fragmentNames,
                                       AstSignatureInputReferences references,
                                       Set<String> visitedFragmentNames) {
        for (String fragmentName : fragmentNames) {
            addFragmentReference(fragmentName, references, visitedFragmentNames);
        }
    }

    private void addFragmentReference(String fragmentName,
                                      AstSignatureInputReferences references,
                                      Set<String> visitedFragmentNames) {
        if (!visitedFragmentNames.add(fragmentName)) {
            return;
        }

        AstSignatureInputReferences fragmentReferences = fragmentReferencesByName.get(fragmentName);
        if (fragmentReferences != null) {
            references.addAll(fragmentReferences);
        }

        Set<String> fragmentSpreads = fragmentSpreadsByName.get(fragmentName);
        if (fragmentSpreads != null) {
            addFragmentReferences(fragmentSpreads, references, visitedFragmentNames);
        }
    }
}

package graphql.normalized;

import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.language.Argument;
import graphql.language.AstComparator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Internal
public class ENFMerger {

    public static void merge(ExecutableNormalizedField parent, List<ExecutableNormalizedField> childrenWithSameResultKey) {
        // they have all the same result key
        // we can only merge the fields if they have the same field name + arguments + all children are the same
        List<Set<ExecutableNormalizedField>> possibleGroupsToMerge = new ArrayList<>();
        for (ExecutableNormalizedField field : childrenWithSameResultKey) {
            boolean addToGroup = false;
            overPossibleGroups:
            for (Set<ExecutableNormalizedField> group : possibleGroupsToMerge) {
                for (ExecutableNormalizedField fieldInGroup : group) {
                    if (field.getFieldName().equals(fieldInGroup.getFieldName()) && sameArguments(field.getAstArguments(), fieldInGroup.getAstArguments())) {
                        addToGroup = true;
                        group.add(field);
                        continue overPossibleGroups;
                    }
                }
            }
            if (!addToGroup) {
                LinkedHashSet<ExecutableNormalizedField> group = new LinkedHashSet<>();
                group.add(field);
                possibleGroupsToMerge.add(group);
            }
        }
        for (Set<ExecutableNormalizedField> groupOfFields : possibleGroupsToMerge) {
            // for each group we check if it could be merged
            List<Set<ExecutableNormalizedField>> listOfChildrenForGroup = new ArrayList<>();
            for (ExecutableNormalizedField fieldInGroup : groupOfFields) {
                Set<ExecutableNormalizedField> childrenSets = new LinkedHashSet<>(fieldInGroup.getChildren());
                listOfChildrenForGroup.add(childrenSets);
            }
            boolean mergeable = areFieldSetsTheSame(listOfChildrenForGroup);
            if (mergeable) {
                Set<String> mergedObjects = new LinkedHashSet<>();
                groupOfFields.forEach(f -> mergedObjects.addAll(f.getObjectTypeNames()));
                // patching the first one to contain more objects, remove all others
                Iterator<ExecutableNormalizedField> iterator = groupOfFields.iterator();
                ExecutableNormalizedField first = iterator.next();
                while (iterator.hasNext()) {
                    parent.getChildren().remove(iterator.next());
                }
                first.setObjectTypeNames(mergedObjects);
            }
        }
    }


    private static boolean areFieldSetsTheSame(List<Set<ExecutableNormalizedField>> listOfSets) {
        if (listOfSets.size() == 0 || listOfSets.size() == 1) {
            return true;
        }
        Set<ExecutableNormalizedField> first = listOfSets.get(0);
        Iterator<Set<ExecutableNormalizedField>> iterator = listOfSets.iterator();
        iterator.next();
        while (iterator.hasNext()) {
            Set<ExecutableNormalizedField> set = iterator.next();
            if (!compareTwoFieldSets(first, set)) {
                return false;
            }
        }
        List<Set<ExecutableNormalizedField>> nextLevel = new ArrayList<>();
        for (Set<ExecutableNormalizedField> set : listOfSets) {
            for (ExecutableNormalizedField fieldInSet : set) {
                nextLevel.add(new LinkedHashSet<>(fieldInSet.getChildren()));
            }
        }
        return areFieldSetsTheSame(nextLevel);
    }

    private static boolean compareTwoFieldSets(Set<ExecutableNormalizedField> setOne, Set<ExecutableNormalizedField> setTwo) {
        if (setOne.size() != setTwo.size()) {
            return false;
        }
        for (ExecutableNormalizedField field : setOne) {
            if (!isContained(field, setTwo)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isContained(ExecutableNormalizedField searchFor, Set<ExecutableNormalizedField> set) {
        for (ExecutableNormalizedField field : set) {
            if (compareWithoutChildren(searchFor, field)) {
                return true;
            }
        }
        return false;
    }

    private static boolean compareWithoutChildren(ExecutableNormalizedField one, ExecutableNormalizedField two) {

        if (!one.getObjectTypeNames().equals(two.getObjectTypeNames())) {
            return false;
        }
        if (!Objects.equals(one.getAlias(), two.getAlias())) {
            return false;
        }
        if (!Objects.equals(one.getFieldName(), two.getFieldName())) {
            return false;
        }
        if (!sameArguments(one.getAstArguments(), two.getAstArguments())) {
            return false;
        }
        return true;
    }

    private static boolean compareOneWithASet(Set<ExecutableNormalizedField> fields) {
        if (fields.size() == 1) {
            return true;
        }
        ExecutableNormalizedField first = fields.iterator().next();
        Set<String> objectTypeNames = first.getObjectTypeNames();
        String alias = first.getAlias();
        String fieldName = first.getFieldName();
        ImmutableList<Argument> arguments = first.getAstArguments();
        Iterator<ExecutableNormalizedField> iterator = fields.iterator();
        iterator.next();
        while (iterator.hasNext()) {
            ExecutableNormalizedField field = iterator.next();
            if (!field.getObjectTypeNames().equals(objectTypeNames)) {
                return false;
            }
            if (!Objects.equals(alias, field.getAlias())) {
                return false;
            }
            if (!Objects.equals(fieldName, field.getFieldName())) {
                return false;
            }
            if (!sameArguments(arguments, field.getAstArguments())) {
                return false;
            }
        }
        return true;
    }

    // copied from graphql.validation.rules.OverlappingFieldsCanBeMerged
    private static boolean sameArguments(List<Argument> arguments1, List<Argument> arguments2) {
        if (arguments1.size() != arguments2.size()) {
            return false;
        }
        for (Argument argument : arguments1) {
            Argument matchedArgument = findArgumentByName(argument.getName(), arguments2);
            if (matchedArgument == null) {
                return false;
            }
            if (!AstComparator.sameValue(argument.getValue(), matchedArgument.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static Argument findArgumentByName(String name, List<Argument> arguments) {
        for (Argument argument : arguments) {
            if (argument.getName().equals(name)) {
                return argument;
            }
        }
        return null;
    }

}

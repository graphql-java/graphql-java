package graphql.validation.rules.experimental.overlappingfieldsmerge;

import graphql.language.Field;
import graphql.language.Node;
import graphql.language.SourceLocation;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationError;
import graphql.validation.rules.OverlappingFieldsCanBeMerged;
import graphql.validation.rules.OverlappingFieldsCanBeMerged.Conflict;
import graphql.validation.rules.OverlappingFieldsCanBeMerged.FieldAndType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static graphql.validation.ValidationError.newValidationError;
import static graphql.validation.ValidationErrorType.FieldsConflict;

/**
 * Inspired by Simon Adameit's implementation in Sangria https://github.com/sangria-graphql-org/sangria/pull/12
 */
class CachedCheck {

    static BiFunction<Field, Field, Boolean> alwaysCheck = (f1, f2) -> false;

    private final AbstractRule rule;

    /**
     * We cache by FieldSet and use SortedArraySet, as it is fast to compare and iterate over
     */
    private HashMap<SortedArraySet<SelectionField>, FieldSetCache> cache = new HashMap<>();

    public CachedCheck(AbstractRule rule) {
        this.rule = rule;
    }

    void checkFieldsInSetCanMerge(SortedArraySet<SelectionField> fields) {
        getCacheLine(fields).checkFieldsInSetCanMerge();
    }

    private FieldSetCache getCacheLine(SortedArraySet<SelectionField> fields) {
        return cache.computeIfAbsent(fields, FieldSetCache::new);
    }

    private class FieldSetCache {

        private final SortedArraySet<SelectionField> fields;

        private ArrayList<FieldSetCache> cacheGroupByOutputNames;
        private FieldSetCache cacheMergeChildSelections;
        private ArrayList<FieldSetCache> cacheGroupByCommonParentTypes;

        private boolean didRequireSameResponseShape = false;
        private boolean didRequireSameFieldNameAndArguments = false;
        private boolean didCheckSameResponseShape = false;
        private boolean didCheckSameFieldsForCoincidentParentTypes = false;

        private FieldSetCache(SortedArraySet<SelectionField> fields) {
            this.fields = fields;
        }

        void checkFieldsInSetCanMerge() {
            checkSameResponseShape();
            checkSameFieldsForCoincidentParentTypes();
        }

        private void checkSameResponseShape() {
            if (didCheckSameResponseShape) {
                return;
            }
            didCheckSameResponseShape = true;
            groupByOutputNames().forEach(fieldSet ->
                    fieldSet
                            .requireSameResponseShape()
                            .mergeChildSelections()
                            .checkSameResponseShape()
            );
        }

        private void checkSameFieldsForCoincidentParentTypes() {
            if (didCheckSameFieldsForCoincidentParentTypes) {
                return;
            }
            didCheckSameFieldsForCoincidentParentTypes = true;
            groupByOutputNames().forEach(fieldSet -> {
                fieldSet.groupByCommonParentTypes().forEach(fieldSet2 -> {
                    fieldSet2
                            .requireSameFieldNameAndArguments()
                            .mergeChildSelections()
                            .checkSameFieldsForCoincidentParentTypes();
                });
            });
        }

        private FieldSetCache requireSameResponseShape() {
            if (didRequireSameResponseShape) {
                return this;
            }
            didRequireSameResponseShape = true;
            Map<TypeShape.Known, ArrayList<SelectionField>> fieldsWithKnownResponseShapes = groupByKnownResponseShape();
            int responseShapesNumber = fieldsWithKnownResponseShapes.size();
            if (responseShapesNumber > 1) {
                List<Map.Entry<TypeShape.Known, ArrayList<SelectionField>>> buckets =
                        new ArrayList<>(fieldsWithKnownResponseShapes.entrySet());
                String outputName = buckets.get(0).getValue().get(0).getOutputName();
                for (int i = 0; i < buckets.size() - 1; i++) {
                    for (int j = i + 1; j < buckets.size(); j++) {
                        Map.Entry<TypeShape.Known, ArrayList<SelectionField>> a = buckets.get(i);
                        Map.Entry<TypeShape.Known, ArrayList<SelectionField>> b = buckets.get(j);

                        checkConflict(outputName, a.getValue(), b.getValue());
                    }
                }
            }
            return this;
        }

        private Map<TypeShape.Known, ArrayList<SelectionField>> groupByKnownResponseShape() {
            Map<TypeShape.Known, ArrayList<SelectionField>> fieldsWithKnownResponseShapes = new LinkedHashMap<>();
            fields.forEach(field -> {
                TypeShape typeShape = field.outputTypeShape();
                if (typeShape instanceof TypeShape.Known) {
                    fieldsWithKnownResponseShapes.computeIfAbsent((TypeShape.Known) typeShape, key -> new ArrayList<>())
                            .add(field);
                }
            });
            return fieldsWithKnownResponseShapes;
        }

        private FieldSetCache requireSameFieldNameAndArguments() {
            if (didRequireSameFieldNameAndArguments) {
                return this;
            }
            didRequireSameFieldNameAndArguments = true;
            Map<FieldNameAndArguments, ArrayList<SelectionField>> fieldsWithSameNameAndArguments = groupByFieldNameAndArguments();
            int fieldNameAndArgumentsNumber = fieldsWithSameNameAndArguments.size();
            if (fieldNameAndArgumentsNumber > 1) {
                List<Map.Entry<FieldNameAndArguments, ArrayList<SelectionField>>> buckets =
                        new ArrayList<>(fieldsWithSameNameAndArguments.entrySet());
                String outputName = buckets.get(0).getValue().get(0).getOutputName();
                for (int i = 0; i < buckets.size() - 1; i++) {
                    for (int j = i + 1; j < buckets.size(); j++) {
                        Map.Entry<FieldNameAndArguments, ArrayList<SelectionField>> a = buckets.get(i);
                        Map.Entry<FieldNameAndArguments, ArrayList<SelectionField>> b = buckets.get(j);

                        checkConflict(outputName, a.getValue(), b.getValue());
                    }
                }
            }
            return this;
        }

        private Map<FieldNameAndArguments, ArrayList<SelectionField>> groupByFieldNameAndArguments() {
            Map<FieldNameAndArguments, ArrayList<SelectionField>> fieldsWithSameNameAndArguments = new LinkedHashMap<>();
            fields.forEach(field -> {
                fieldsWithSameNameAndArguments
                        .computeIfAbsent(field.fieldNameAndArguments(), key -> new ArrayList<>())
                        .add(field);
            });
            return fieldsWithSameNameAndArguments;
        }

        private ArrayList<FieldSetCache> groupByOutputNames() {
            if (cacheGroupByOutputNames == null) {
                Map<String, SortedArraySet.Builder<SelectionField>> outputNames = new LinkedHashMap<>();
                fields.forEach(field -> {
                    outputNames
                            .computeIfAbsent(field.getOutputName(), t -> newFieldSetBuilder())
                            .add(field);
                });
                ArrayList<FieldSetCache> result = new ArrayList<>(outputNames.size());
                outputNames.values()
                        .forEach(builder -> result.add(getCacheLine(builder.build())));
                cacheGroupByOutputNames = result;
            }
            return cacheGroupByOutputNames;
        }

        private void checkConflict(String outputName, ArrayList<SelectionField> a, ArrayList<SelectionField> b) {
            SelectionField selectionFieldA = a.get(0);
            SelectionField selectionFieldB = b.get(0);

            Conflict conflict = OverlappingFieldsCanBeMerged.findConflict(
                    alwaysCheck, outputName,
                    new FieldAndType(selectionFieldA.astField, selectionFieldA.outputType, selectionFieldA.parentType),
                    new FieldAndType(selectionFieldB.astField, selectionFieldB.outputType, selectionFieldB.parentType));
            if (conflict != null) {
                List<SourceLocation> locationList = new ArrayList<>();
                for (Node<?> node : conflict.fields) {
                    locationList.add(node.getSourceLocation());
                }
                ValidationError.Builder error = newValidationError()
                        .validationErrorType(FieldsConflict)
                        .sourceLocations(locationList)
                        //.queryPath(queryPath); // TODO we are missing query path here
                        .description(conflict.reason);
                rule.getValidationErrorCollector().addError(error.build());
            }
        }

        private FieldSetCache mergeChildSelections() {
            if (cacheMergeChildSelections == null) {
                cacheMergeChildSelections = getCacheLine(SelectionField.children(fields));
            }
            return cacheMergeChildSelections;
        }

        private ArrayList<FieldSetCache> groupByCommonParentTypes() {
            if (cacheGroupByCommonParentTypes == null) {
                ArrayList<SelectionField> fieldsWithAbstractParentTypes = new ArrayList<>();
                LinkedHashMap<TypeAbstractness.Concrete, SortedArraySet.Builder<SelectionField>> fieldsWithConcreteParents =
                        new LinkedHashMap<>();
                fields.forEach(field -> {
                    TypeAbstractness typeAbstractness = field.parentTypeAbstractness();
                    if (typeAbstractness.equals(TypeAbstractness.Abstract.INSTANCE)) {
                        fieldsWithAbstractParentTypes.add(field);
                    } else if (typeAbstractness instanceof TypeAbstractness.Concrete) {
                        fieldsWithConcreteParents.computeIfAbsent((TypeAbstractness.Concrete) typeAbstractness, t -> newFieldSetBuilder())
                                .add(field);
                    }
                });
                cacheGroupByCommonParentTypes = combineAbstractAndConcreteParentTypes(fieldsWithAbstractParentTypes,
                        fieldsWithConcreteParents);
            }
            return cacheGroupByCommonParentTypes;
        }

        private ArrayList<FieldSetCache> combineAbstractAndConcreteParentTypes(
                ArrayList<SelectionField> fieldsWithAbstractParentTypes,
                LinkedHashMap<TypeAbstractness.Concrete, SortedArraySet.Builder<SelectionField>> fieldsWithConreteParents
        ) {
            if (fieldsWithConreteParents.isEmpty()) {
                if (fieldsWithAbstractParentTypes.isEmpty()) {
                    return new ArrayList<>(0);
                } else {
                    ArrayList<FieldSetCache> list = new ArrayList<>(1);
                    SortedArraySet<SelectionField> set = newFieldSetBuilder(fieldsWithAbstractParentTypes.size())
                            .addAll(fieldsWithAbstractParentTypes)
                            .build();
                    list.add(getCacheLine(set));
                    return list;
                }
            } else {
                ArrayList<FieldSetCache> list = new ArrayList<>(fieldsWithConreteParents.size());
                if (fieldsWithAbstractParentTypes.isEmpty()) {
                    fieldsWithConreteParents.values().forEach(builder -> {
                        list.add(getCacheLine(builder.build()));
                    });
                } else {
                    fieldsWithConreteParents.values().forEach(builder -> {
                        SortedArraySet<SelectionField> set = builder.addAll(fieldsWithAbstractParentTypes).build();
                        list.add(getCacheLine(set));
                    });
                }
                return list;
            }
        }
    }

    private SortedArraySet.Builder<SelectionField> newFieldSetBuilder() {
        return SortedArraySet.newBuilder(SelectionField.comparator());
    }

    private SortedArraySet.Builder<SelectionField> newFieldSetBuilder(int sizeHint) {
        return SortedArraySet.newBuilder(sizeHint, SelectionField.comparator());
    }
}
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.language.Node;
import graphql.schema.GraphQLType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *
 * @author gkesler
 */
public class ResultCollector {
    public ResultCollector operation (NodeVertex<? extends Node, ? extends GraphQLType> operation) {
        Objects.requireNonNull(operation);
        
        operations.add((NodeVertex<Node, GraphQLType>)operation);        
        return this;
    }
    
    public List<Object> joinOn (String on, List<Object> relation, List<Object> target) {
        Objects.requireNonNull(on);
        Objects.requireNonNull(relation);
        Objects.requireNonNull(target);
                
        // will join relation dataset to target dataset
        // since we don't have any keys to build a cartesian product,
        // will be assuming that relation and target dataset s are alerady sorted by the
        // same key, so a pair {target[i], relation[i]} represents that cartesian product
        // we can use to join them together
        int[] indexHolder = {0};
        relation
            .stream()
            .limit(Math.min(target.size(), relation.size()))
            .forEach(o -> {
                int index = indexHolder[0]++;
                Map<String, Object> targetMap = (Map<String, Object>)target.get(index);
                if (targetMap != null) {
                    targetMap.put(on, relation.get(index));
                }
            });
        
        return target;
    }
    
    public Object getResult () {
        List<Object> result = operations
            .stream()
            .map(NodeVertex::getResult)
            .collect(Collectors.toList());
        
        return result.size() > 1
            ? result
            : result.get(0);
    }
    
    private final Collection<NodeVertex<Node, GraphQLType>> operations = new ArrayList<>();
}

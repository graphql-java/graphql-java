/*
 * Copyright (c) 2011-2017 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package graphql.tuples;


import graphql.Internal;

import java.util.Objects;
import java.util.function.Function;

/**
 * A tuple that holds five non-null values
 *
 * @param <T1> The type of the first non-null value held by this tuple
 * @param <T2> The type of the second non-null value held by this tuple
 * @param <T3> The type of the third non-null value held by this tuple
 * @param <T4> The type of the fourth non-null value held by this tuple
 * @param <T5> The type of the fifth non-null value held by this tuple
 *
 * @author Jon Brisbin
 * @author Stephane Maldini
 */
@Internal
public class Tuple5<T1, T2, T3, T4, T5> extends Tuple4<T1, T2, T3, T4> {

    private static final long serialVersionUID = 3541548454198133275L;

    final T5 t5;

    Tuple5(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) {
        super(t1, t2, t3, t4);
        this.t5 = Objects.requireNonNull(t5, "t5");
    }

    /**
     * Type-safe way to get the fifth object of this {@link Tuples}.
     *
     * @return The fifth object
     */
    public T5 getT5() {
        return t5;
    }

    /**
     * Map the 1st part (T1) of this {@link Tuple5} into a different value and type,
     * keeping the other parts.
     *
     * @param mapper the mapping {@link Function} for the T1 part
     * @param <R>    the new type for the T1 part
     *
     * @return a new {@link Tuple5} with a different T1 value
     */
    public <R> Tuple5<R, T2, T3, T4, T5> mapT1(Function<T1, R> mapper) {
        return new Tuple5<>(mapper.apply(t1), t2, t3, t4, t5);
    }

    /**
     * Map the 2nd part (T2) of this {@link Tuple5} into a different value and type,
     * keeping the other parts.
     *
     * @param mapper the mapping {@link Function} for the T2 part
     * @param <R>    the new type for the T2 part
     *
     * @return a new {@link Tuple5} with a different T2 value
     */
    public <R> Tuple5<T1, R, T3, T4, T5> mapT2(Function<T2, R> mapper) {
        return new Tuple5<>(t1, mapper.apply(t2), t3, t4, t5);
    }

    /**
     * Map the 3rd part (T3) of this {@link Tuple5} into a different value and type,
     * keeping the other parts.
     *
     * @param mapper the mapping {@link Function} for the T3 part
     * @param <R>    the new type for the T3 part
     *
     * @return a new {@link Tuple5} with a different T3 value
     */
    public <R> Tuple5<T1, T2, R, T4, T5> mapT3(Function<T3, R> mapper) {
        return new Tuple5<>(t1, t2, mapper.apply(t3), t4, t5);
    }

    /**
     * Map the 4th part (T4) of this {@link Tuple5} into a different value and type,
     * keeping the other parts.
     *
     * @param mapper the mapping {@link Function} for the T4 part
     * @param <R>    the new type for the T4 part
     *
     * @return a new {@link Tuple5} with a different T4 value
     */
    public <R> Tuple5<T1, T2, T3, R, T5> mapT4(Function<T4, R> mapper) {
        return new Tuple5<>(t1, t2, t3, mapper.apply(t4), t5);
    }

    /**
     * Map the 5th part (T5) of this {@link Tuple5} into a different value and type,
     * keeping the other parts.
     *
     * @param mapper the mapping {@link Function} for the T5 part
     * @param <R>    the new type for the T5 part
     *
     * @return a new {@link Tuple5} with a different T5 value
     */
    public <R> Tuple5<T1, T2, T3, T4, R> mapT5(Function<T5, R> mapper) {
        return new Tuple5<>(t1, t2, t3, t4, mapper.apply(t5));
    }

    @Override
    public Object get(int index) {
        switch (index) {
            case 0:
                return t1;
            case 1:
                return t2;
            case 2:
                return t3;
            case 3:
                return t4;
            case 4:
                return t5;
            default:
                return null;
        }
    }

    @Override
    public Object[] toArray() {
        return new Object[]{t1, t2, t3, t4, t5};
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Tuple5)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        @SuppressWarnings("rawtypes")
        Tuple5 tuple5 = (Tuple5) o;

        return t5.equals(tuple5.t5);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + t5.hashCode();
        return result;
    }

    @Override
    public int size() {
        return 5;
    }
}

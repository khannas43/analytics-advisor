package gov.rajasthan.smart.srse.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One aggregate in a grouped match result. {@code column} is null for {@code COUNT(*)}.
 * {@code distinct} applies only to {@link AggregateFunction#COUNT}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AggregateSpec(
        AggregateFunction function,
        DisplayColumn column,
        boolean distinct,
        String alias) {

    public AggregateSpec {
        if (function == null) {
            throw new IllegalArgumentException("function is required");
        }
        if (function != AggregateFunction.COUNT && column == null) {
            throw new IllegalArgumentException(function + " requires a column");
        }
        if (distinct && function != AggregateFunction.COUNT) {
            throw new IllegalArgumentException("distinct applies only to COUNT");
        }
    }

    public boolean countStar() {
        return function == AggregateFunction.COUNT && column == null;
    }
}

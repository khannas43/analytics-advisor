package gov.rajasthan.smart.srse.compiler;

import org.springframework.stereotype.Component;

/**
 * Placeholder {@link FieldResolver}.
 *
 * <p>SRSE resolved abstract field keys (e.g. {@code age_years}) to physical
 * columns through a welfare-scheme field catalogue, which this product does not
 * have. The only remaining caller is the Analysis age filter, inherited from
 * SRSE and not yet reviewed for this product.
 *
 * <p>So every key fails loudly here rather than resolving to something invented.
 * TODO: either drop the age filter from the Analysis match, or replace this with
 * a resolver backed by whatever field concept this product settles on.
 */
@Component
public class StubFieldResolver implements FieldResolver {

    @Override
    public String resolveColumn(String fieldKey) {
        throw new UnknownFieldException(fieldKey);
    }
}

package gov.rajasthan.smart.srse.compiler;

import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService.RegisteredColumn;
import gov.rajasthan.smart.srse.lakehouse.QualifiedColumn;
import gov.rajasthan.smart.srse.lakehouse.QualifiedTable;
import org.springframework.stereotype.Component;

/**
 * Resolves rule predicate columns through the registry's two gates (§4.5 / AA-13).
 */
@Component
public class RuleColumnResolver {

    private final LakehouseRegistryService registry;

    public RuleColumnResolver(LakehouseRegistryService registry) {
        this.registry = registry;
    }

    public RegisteredColumn resolve(QualifiedColumn column, QualifiedTable expectedTable) {
        if (!column.table().equals(expectedTable)) {
            throw new IllegalArgumentException(
                    "Rule column must be on " + expectedTable.qualifiedName()
                            + ", not " + column.table().qualifiedName());
        }
        return registry.describeColumns(column.table(), java.util.List.of(column.column()))
                .get(column.column());
    }
}

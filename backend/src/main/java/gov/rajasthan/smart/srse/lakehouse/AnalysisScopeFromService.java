package gov.rajasthan.smart.srse.lakehouse;

import gov.rajasthan.smart.srse.scope.ScopeNode;
import gov.rajasthan.smart.srse.scope.ScopeNodeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Single choke point for scoped table references in Analysis SQL (§7.2.3).
 * Every officer-facing FROM should be built through {@link #planFrom(QualifiedTable)}.
 */
@Service
public class AnalysisScopeFromService {

    private final OfficerRegistryScopeService officerScope;
    private final RegisteredTableRepository registeredTableRepository;
    private final RegisteredTableScopeCatalog scopeCatalog;
    private final ScopeNodeRepository scopeNodeRepository;
    private final ScopeFilterProperties scopeFilterProperties;

    public AnalysisScopeFromService(
            OfficerRegistryScopeService officerScope,
            RegisteredTableRepository registeredTableRepository,
            RegisteredTableScopeCatalog scopeCatalog,
            ScopeNodeRepository scopeNodeRepository,
            ScopeFilterProperties scopeFilterProperties) {
        this.officerScope = officerScope;
        this.registeredTableRepository = registeredTableRepository;
        this.scopeCatalog = scopeCatalog;
        this.scopeNodeRepository = scopeNodeRepository;
        this.scopeFilterProperties = scopeFilterProperties;
    }

    public ScopeFilteredFrom planFrom(QualifiedTable table) {
        TableScopePolicy.OfficerScopeView officer = officerScope.currentOfficerScope();
        if (officer.bypassDataScoping()) {
            return ScopeFilteredFrom.unfiltered(table.qualifiedName());
        }
        RegisteredTable registration = registeredTableRepository
                .findByCatalogNameAndSchemaNameAndTableName(
                        table.catalog(), table.schema(), table.table())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Table is not registered for SRSE: " + table.qualifiedName()));
        TableScopePolicy.TableScopeMetadata metadata = scopeCatalog.metadataFor(registration);
        if (metadata.sharedReference()) {
            return ScopeFilteredFrom.unfiltered(table.qualifiedName());
        }
        return planFrom(officer, table.qualifiedName(), metadata);
    }

    /** Fixed officer view — saved-query share validation and unit tests (AA-18). */
    public ScopeFilteredFrom planFrom(
            TableScopePolicy.OfficerScopeView officer,
            String qualifiedName,
            TableScopePolicy.TableScopeMetadata metadata) {
        Map<Long, List<TableScopePolicy.AssignmentNode>> assignments = officer.assignmentsByDimension();
        if (assignments == null || assignments.isEmpty()) {
            return ScopeFilteredFrom.emptyResult(qualifiedName);
        }
        List<String> conjuncts = new ArrayList<>();
        List<Object> bindValues = new ArrayList<>();
        List<Long> dimensionIds = new ArrayList<>(assignments.keySet());
        dimensionIds.sort(Long::compareTo);
        for (long dimensionId : dimensionIds) {
            List<TableScopePolicy.AssignmentNode> assignmentNodes = assignments.get(dimensionId);
            if (assignmentNodes == null || assignmentNodes.isEmpty()) {
                continue;
            }
            if (metadata.exemptDimensionIds().contains(dimensionId)) {
                continue;
            }
            Optional<TableScopePolicy.LevelBinding> binding =
                    TableScopePolicy.chosenBindingForDimension(officer, metadata, dimensionId);
            if (binding.isEmpty()) {
                return ScopeFilteredFrom.emptyResult(qualifiedName);
            }
            List<ScopeNode> nodes = scopeNodeRepository.findByDimensionWithFetchOrderByPath(dimensionId);
            List<String> codes = ScopePredicateCodeResolver.codesForBinding(
                    assignmentNodes, binding.get().levelDepth(), nodes);
            if (codes.isEmpty()) {
                return ScopeFilteredFrom.emptyResult(qualifiedName);
            }
            if (codes.size() > scopeFilterProperties.maxInValues()) {
                throw new IllegalArgumentException(
                        "Scope filter for " + qualifiedName + " in dimension " + dimensionId
                                + " would bind " + codes.size() + " values (limit "
                                + scopeFilterProperties.maxInValues()
                                + "). Narrow assignments or raise SRSE_SCOPE_MAX_IN_VALUES.");
            }
            LakehouseIdentifiers.requireSafe("column", binding.get().columnName());
            conjuncts.add(ScopeFilteredFrom.inClause(binding.get().columnName(), codes.size()));
            bindValues.addAll(codes);
        }
        if (conjuncts.isEmpty()) {
            return ScopeFilteredFrom.unfiltered(qualifiedName);
        }
        return ScopeFilteredFrom.filtered(qualifiedName, String.join(" AND ", conjuncts), bindValues);
    }
}

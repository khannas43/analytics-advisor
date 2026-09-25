package gov.rajasthan.smart.srse.lakehouse;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RegisteredTableScopeCatalog {

    private final TableScopeBindingRepository bindingRepository;
    private final TableDimensionExemptionRepository exemptionRepository;

    public RegisteredTableScopeCatalog(
            TableScopeBindingRepository bindingRepository,
            TableDimensionExemptionRepository exemptionRepository) {
        this.bindingRepository = bindingRepository;
        this.exemptionRepository = exemptionRepository;
    }

    public Map<Long, TableScopePolicy.TableScopeMetadata> metadataForTables(List<RegisteredTable> tables) {
        if (tables.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = tables.stream().map(RegisteredTable::getId).toList();
        List<TableScopeBinding> bindings = bindingRepository.findByRegisteredTableIdsWithLevel(ids);
        List<TableDimensionExemption> exemptions = exemptionRepository.findByRegisteredTableIdsWithDimension(ids);

        Map<Long, List<TableScopePolicy.LevelBinding>> bindingsByTable = new HashMap<>();
        for (TableScopeBinding binding : bindings) {
            bindingsByTable.computeIfAbsent(binding.getRegisteredTable().getId(), k -> new ArrayList<>())
                    .add(new TableScopePolicy.LevelBinding(
                            binding.getScopeLevel().getId(),
                            binding.getScopeLevel().getDimension().getId(),
                            binding.getScopeLevel().getDepth(),
                            binding.getColumnName()));
        }
        Map<Long, Set<Long>> exemptByTable = new HashMap<>();
        for (TableDimensionExemption exemption : exemptions) {
            exemptByTable.computeIfAbsent(exemption.getRegisteredTable().getId(), k -> new HashSet<>())
                    .add(exemption.getScopeDimension().getId());
        }

        Map<Long, TableScopePolicy.TableScopeMetadata> result = new HashMap<>();
        for (RegisteredTable table : tables) {
            result.put(
                    table.getId(),
                    new TableScopePolicy.TableScopeMetadata(
                            table.isSharedReference(),
                            List.copyOf(bindingsByTable.getOrDefault(table.getId(), List.of())),
                            Set.copyOf(exemptByTable.getOrDefault(table.getId(), Set.of()))));
        }
        return result;
    }

    public TableScopePolicy.TableScopeMetadata metadataFor(RegisteredTable table) {
        return metadataForTables(List.of(table)).get(table.getId());
    }
}

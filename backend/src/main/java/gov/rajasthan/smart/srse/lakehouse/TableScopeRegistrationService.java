package gov.rajasthan.smart.srse.lakehouse;

import gov.rajasthan.smart.srse.identity.AdminAccessDeniedException;
import gov.rajasthan.smart.srse.identity.AdminAuthorizationService;
import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
import gov.rajasthan.smart.srse.scope.ScopeDimensionRepository;
import gov.rajasthan.smart.srse.scope.ScopeLevel;
import gov.rajasthan.smart.srse.scope.ScopeLevelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TableScopeRegistrationService {

    private final RegisteredTableRepository registeredTableRepository;
    private final TableScopeBindingRepository bindingRepository;
    private final TableDimensionExemptionRepository exemptionRepository;
    private final ScopeLevelRepository levelRepository;
    private final ScopeDimensionRepository dimensionRepository;
    private final LakehouseBrowseService browse;
    private final AuthenticatedUserService authenticatedUserService;
    private final AdminAuthorizationService adminAuthorization;

    public TableScopeRegistrationService(
            RegisteredTableRepository registeredTableRepository,
            TableScopeBindingRepository bindingRepository,
            TableDimensionExemptionRepository exemptionRepository,
            ScopeLevelRepository levelRepository,
            ScopeDimensionRepository dimensionRepository,
            LakehouseBrowseService browse,
            AuthenticatedUserService authenticatedUserService,
            AdminAuthorizationService adminAuthorization) {
        this.registeredTableRepository = registeredTableRepository;
        this.bindingRepository = bindingRepository;
        this.exemptionRepository = exemptionRepository;
        this.levelRepository = levelRepository;
        this.dimensionRepository = dimensionRepository;
        this.browse = browse;
        this.authenticatedUserService = authenticatedUserService;
        this.adminAuthorization = adminAuthorization;
    }

    @Transactional(readOnly = true)
    public TableScopeConfigView getConfig(long registrationId) {
        RegisteredTable table = requireRegistration(registrationId);
        return toView(table);
    }

    @Transactional
    public TableScopeConfigView replaceConfig(long registrationId, TableScopeConfigRequest request) {
        AppUser caller = authenticatedUserService.requireCurrentUser();
        RegisteredTable table = requireRegistration(registrationId);
        if (request.sharedReference() != null && request.sharedReference() && !adminAuthorization.isSuperAdmin(caller)) {
            throw new AdminAccessDeniedException("Only a SuperAdmin may mark a table as shared reference");
        }
        if (request.sharedReference() != null) {
            table.setSharedReference(request.sharedReference());
            registeredTableRepository.save(table);
        }
        bindingRepository.deleteByRegisteredTableId(registrationId);
        exemptionRepository.deleteByRegisteredTableId(registrationId);

        if (request.bindings() != null) {
            Set<Long> seenLevels = new HashSet<>();
            for (BindingRequest binding : request.bindings()) {
                if (!seenLevels.add(binding.scopeLevelId())) {
                    throw new IllegalArgumentException("Duplicate binding for the same level");
                }
                ScopeLevel level = levelRepository.findById(binding.scopeLevelId())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown scope level"));
                assertColumnExists(table, binding.columnName());
                bindingRepository.save(new TableScopeBinding(table, level, binding.columnName().trim()));
            }
        }
        if (request.exemptDimensionIds() != null) {
            for (Long dimensionId : request.exemptDimensionIds()) {
                var dimension = dimensionRepository.findById(dimensionId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown dimension"));
                exemptionRepository.save(new TableDimensionExemption(table, dimension));
            }
        }
        return toView(table);
    }

    @Transactional
    public void deleteScopeDataForRegistration(long registrationId) {
        bindingRepository.deleteByRegisteredTableId(registrationId);
        exemptionRepository.deleteByRegisteredTableId(registrationId);
    }

    private void assertColumnExists(RegisteredTable table, String columnName) {
        LakehouseIdentifiers.requireSafe("column", columnName);
        Set<String> live = browse.listColumns(
                        table.getCatalogName(), table.getSchemaName(), table.getTableName())
                .stream()
                .map(c -> c.name().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (!live.contains(columnName.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Unknown column on registered table: " + table.toQualifiedTable().qualifiedName()
                            + "." + columnName);
        }
    }

    private RegisteredTable requireRegistration(long id) {
        return registeredTableRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No such registration: " + id));
    }

    private TableScopeConfigView toView(RegisteredTable table) {
        List<TableScopeBinding> bindings = bindingRepository.findByRegisteredTableIdWithLevel(table.getId());
        List<TableDimensionExemption> exemptions =
                exemptionRepository.findByRegisteredTableIdWithDimension(table.getId());
        return new TableScopeConfigView(
                table.getId(),
                table.isSharedReference(),
                bindings.stream()
                        .map(b -> new BindingView(
                                b.getId(),
                                b.getScopeLevel().getId(),
                                b.getScopeLevel().getDimension().getId(),
                                b.getScopeLevel().getDepth(),
                                b.getScopeLevel().getName(),
                                b.getColumnName()))
                        .toList(),
                exemptions.stream()
                        .map(e -> e.getScopeDimension().getId())
                        .toList());
    }

    public record BindingRequest(long scopeLevelId, String columnName) {
    }

    public record TableScopeConfigRequest(
            List<BindingRequest> bindings,
            List<Long> exemptDimensionIds,
            Boolean sharedReference) {
    }

    public record BindingView(
            long id,
            long scopeLevelId,
            long dimensionId,
            int levelDepth,
            String levelName,
            String columnName) {
    }

    public record TableScopeConfigView(
            long registrationId,
            boolean sharedReference,
            List<BindingView> bindings,
            List<Long> exemptDimensionIds) {
    }
}

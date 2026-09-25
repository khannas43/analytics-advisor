package gov.rajasthan.smart.srse.scope;

import gov.rajasthan.smart.srse.identity.AdminAccessDeniedException;
import gov.rajasthan.smart.srse.identity.AdminAuthorizationService;
import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.UserScopeAssignmentRepository;
import gov.rajasthan.smart.srse.scope.admin.ScopeAdminDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ScopeHierarchyService {

    private final ScopeDimensionRepository dimensionRepository;
    private final ScopeLevelRepository levelRepository;
    private final ScopeNodeRepository nodeRepository;
    private final UserScopeAssignmentRepository assignmentRepository;
    private final AdminAuthorizationService authorization;

    public ScopeHierarchyService(
            ScopeDimensionRepository dimensionRepository,
            ScopeLevelRepository levelRepository,
            ScopeNodeRepository nodeRepository,
            UserScopeAssignmentRepository assignmentRepository,
            AdminAuthorizationService authorization) {
        this.dimensionRepository = dimensionRepository;
        this.levelRepository = levelRepository;
        this.nodeRepository = nodeRepository;
        this.assignmentRepository = assignmentRepository;
        this.authorization = authorization;
    }

    public List<ScopeAdminDtos.DimensionView> listDimensions() {
        return dimensionRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(d -> new ScopeAdminDtos.DimensionView(
                        d.getId(), d.getCode(), d.getName(), d.getDisplayOrder()))
                .toList();
    }

    @Transactional
    public ScopeAdminDtos.DimensionView createDimension(AppUser caller, ScopeAdminDtos.CreateDimensionRequest req) {
        authorization.assertSuperAdminOnly(caller);
        if (dimensionRepository.findByCodeIgnoreCase(req.code()).isPresent()) {
            throw new IllegalArgumentException("Dimension code already exists");
        }
        ScopeDimension dimension = dimensionRepository.save(
                new ScopeDimension(req.code().trim(), req.name().trim(), req.displayOrder()));
        return new ScopeAdminDtos.DimensionView(
                dimension.getId(), dimension.getCode(), dimension.getName(), dimension.getDisplayOrder());
    }

    @Transactional
    public ScopeAdminDtos.DimensionView updateDimension(
            AppUser caller, long id, ScopeAdminDtos.UpdateDimensionRequest req) {
        authorization.assertSuperAdminOnly(caller);
        ScopeDimension dimension = dimensionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dimension not found"));
        dimension.setName(req.name().trim());
        dimension.setDisplayOrder(req.displayOrder());
        return new ScopeAdminDtos.DimensionView(
                dimension.getId(), dimension.getCode(), dimension.getName(), dimension.getDisplayOrder());
    }

    public List<ScopeAdminDtos.LevelView> listLevels(long dimensionId) {
        return levelRepository.findByDimensionIdOrderByDepthAsc(dimensionId).stream()
                .map(l -> new ScopeAdminDtos.LevelView(
                        l.getId(), l.getDimension().getId(), l.getDepth(), l.getName()))
                .toList();
    }

    @Transactional
    public ScopeAdminDtos.LevelView createLevel(AppUser caller, ScopeAdminDtos.CreateLevelRequest req) {
        authorization.assertSuperAdminOnly(caller);
        ScopeDimension dimension = dimensionRepository.findById(req.dimensionId())
                .orElseThrow(() -> new IllegalArgumentException("Dimension not found"));
        if (levelRepository.findByDimensionIdAndDepth(req.dimensionId(), req.depth()).isPresent()) {
            throw new IllegalArgumentException("Level depth already exists for dimension");
        }
        ScopeLevel level = levelRepository.save(new ScopeLevel(dimension, req.depth(), req.name().trim()));
        return new ScopeAdminDtos.LevelView(level.getId(), dimension.getId(), level.getDepth(), level.getName());
    }

    @Transactional
    public ScopeAdminDtos.LevelView updateLevel(AppUser caller, long id, ScopeAdminDtos.UpdateLevelRequest req) {
        authorization.assertSuperAdminOnly(caller);
        ScopeLevel level = levelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Level not found"));
        level.setName(req.name().trim());
        return new ScopeAdminDtos.LevelView(
                level.getId(), level.getDimension().getId(), level.getDepth(), level.getName());
    }

    public List<ScopeAdminDtos.NodeView> listNodes(AppUser caller, Long dimensionId) {
        List<ScopeNode> nodes = dimensionId == null
                ? nodeRepository.findAllWithDimensionOrderByPath()
                : nodeRepository.findByDimensionWithFetchOrderByPath(dimensionId);
        Map<Long, List<String>> callerPaths = authorization.assignmentPaths(caller.getId());
        boolean superAdmin = authorization.isSuperAdmin(caller);
        List<ScopeAdminDtos.NodeView> views = new ArrayList<>();
        for (ScopeNode node : nodes) {
            if (!superAdmin && !authorization.callerCoversNodePath(callerPaths, node)) {
                continue;
            }
            views.add(toNodeView(node, superAdmin || authorization.callerCoversNodePath(callerPaths, node)));
        }
        return views;
    }

    public List<ScopeAdminDtos.NodeView> grantableNodes(AppUser caller) {
        List<ScopeNode> nodes = nodeRepository.findAllWithDimensionOrderByPath();
        Map<Long, List<String>> callerPaths = authorization.assignmentPaths(caller.getId());
        boolean superAdmin = authorization.isSuperAdmin(caller);
        return nodes.stream()
                .filter(n -> superAdmin || authorization.callerCoversNodePath(callerPaths, n))
                .map(n -> toNodeView(n, true))
                .toList();
    }

    @Transactional
    public ScopeAdminDtos.NodeView createNode(AppUser caller, ScopeAdminDtos.CreateNodeRequest req) {
        ScopeDimension dimension = dimensionRepository.findById(req.dimensionId())
                .orElseThrow(() -> new IllegalArgumentException("Dimension not found"));
        ScopeLevel level = levelRepository.findById(req.levelId())
                .orElseThrow(() -> new IllegalArgumentException("Level not found"));
        if (level.getDimension().getId() != dimension.getId()) {
            throw new IllegalArgumentException("Level does not belong to dimension");
        }
        ScopeNode parent = null;
        if (req.parentId() != null) {
            parent = nodeRepository.findById(req.parentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent node not found"));
            if (parent.getDimension().getId() != dimension.getId()) {
                throw new IllegalArgumentException("Parent belongs to another dimension");
            }
        }
        authorization.assertCanCreateNodeUnderParent(caller, parent);
        String path = ScopePathBuilder.deriveChildPath(parent, req.code());
        ScopeNode node = nodeRepository.save(
                new ScopeNode(dimension, level, parent, req.code().trim(), req.name().trim(), path));
        return toNodeView(node, true);
    }

    @Transactional
    public ScopeAdminDtos.NodeView updateNode(AppUser caller, long id, ScopeAdminDtos.UpdateNodeRequest req) {
        ScopeNode node = nodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Node not found"));
        if (!authorization.isSuperAdmin(caller)) {
            Map<Long, List<String>> callerPaths = authorization.assignmentPaths(caller.getId());
            if (!authorization.callerCoversNodePath(callerPaths, node)) {
                throw new AdminAccessDeniedException("Node is outside your scope");
            }
        }
        node.setName(req.name().trim());
        return toNodeView(node, true);
    }

    @Transactional
    public void deleteNode(AppUser caller, long id) {
        ScopeNode node = nodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Node not found"));
        if (!authorization.isSuperAdmin(caller)) {
            Map<Long, List<String>> callerPaths = authorization.assignmentPaths(caller.getId());
            if (!authorization.callerCoversNodePath(callerPaths, node)) {
                throw new AdminAccessDeniedException("Node is outside your scope");
            }
        }
        long childCount = nodeRepository.countByParentId(id);
        if (childCount > 0) {
            throw new IllegalStateException(
                    "Cannot delete node: " + childCount + " descendant node(s) still exist");
        }
        long assignmentCount = assignmentRepository.countByScopeNodeId(id);
        if (assignmentCount > 0) {
            throw new IllegalStateException(
                    "Cannot delete node: " + assignmentCount + " user scope assignment(s) reference it");
        }
        nodeRepository.delete(node);
    }

    private static ScopeAdminDtos.NodeView toNodeView(ScopeNode node, boolean grantable) {
        return new ScopeAdminDtos.NodeView(
                node.getId(),
                node.getDimension().getId(),
                node.getLevel().getId(),
                node.getParent() == null ? null : node.getParent().getId(),
                node.getCode(),
                node.getName(),
                node.getPath(),
                grantable);
    }
}

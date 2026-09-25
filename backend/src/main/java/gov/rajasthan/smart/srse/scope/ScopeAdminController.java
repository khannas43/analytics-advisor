package gov.rajasthan.smart.srse.scope;

import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
import gov.rajasthan.smart.srse.scope.admin.ScopeAdminDtos;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/scope")
public class ScopeAdminController {

    private final ScopeHierarchyService hierarchyService;
    private final AuthenticatedUserService authenticatedUserService;

    public ScopeAdminController(
            ScopeHierarchyService hierarchyService, AuthenticatedUserService authenticatedUserService) {
        this.hierarchyService = hierarchyService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @GetMapping("/dimensions")
    public List<ScopeAdminDtos.DimensionView> listDimensions() {
        return hierarchyService.listDimensions();
    }

    @PostMapping("/dimensions")
    @ResponseStatus(HttpStatus.CREATED)
    public ScopeAdminDtos.DimensionView createDimension(@RequestBody ScopeAdminDtos.CreateDimensionRequest req) {
        return hierarchyService.createDimension(authenticatedUserService.requireCurrentUser(), req);
    }

    @PutMapping("/dimensions/{id}")
    public ScopeAdminDtos.DimensionView updateDimension(
            @PathVariable long id, @RequestBody ScopeAdminDtos.UpdateDimensionRequest req) {
        return hierarchyService.updateDimension(authenticatedUserService.requireCurrentUser(), id, req);
    }

    @GetMapping("/levels")
    public List<ScopeAdminDtos.LevelView> listLevels(@RequestParam long dimensionId) {
        return hierarchyService.listLevels(dimensionId);
    }

    @PostMapping("/levels")
    @ResponseStatus(HttpStatus.CREATED)
    public ScopeAdminDtos.LevelView createLevel(@RequestBody ScopeAdminDtos.CreateLevelRequest req) {
        return hierarchyService.createLevel(authenticatedUserService.requireCurrentUser(), req);
    }

    @PutMapping("/levels/{id}")
    public ScopeAdminDtos.LevelView updateLevel(
            @PathVariable long id, @RequestBody ScopeAdminDtos.UpdateLevelRequest req) {
        return hierarchyService.updateLevel(authenticatedUserService.requireCurrentUser(), id, req);
    }

    @GetMapping("/nodes")
    public List<ScopeAdminDtos.NodeView> listNodes(@RequestParam(required = false) Long dimensionId) {
        return hierarchyService.listNodes(authenticatedUserService.requireCurrentUser(), dimensionId);
    }

    @GetMapping("/nodes/grantable")
    public ScopeAdminDtos.GrantableNodesResponse grantableNodes() {
        return new ScopeAdminDtos.GrantableNodesResponse(
                hierarchyService.grantableNodes(authenticatedUserService.requireCurrentUser()));
    }

    @PostMapping("/nodes")
    @ResponseStatus(HttpStatus.CREATED)
    public ScopeAdminDtos.NodeView createNode(@RequestBody ScopeAdminDtos.CreateNodeRequest req) {
        return hierarchyService.createNode(authenticatedUserService.requireCurrentUser(), req);
    }

    @PutMapping("/nodes/{id}")
    public ScopeAdminDtos.NodeView updateNode(
            @PathVariable long id, @RequestBody ScopeAdminDtos.UpdateNodeRequest req) {
        return hierarchyService.updateNode(authenticatedUserService.requireCurrentUser(), id, req);
    }

    @DeleteMapping("/nodes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNode(@PathVariable long id) {
        hierarchyService.deleteNode(authenticatedUserService.requireCurrentUser(), id);
    }
}

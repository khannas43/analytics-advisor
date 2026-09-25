package gov.rajasthan.smart.srse.scope.admin;

import java.util.List;

public final class ScopeAdminDtos {

    private ScopeAdminDtos() {
    }

    public record DimensionView(long id, String code, String name, int displayOrder) {
    }

    public record LevelView(long id, long dimensionId, int depth, String name) {
    }

    public record NodeView(
            long id,
            long dimensionId,
            long levelId,
            Long parentId,
            String code,
            String name,
            String path,
            boolean grantable) {
    }

    public record CreateDimensionRequest(String code, String name, int displayOrder) {
    }

    public record UpdateDimensionRequest(String name, int displayOrder) {
    }

    public record CreateLevelRequest(long dimensionId, int depth, String name) {
    }

    public record UpdateLevelRequest(String name) {
    }

    public record CreateNodeRequest(long dimensionId, long levelId, Long parentId, String code, String name) {
    }

    public record UpdateNodeRequest(String name) {
    }

    public record GrantableNodesResponse(List<NodeView> nodes) {
    }
}

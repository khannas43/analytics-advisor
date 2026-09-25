package gov.rajasthan.smart.srse.scope;

/** Derives materialised paths for {@code scope_node} (never caller-supplied). */
public final class ScopePathBuilder {

    private ScopePathBuilder() {
    }

    public static String deriveChildPath(ScopeNode parent, String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Node code is required");
        }
        if (code.contains("/")) {
            throw new IllegalArgumentException("Node code must not contain '/'");
        }
        String segment = code.trim();
        if (parent == null) {
            return "/" + segment + "/";
        }
        String parentPath = parent.getPath();
        if (!parentPath.endsWith("/")) {
            parentPath = parentPath + "/";
        }
        return parentPath + segment + "/";
    }
}

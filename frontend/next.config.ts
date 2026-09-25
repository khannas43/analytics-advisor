import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async redirects() {
    return [
      { source: "/extract", destination: "/query-builder", permanent: false },
      { source: "/analysis", destination: "/query-builder", permanent: false },
      { source: "/database-overview", destination: "/overview", permanent: false },
    ];
  },
};

export default nextConfig;

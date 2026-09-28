"use client";

import type { Ref } from "react";

export function AdminSectionHeader({
  title,
  description,
  headingRef,
}: Readonly<{
  title: string;
  description: string;
  headingRef?: Ref<HTMLHeadingElement>;
}>) {
  return (
    <header className="admin-section-header">
      <h1 ref={headingRef} tabIndex={-1} className="admin-section-title">
        {title}
      </h1>
      <p className="admin-section-description">{description}</p>
    </header>
  );
}

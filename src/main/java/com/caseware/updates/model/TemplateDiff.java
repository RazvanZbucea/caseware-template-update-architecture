package com.caseware.updates.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic net JSON diff between two versions (fromVersion -> toVersion) of a Product Template.
 */
public record TemplateDiff(
        String templateId,
        int fromVersion,
        int toVersion,
        List<SectionChange> sectionChanges
) {
    public TemplateDiff {
        Objects.requireNonNull(templateId, "templateId must not be null");
        if (fromVersion < 1 || toVersion <= fromVersion) {
            throw new IllegalArgumentException("Invalid version range: " + fromVersion + " -> " + toVersion);
        }
        sectionChanges = List.copyOf(Objects.requireNonNull(sectionChanges, "sectionChanges must not be null"));
    }

    public Set<String> allSectionIds() {
        return sectionChanges.stream()
                .map(SectionChange::sectionId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> allJsonPaths() {
        return sectionChanges.stream()
                .flatMap(sc -> sc.modifiedJsonPaths().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> jsonPathsForSection(String sectionId) {
        return sectionChanges.stream()
                .filter(sc -> sc.sectionId().equals(sectionId))
                .flatMap(sc -> sc.modifiedJsonPaths().stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}

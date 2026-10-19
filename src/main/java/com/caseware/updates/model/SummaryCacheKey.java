package com.caseware.updates.model;

public record SummaryCacheKey(String templateId, int fromVersion, int toVersion) {}

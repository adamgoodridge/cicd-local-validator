package net.adamgoodridge.cicd_local_validator.domain;

public record ValidationIssue(String path, String message) {
}

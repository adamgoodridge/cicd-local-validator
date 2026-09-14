package net.adamgoodridge.cicd_local_validator.web;

import net.adamgoodridge.cicd_local_validator.domain.PipelineResultStatus;

import java.util.UUID;

record RunStatus(UUID id, PipelineResultStatus status) {
}

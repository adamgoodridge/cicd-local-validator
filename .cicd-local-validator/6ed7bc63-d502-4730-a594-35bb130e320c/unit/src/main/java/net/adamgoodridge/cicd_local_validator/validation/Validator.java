package net.adamgoodridge.cicd_local_validator.validation;

import net.adamgoodridge.cicd_local_validator.ErrorMessages;
import lombok.*;
import net.adamgoodridge.cicd_local_validator.domain.*;

@AllArgsConstructor
public class Validator {
	private final ValidationContext context;

	public void registerAndValidateJob(JobDefinition job) {
		if (!context.jobs().containsKey(job.name())) {
			context.jobs().put(job.name(), job);
		} else {
			context.issues().add(new ValidationIssue(job.name(), "Duplicate Job name."));
		}
		validateRequiredFields(job);
		validateStage(job);
		validateNeeds(job);
	}
	private void validateRequiredFields(JobDefinition job) {
		if (job.isImageBlank()) {
			context.issues().add(new ValidationIssue(job.name() + ".image", ErrorMessages.JOB_IMAGE_BLANK));
		}
		if (job.script().isEmpty()) {
			context.issues().add(new ValidationIssue(job.name() + ".script", ErrorMessages.JOB_SCRIPT_BLANK));
		}
	}

	private void validateStage(JobDefinition job) {
		if (!context.stageNames().contains(job.stage())) {
			context.issues().add(new ValidationIssue(job.name() + ".stage", ErrorMessages.JOB_STAGE_NOT_FOUND));
		}
	}

	private void validateNeeds(JobDefinition job) {
		for (String need : job.needs()) {
			if (!context.jobs().containsKey(need)
					&& context.pipeline().jobs().stream().noneMatch(candidate -> need.equals(candidate.name()))) {
				context.issues().add(new ValidationIssue(job.name() + ".needs",
						ErrorMessages.JOB_NEEDS_NOT_FOUND + ": " + need));
			}
		}
	}

}

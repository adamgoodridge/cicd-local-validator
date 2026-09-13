package net.adamgoodridge.cicd_local_validator.validation;

import net.adamgoodridge.cicd_local_validator.ErrorMessages;
import net.adamgoodridge.cicd_local_validator.domain.JobDefinition;
import net.adamgoodridge.cicd_local_validator.domain.PipelineDefinition;
import net.adamgoodridge.cicd_local_validator.domain.StaticValidationResult;
import net.adamgoodridge.cicd_local_validator.domain.ValidationIssue;
import net.adamgoodridge.cicd_local_validator.pipeline.PipelineParseResult;
import net.adamgoodridge.cicd_local_validator.pipeline.PipelineParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PipelineValidator {
	private final PipelineParser parser;

	public PipelineValidator(PipelineParser parser) {
		this.parser = parser;
	}

	public StaticValidationResult validate(String source) {
		PipelineParseResult parsed = parser.parse(source);
		List<ValidationIssue> issues = new ArrayList<>(parsed.issues());
		if (!parsed.isValid()) {
			return new StaticValidationResult(issues);
		}
		validatePipeline(parsed.pipeline(), issues);
		return new StaticValidationResult(issues);
	}

	private void validatePipeline(PipelineDefinition pipeline, List<ValidationIssue> issues) {
		ValidationContext context = new ValidationContext(
				pipeline,
				new HashSet<>(pipeline.stages()),
				new java.util.HashMap<>(),
				issues);
		Validator validator = new Validator(context);
		for (JobDefinition job : pipeline.jobs()) {
			if (job.isNameBlank()) {
				context.issues().add(new ValidationIssue("jobs", ErrorMessages.JOB_NAME_BLANK));
				continue;
			}
			validator.registerAndValidateJob(job);
		}
		new CycleValidator(context).validate();
	}


}

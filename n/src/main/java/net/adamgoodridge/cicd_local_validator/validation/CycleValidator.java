package net.adamgoodridge.cicd_local_validator.validation;

import lombok.*;
import net.adamgoodridge.cicd_local_validator.domain.*;

import java.util.*;

@AllArgsConstructor
public class CycleValidator {
	private final ValidationContext context;
	public void validate() {
		Set<String> visiting = new HashSet<>();
		Set<String> visited = new HashSet<>();
		for (String jobName : context.jobs().keySet()) {
			if (hasCycle(jobName, context, visiting, visited)) {
				context.issues().add(new ValidationIssue(jobName + ".needs", "Dependency cycle detected."));
				return;
			}
		}
	}

	private boolean hasCycle(
			String jobName,
			ValidationContext context,
			Set<String> visiting,
			Set<String> visited) {
		if (visiting.contains(jobName)) {
			return true;
		}
		if (visited.contains(jobName)) {
			return false;
		}
		JobDefinition job = context.jobs().get(jobName);
		if (job == null) {
			return false;
		}
		visiting.add(jobName);
		for (String need : job.needs()) {
			if (hasCycle(need, context, visiting, visited)) {
				return true;
			}
		}
		visiting.remove(jobName);
		visited.add(jobName);
		return false;
	}
}

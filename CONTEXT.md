# CI/CD Local Validation

This context describes the domain language and expected behavior for validating GitLab CI/CD pipelines locally. It distinguishes pipeline configuration from an individual validation run and from the results produced by executing jobs.

## Pipeline Language

**Pipeline**:
The complete GitLab CI configuration being validated.
_Avoid_: workflow, configuration file when referring to the whole execution model

**Job**:
One configured unit of CI work in a Pipeline.
_Avoid_: task, container; a container is the execution environment, not the Job itself

**Validation Run**:
One local attempt to validate a Pipeline.
_Avoid_: build, deployment, execution

**Dependency**:
A relationship that determines when a Job may run, based on GitLab `needs` and stage semantics.
_Avoid_: prerequisite when describing the full scheduling relationship

**Stage**:
A named grouping that supplies the default ordering for Jobs when explicit Dependencies do not override it.
_Avoid_: phase when referring to GitLab CI ordering

## Execution Language

**Job Result**:
The outcome of one Job in a Validation Run, including success, failure, skipped, or allowed failure.
_Avoid_: job status when discussing the complete recorded outcome

**Pipeline Result**:
The aggregate outcome of a Validation Run, including whether the Pipeline passed despite allowed failures.
_Avoid_: build result

**Allowed Failure**:
A Job failure that remains visible in its Job Result but does not block eligible dependent Jobs or fail the Pipeline Result.
_Avoid_: ignored failure

**Artifact**:
A declared file or directory produced by a Job and made available to eligible dependent Jobs.
_Avoid_: workspace file; unlisted workspace files are not Artifacts

**Variable**:
A named environment value available to a Job during execution.
_Avoid_: property when referring to the runtime environment

**Validation Workspace**:
The isolated filesystem associated with one Validation Run, including source files and declared Artifacts.
_Avoid_: shared workspace

## Scheduling Rules

**Needs Scheduling**:
When a Job declares `needs`, those Dependencies determine when it can run. Jobs without explicit `needs` follow GitLab stage ordering.

**Failure Propagation**:
A failed Job blocks normal dependent Jobs. Independent Jobs may continue, and Jobs marked `when: always` may run according to their Dependencies.

**Optional Dependency**:
A `needs` relationship that does not block scheduling when its target Job is unavailable.
_Avoid_: soft dependency

## Variable Rules

**Variable Precedence**:
Runtime values are resolved from lowest to highest precedence as `cicd.yamlVariable.*` properties, top-level pipeline yamlVariables, and Job-level yamlVariables. Built-in CI yamlVariables are available to Jobs and may be overridden where GitLab permits.

**Secret Variable**:
A Variable whose name or value is sensitive and must be passed to the Job without being printed or persisted by default.
_Avoid_: hidden yamlVariable; hiding is a presentation concern, while sensitivity is a domain property

## Configuration Language

**Template**:
A reusable Pipeline definition that contributes Job configuration to another Pipeline.
_Avoid_: copy-paste configuration

**Static Validation**:
The structural validation of a Pipeline before any Job is executed, including syntax, schema, image references, yamlVariable references, dependency cycles, and invalid dependency names.
_Avoid_: runtime validation

**Execution Validation**:
The Docker-backed validation that runs eligible Jobs after Static Validation succeeds.
_Avoid_: deployment; this process validates behavior but does not deploy to GitLab

## Result Language

**Blocked**:
A Job that could not be started because a required local or external dependency was unavailable or because its execution was not explicitly authorized.
_Avoid_: failed; a Job is Failed only after it starts and exits unsuccessfully

**Skipped**:
A Job that was not run because GitLab scheduling rules, a failed required Dependency, or a conditional rule excluded it.
_Avoid_: blocked; Skipped means the scheduler excluded it, while Blocked means execution could not proceed

**Validation Evidence**:
The machine-readable record of a Validation Run, containing each Job Result, exit status, duration, dependency decision, skip or block reason, Artifact transfer, redacted Variables, container identity, and logs.
_Avoid_: console output; console output is a presentation of the evidence, not the authoritative record

## Validation Boundaries

**Validation Goal**:
A passing Validation Run is evidence that the local execution is sufficiently compatible with GitLab for the supported Pipeline features. It is not a guarantee that GitLab-hosted runners, permissions, networks, or unavailable external services will behave identically.

**Execution Opt-In**:
Static Validation is allowed by default, but Execution Validation requires explicit user authorization because a Pipeline contains executable code and may request Docker resources.

**Supported Pipeline Features**:
The first supported feature set includes Jobs with `image` and `script`, Variables, Stages, `needs`, `allow_failure`, `when: always`, Artifacts, and Templates. Services, caches, includes, rules, matrix Jobs, and parallel Jobs are outside this initial boundary.

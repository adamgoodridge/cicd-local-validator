# Problem Statement
Validating a .gitlab-ci.yml configuration file is tedious and error-prone, especially when dealing with complex CI/CD pipelines. The goal of this project is to create a tool that can automatically validate the configuration file, ensuring that it adheres to the required syntax and best practices, and providing meaningful error messages when issues are detected. This saves time, reduces human error, and improves the overall reliability of the CI/CD process.

## Objectives
- In docker, run a local instance of each job defined in the .gitlab-ci.yml file and validate its configuration.
- The java application should take any property defined in the application.properties file that starts with a specific prefix `cicd.yamlVariable.` and use them as environment yamlVariables when running the local instances of the CI/CD jobs.
- The tool should provide clear and actionable error messages when validation fails, helping users quickly identify and fix issues in their .gitlab-ci.yml configuration.
- The tool should be extensible, allowing for the addition of new validation rules and checks as CI/CD best practices evolve.
- The tool should run docker containers locally to simulate the execution of CI/CD jobs, ensuring that the configuration works as expected in a real environment.
- The tool should run docker containers in order, respecting the dependencies and execution order defined in the .gitlab-ci.yml file.
- The tool should pass around job artifacts and environment yamlVariables between dependent jobs, ensuring that the CI/CD pipeline behaves as expected.
- The tool should provide a mechanism to clean up docker containers and resources after the validation process to avoid resource leaks and maintain a clean environment.

## Motivation
Getting tired of pushing a CI/CD pipeline to a remote repository just to test it, validating it is a tedious and error-prone process, back-and-forth between local changes and remote testing. During Friday, I had an idea to create a local validator for CI/CD pipelines but didn't know if it would actually work but with vibe coding, early experiments showed promise. I am surprised how much I was able to get done in a short time.

This is not a replacement for a CI/CD pipeline, and should be used solely for local testing and validation purposes like you would use with aws Localstack or other local testing frameworks.

### Features to be added
- Give a list of programs that only be only simulated locally without actually running them, like aws-cli.
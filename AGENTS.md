# STT ( Audio ( or Audio+Video ) to Text, Wiki And Knowledge Base Generator App

This is a spring boot 4, Java 21, reactive web flux based web application.

The project includes another similarly architected application as a git submodule in the whisper-wrapper directory.

## Code Standards

- Immutability by default. 
- All lists and collections should be declared as guava immutable variants.
- All variables should be declared as final
- All reactive stream subscriptions should handle exceptions or dropped events before termination.
- All exceptions that are not being re-thrown should be logged even if a fallback is inplace. If a fallback is in place then the log level can be warning.
- Use lombok wherever possible.
- All DTOs or other non Entity, simple state bearing objects, should use lombok immutable @Value and @Builder annotations. 
- All persistent r2dbc Entity models should use @Data.
- Note that R2dbc does not save new entities if they have a pre-allocated id. Therefore, BeforeConvertCallback beans should be registered for all entities to pre-allocate ids.
- All entities need corresponding database table definitions in liquibase. 
- All liquibase changesets need rollbacks. 
- At this time the project is in rapid iteration and development mode. Therefore, all liquibase changes to already defined tables or other objects, should be made to the existing defintiion rather than patched with a followup change set.
- The whisper-wrapper folder is fo a git sub module that should never be mutated directly within this project. It is used for starting the application or as a readonly reference for the integration API only.
- Ensure all tests integrating with Ollama leverage the OllamaContainer defined in OllamaTestContainersDefaultConfig to ensure models are not re-downloaded during testing.
- Use spring boot 4 best practices.
- Avoid loading large files in memory all at once.
- Ensure all code branches are covered with unit tests and that all tests pass.
- As much as possible seek to minimize cyclomatic complexity.
- Avoid code duplication, look for ways to re-use code that does conceptually the same task.
- Avoid "conditional relevance" in model declared fields or in method args. This is where one field or arg may not be relevant unless another field or arg has a specific value. To resolve this, favor polymorphic encapsulations of the related fields instead.
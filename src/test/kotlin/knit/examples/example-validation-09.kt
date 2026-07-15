// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation09

context(_: Raise<InvalidField>)
private fun List<String>.validTags(): Set<String> =
    withError(::InvalidTag) { mapOrAccumulate { it.trim().notBlank() }.toSet() }

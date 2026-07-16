// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation03

context(_: Accumulate<String>)
fun String.ensureNotBlank() {
    ensureOrAccumulate(isNotBlank()) { "Password cannot be blank" }
}

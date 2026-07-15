// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation05

context(_: Raise<InvalidField>)
private fun String.emailValidation(): String =
    withError(::InvalidEmail) { trim().emailRules() }

context(_: Raise<InvalidField>)
private fun String.usernameValidation(): String =
    withError(::InvalidUsername) { trim().usernameRules() }

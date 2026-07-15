// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation01

import arrow.core.NonEmptyList

data class InvalidPassword(val errors: NonEmptyList<String>)

@JvmInline
value class Password(private val value: String) {
    fun raw(): String = value
    override fun toString(): String = "Password(*****)"
}

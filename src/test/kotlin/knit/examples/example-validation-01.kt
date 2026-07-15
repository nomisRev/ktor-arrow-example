// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation01

import arrow.core.NonEmptyList

sealed interface InvalidField {
    val errors: NonEmptyList<String>
    val field: String
}

data class InvalidPassword(override val errors: NonEmptyList<String>) : InvalidField {
    constructor(error: String) : this(nonEmptyListOf(error))

    override val field: String = "password"
}

@JvmInline
value class Password private constructor(private val value: String) {
    fun raw(): String = value
    override fun toString(): String = "Password(*****)"
}

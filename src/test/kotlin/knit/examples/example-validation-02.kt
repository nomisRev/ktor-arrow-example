// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation02

import arrow.core.NonEmptyList
import arrow.core.raise.context.Raise

data class InvalidPassword(val errors: NonEmptyList<String>)

@JvmInline
value class Password private constructor(private val value: String) {
    fun raw(): String = value
    override fun toString(): String = "Password(*****)"

    companion object {
        context(_: Raise<InvalidPassword>)
        fun create(value: String): Password = TODO()

        context(_: Raise<InvalidPassword>)
        operator fun invoke(value: String): Password = create(value)
    }
}

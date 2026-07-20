package io.github.nomisrev.users

import arrow.core.raise.context.Raise
import io.github.nomisrev.InvalidField
import io.github.nomisrev.maxSize
import io.github.nomisrev.minSize
import io.github.nomisrev.notBlank
import io.github.nomisrev.validate

private const val MIN_USERNAME_LENGTH = 1
private const val MAX_USERNAME_LENGTH = 25

@JvmInline
value class Username private constructor(val value: String) {
    companion object {
        context(_: Raise<InvalidField>)
        operator fun invoke(value: String): Username = validate("username") {
            val normalized = value.trim()
            Username(
                normalized.notBlank()
                    .minSize(MIN_USERNAME_LENGTH)
                    .maxSize(MAX_USERNAME_LENGTH),
            )
        }
    }
}

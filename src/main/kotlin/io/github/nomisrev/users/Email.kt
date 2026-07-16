package io.github.nomisrev.users

import arrow.core.raise.context.Raise
import io.github.nomisrev.InvalidField
import io.github.nomisrev.validate
import io.github.nomisrev.ensureOrAccumulate
import io.github.nomisrev.maxSize
import io.github.nomisrev.notBlank

private const val MAX_EMAIL_LENGTH = 350
private val pattern = ".+@.+\\..+".toRegex()

@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        context(_: Raise<InvalidField>)
        operator fun invoke(value: String): Email = validate("email") {
            val normalized = value.trim()
            ensureOrAccumulate(pattern.matches(normalized)) { "'$normalized' is invalid email" }
            Email(normalized.notBlank().maxSize(MAX_EMAIL_LENGTH))
        }
    }
}

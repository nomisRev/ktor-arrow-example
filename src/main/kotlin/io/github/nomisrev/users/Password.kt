package io.github.nomisrev.users

import arrow.core.raise.context.Raise
import io.github.nomisrev.InvalidField
import io.github.nomisrev.ensureOrAccumulate
import io.github.nomisrev.maxSize
import io.github.nomisrev.minSize
import io.github.nomisrev.notBlank
import io.github.nomisrev.validate

private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_PASSWORD_LENGTH = 100
private val uppercase = "[A-Z]".toRegex()
private val lowercase = "[a-z]".toRegex()
private val number = "[0-9]".toRegex()
private val special = """[$#%&^*!?{}\[\]+=<€>±§|]""".toRegex()

@JvmInline
value class Password private constructor(private val value: String) {
    fun raw(): String = value
    override fun toString(): String = "Password(*****)"

    companion object {
        context(_: Raise<InvalidField>)
        operator fun invoke(value: String): Password = validate("password") {
            ensureOrAccumulate(value.contains(uppercase)) { "At least one uppercase letter" }
            ensureOrAccumulate(value.contains(lowercase)) { "At least one lowercase letter" }
            ensureOrAccumulate(value.contains(number)) { "At least one number" }
            ensureOrAccumulate(value.contains(special)) { "At least one special character" }
            Password(value.notBlank().minSize(MIN_PASSWORD_LENGTH).maxSize(MAX_PASSWORD_LENGTH))
        }
    }
}

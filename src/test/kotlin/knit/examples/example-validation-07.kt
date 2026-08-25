// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation07

import arrow.core.raise.context.Raise
import arrow.core.raise.context.accumulate
import arrow.core.raise.context.accumulating
import arrow.core.raise.context.withError

context(_: Raise<IncorrectInput>)
fun UpdateUser.toUpdate(userId: UserId): Update = withError(::IncorrectInput) {
    accumulate {
        val username by accumulating { username?.let(::Username) }
        val email by accumulating { email?.let(::Email) }
        val password by accumulating { password?.let(::Password) }
        Update(userId, username, email, password, bio, image)
    }
}

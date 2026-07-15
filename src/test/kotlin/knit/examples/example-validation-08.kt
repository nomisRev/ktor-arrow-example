// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation08

context(_: Raise<IncorrectInput>)
fun Update.validate(): Update =
    withError(::IncorrectInput) {
        accumulate {
            val username by accumulating { username?.validUsername() }
            val email by accumulating { email?.validEmail() }
            val password by accumulating { password?.validPassword() }
            Update(userId, username, email, password, bio, image)
        }
    }

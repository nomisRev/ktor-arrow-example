// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation07

// Short-circuits at the first failure.
context(_: Raise<IncorrectInput>)
fun RegisterUser.validateNaive(): RegisterUser {
    val username = username.validUsername()
    val email = email.validEmail()
    val password = password.validPassword()
    return RegisterUser(username, email, password)
}

// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation12

context(_: DomainErrors)
fun register(input: RegisterUser): JwtToken {
    val (username, email, password) = input.validate()
    val userId = repo.insert(username, email, password)
    return jwtService.generateJwtToken(userId)
}

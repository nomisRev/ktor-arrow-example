package io.github.nomisrev.service

import arrow.core.Either
import io.github.nomisrev.DomainError
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.routes.Profile


interface ProfileService {
    /** Select a Profile by its username */
    suspend fun getProfile(username: String): Either<DomainError, Profile>
}

fun profileService(
    repo: UserPersistence,
): ProfileService = object : ProfileService {

    override suspend fun getProfile(
        username: String,
    ): Either<DomainError, Profile> = repo.selectProfile(username)
}
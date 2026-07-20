package io.github.nomisrev.users

import arrow.core.raise.catch
import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import io.github.nomisrev.DomainErrors
import io.github.nomisrev.EmailAlreadyExists
import io.github.nomisrev.EmptyUpdate
import io.github.nomisrev.PasswordNotMatched
import io.github.nomisrev.UserError
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.UsernameAlreadyExists
import io.github.nomisrev.auth.JwtService
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.profiles.Profile
import io.github.nomisrev.sqldelight.FollowingQueries
import io.github.nomisrev.sqldelight.UsersQueries
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

@JvmInline
value class UserId(val serial: Long)

data class RegisterUser(val username: Username, val email: Email, val password: Password)

data class Update(
    val userId: UserId,
    val username: Username?,
    val email: Email?,
    val password: Password?,
    val bio: String?,
    val image: String?,
) {
    fun isNotEmpty() = username != null ||
        email != null ||
        password != null ||
        bio != null ||
        image != null
}

data class UserInfo(val email: Email, val username: Username, val bio: String, val image: String)

data class TokenAndUserInfo(val token: JwtToken, val info: UserInfo)

data class Login(val email: Email, val password: Password)

class UserService(
    private val usersQueries: UsersQueries,
    private val followingQueries: FollowingQueries,
    private val jwtService: JwtService,
    private val defaultIterations: Int = 64000,
    private val defaultKeyLength: Int = 512,
    private val secretKeysFactory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
) {
    context(_: DomainErrors)
    fun register(input: RegisterUser): JwtToken {
        val salt = generateSalt()
        val key = generateKey(input.password.raw(), salt)
        val userId = catch({
                usersQueries.insertAndGetId(
                    username = input.username,
                    email = input.email,
                    salt = salt,
                    hashed_password = key,
                    bio = "",
                    image = "",
                ).executeAsOne()
            }) { exception: PSQLException ->
            raiseUniqueViolation(exception, input.username, input.email)
        }
        return jwtService.generateJwtToken(userId)
    }

    context(_: DomainErrors)
    fun update(input: Update): UserInfo {
        ensure(input.isNotEmpty()) {
            EmptyUpdate("Cannot update user with ${input.userId} with only null values")
        }

        val passwordUpdate = input.password?.let { password ->
            val salt = generateSalt()
            salt to generateKey(password.raw(), salt)
        }

        val info = catch({
                usersQueries.update(
                    email = input.email?.value,
                    username = input.username?.value,
                    salt = passwordUpdate?.first,
                    hashed_password = passwordUpdate?.second,
                    bio = input.bio,
                    image = input.image,
                    userId = input.userId,
                    ::UserInfo,
                ).executeAsOneOrNull()
            }) { exception: PSQLException ->
            raiseUniqueViolation(exception, input.username, input.email)
        }

        return ensureNotNull(info) { UserNotFound("userId=${input.userId}") }
    }

    context(_: DomainErrors)
    fun login(input: Login): TokenAndUserInfo {
        val (id, username, salt, hashed_password, bio, image) = ensureNotNull(
            usersQueries.selectSecurityByEmail(input.email).executeAsOneOrNull(),
        ) { UserNotFound("email=${input.email}") }

        val hash = generateKey(input.password.raw(), salt)
        ensure(hash contentEquals hashed_password) { PasswordNotMatched }
        val token = jwtService.generateJwtToken(id)
        return TokenAndUserInfo(token, UserInfo(input.email, username, bio, image))
    }

    context(_: Raise<UserNotFound>)
    fun getUser(userId: UserId): UserInfo {
        val userInfo = usersQueries.selectById(userId) { email, username, _, _, bio, image ->
            UserInfo(email, username, bio, image)
        }.executeAsOneOrNull()
        return ensureNotNull(userInfo) { UserNotFound("userId=$userId") }
    }

    context(_: Raise<UserNotFound>)
    fun select(username: Username): UserInfo {
        val userInfo = usersQueries.selectByUsername(username, ::UserInfo).executeAsOneOrNull()
        return ensureNotNull(userInfo) { UserNotFound("username=$username") }
    }

    context(_: Raise<UserNotFound>)
    fun selectProfile(username: Username, viewerId: UserId? = null): Profile {
        val profileInfo = when (viewerId) {
            null -> usersQueries.selectProfile(username, ::toProfile).executeAsOneOrNull()
            else -> usersQueries
                .selectProfileByViewer(viewerId.serial, username, ::toProfile)
                .executeAsOneOrNull()
        }
        return ensureNotNull(profileInfo) { UserNotFound("username=$username") }
    }

    fun selectAuthorProfiles(
        viewerId: UserId?,
        authorIds: Collection<UserId>,
    ): Map<UserId, Profile> = if (authorIds.isEmpty()) emptyMap()
    else usersQueries.selectProfilesByViewer(
        viewerId?.serial ?: NO_USER,
        authorIds.distinct(),
    ) { id, username, bio, image, following ->
        id to Profile(username.value, bio, image, following > 0)
    }
        .executeAsList()
        .toMap()

    suspend fun unfollowProfile(followedUsername: Username, followerId: UserId) {
        followingQueries.delete(followedUsername, followerId.serial).await()
    }

    context(_: Raise<UserNotFound>)
    suspend fun followProfile(
        followedUsername: Username,
        followerId: UserId,
    ): Long = catch({
        followingQueries.insertByUsername(followedUsername, followerId.serial).await()
    }) { exception: PSQLException ->
        if (exception.sqlState == PSQLState.NOT_NULL_VIOLATION.state) {
            raise(UserNotFound("username=$followedUsername"))
        } else {
            throw exception
        }
    }

    context(_: Raise<UserError>)
    private fun raiseUniqueViolation(
        exception: PSQLException,
        username: Username?,
        email: Email?,
    ): Nothing = when (exception.serverErrorMessage?.constraint) {
        "users_username_key" -> raise(UsernameAlreadyExists(username!!))
        "users_email_key" -> raise(EmailAlreadyExists(email!!))
        else -> throw exception
    }

    private fun toProfile(username: Username, bio: String, image: String, following: Int): Profile =
        Profile(username.value, bio, image, following > 0)

    private fun generateSalt(): ByteArray = UUID.randomUUID().toString().toByteArray()

    private fun generateKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, defaultIterations, defaultKeyLength)
        return secretKeysFactory.generateSecret(spec).encoded
    }

    private companion object {
        /** Sentinel id that never matches a real user, used to represent "no current user". */
        const val NO_USER = -1L
    }
}

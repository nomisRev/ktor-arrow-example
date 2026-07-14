package io.github.nomisrev.users

import arrow.core.raise.catch
import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import io.github.nomisrev.Email
import io.github.nomisrev.EmailAlreadyExists
import io.github.nomisrev.Password
import io.github.nomisrev.PasswordNotMatched
import io.github.nomisrev.UserError
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.Username
import io.github.nomisrev.UsernameAlreadyExists
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

class UserPersistence(
    private val usersQueries: UsersQueries,
    private val followingQueries: FollowingQueries,
    private val defaultIterations: Int = 64000,
    private val defaultKeyLength: Int = 512,
    private val secretKeysFactory: SecretKeyFactory =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512"),
) {
    context(_: Raise<UserError>)
    fun insert(register: RegisterUser): UserId {
        val salt = generateSalt()
        val key = generateKey(register.password.raw(), salt)
        return catch({
            usersQueries
                .insertAndGetId(
                    username = register.username.value,
                    email = register.email.value,
                    salt = salt,
                    hashed_password = key,
                    bio = "",
                    image = "",
                )
                .executeAsOne()
        }) { e: PSQLException ->
            raiseUniqueViolation(e, register.username, register.email)
        }
    }

    context(_: Raise<UserError>)
    fun verifyPassword(email: Email, password: Password): UserIdAndInfo {
        val (id, username, salt, hashed_password, bio, image) =
            ensureNotNull(usersQueries.selectSecurityByEmail(email.value).executeAsOneOrNull()) {
                UserNotFound("email=$email")
            }

        val hash = generateKey(password.raw(), salt)
        ensure(hash contentEquals hashed_password) { PasswordNotMatched }
        return UserIdAndInfo(id, UserInfo(email.value, username, bio, image))
    }

    context(_: Raise<UserNotFound>)
    fun select(userId: UserId): UserInfo {
        val userInfo =
            usersQueries
                .selectById(userId) { email, username, _, _, bio, image ->
                    UserInfo(email, username, bio, image)
                }
                .executeAsOneOrNull()
        return ensureNotNull(userInfo) { UserNotFound("userId=$userId") }
    }

    context(_: Raise<UserNotFound>)
    fun select(username: String): UserInfo {
        val userInfo = usersQueries.selectByUsername(username, ::UserInfo).executeAsOneOrNull()
        return ensureNotNull(userInfo) { UserNotFound("username=$username") }
    }

    context(_: Raise<UserNotFound>)
    fun selectProfile(username: String, viewerId: UserId? = null): Profile {
        val profileInfo =
            when (viewerId) {
                null -> usersQueries.selectProfile(username, ::toProfile).executeAsOneOrNull()
                else ->
                    usersQueries
                        .selectProfileByViewer(viewerId.serial, username, ::toProfile)
                        .executeAsOneOrNull()
            }
        return ensureNotNull(profileInfo) { UserNotFound("username=$username") }
    }

    fun selectAuthorProfiles(
        viewerId: UserId?,
        authorIds: Collection<UserId>,
    ): Map<UserId, Profile> =
        if (authorIds.isEmpty()) emptyMap()
        else
            usersQueries
                .selectProfilesByViewer(viewerId?.serial ?: NO_USER, authorIds.distinct()) { id,
                                                                                             username,
                                                                                             bio,
                                                                                             image,
                                                                                             following ->
                    id to Profile(username, bio, image, following > 0)
                }
                .executeAsList()
                .toMap()

    private fun toProfile(username: String, bio: String, image: String, following: Int): Profile =
        Profile(username, bio, image, following > 0)

    @Suppress("LongParameterList")
    context(_: Raise<UserError>)
    fun update(update: Update): UserInfo {
        val passwordUpdate = update.password?.let {
            val salt = generateSalt()
            salt to generateKey(it.raw(), salt)
        }

        val info =
            catch({
                usersQueries
                    .update(
                        email = update.email?.value,
                        username = update.username?.value,
                        salt = passwordUpdate?.first,
                        hashed_password = passwordUpdate?.second,
                        bio = update.bio,
                        image = update.image,
                        userId = update.userId,
                        ::UserInfo
                    )
                    .executeAsOneOrNull()
            }) { e: PSQLException ->
                raiseUniqueViolation(e, update.username, update.email)
            }

        return ensureNotNull(info) { UserNotFound("userId=${update.userId}") }
    }

    suspend fun unfollowProfile(followedUsername: String, followerId: UserId) {
        followingQueries.delete(followedUsername, followerId.serial).await()
    }

    context(_: Raise<UserNotFound>)
    suspend fun followProfile(
        followedUsername: String,
        followerId: UserId,
    ): Long =
        catch({
            followingQueries.insertByUsername(followedUsername, followerId.serial).await()
        }) { e: PSQLException ->
            if (e.sqlState == PSQLState.NOT_NULL_VIOLATION.state)
                raise(UserNotFound("username=$followedUsername"))
            else throw e
        }

    context(_: Raise<UserError>)
    private fun raiseUniqueViolation(
        exception: PSQLException,
        username: Username?,
        email: Email?,
    ): Nothing =
        when (exception.serverErrorMessage?.constraint) {
            "users_username_key" -> raise(UsernameAlreadyExists(username?.value.orEmpty()))
            "users_email_key" -> raise(EmailAlreadyExists(email?.value.orEmpty()))
            else -> throw exception
        }

    private fun generateSalt(): ByteArray = UUID.randomUUID().toString().toByteArray()

    private companion object {
        /** Sentinel id that never matches a real user, used to represent "no current user". */
        const val NO_USER = -1L
    }

    private fun generateKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, defaultIterations, defaultKeyLength)
        return secretKeysFactory.generateSecret(spec).encoded
    }
}

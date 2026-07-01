package io.github.nomisrev.users

import arrow.core.raise.catch
import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import io.github.nomisrev.EmailAlreadyExists
import io.github.nomisrev.PasswordNotMatched
import io.github.nomisrev.UserError
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.UsernameAlreadyExists
import io.github.nomisrev.profiles.Profile
import io.github.nomisrev.sqldelight.FollowingQueries
import io.github.nomisrev.sqldelight.UsersQueries
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

@JvmInline value class UserId(val serial: Long)

class UserPersistence(
    private val usersQueries: UsersQueries,
    private val followingQueries: FollowingQueries,
    private val defaultIterations: Int = 64000,
    private val defaultKeyLength: Int = 512,
    private val secretKeysFactory: SecretKeyFactory =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512"),
) {
    context(_: Raise<UserError>)
    fun insert(username: String, email: String, password: String): UserId {
        val salt = generateSalt()
        val key = generateKey(password, salt)
        return catch({
            usersQueries
                .insertAndGetId(
                    username = username,
                    email = email,
                    salt = salt,
                    hashed_password = key,
                    bio = "",
                    image = "",
                )
                .executeAsOne()
        }) { e: PSQLException ->
            raiseUniqueViolation(e, username, email)
        }
    }

    context(_: Raise<UserError>)
    fun verifyPassword(email: String, password: String): Pair<UserId, UserInfo> {
        val (userId, username, salt, key, bio, image) =
            ensureNotNull(usersQueries.selectSecurityByEmail(email).executeAsOneOrNull()) {
                UserNotFound("email=$email")
            }

        val hash = generateKey(password, salt)
        ensure(hash contentEquals key) { PasswordNotMatched }
        return Pair(userId, UserInfo(email, username, bio, image))
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
                .selectProfilesByViewer(viewerId?.serial ?: NO_USER, authorIds.distinct()) {
                    id,
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
    fun update(
        userId: UserId,
        email: String?,
        username: String?,
        password: String?,
        bio: String?,
        image: String?,
    ): UserInfo {
        val passwordUpdate = password?.let {
            val salt = generateSalt()
            salt to generateKey(it, salt)
        }

        val info =
            catch({
                usersQueries
                    .update(
                        email = email,
                        username = username,
                        salt = passwordUpdate?.first,
                        hashed_password = passwordUpdate?.second,
                        bio = bio,
                        image = image,
                        userId = userId,
                        mapper = ::UserInfo,
                    )
                    .executeAsOneOrNull()
            }) { e: PSQLException ->
                raiseUniqueViolation(e, username, email)
            }

        return ensureNotNull(info) { UserNotFound("userId=$userId") }
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
        username: String?,
        email: String?,
    ): Nothing =
        when (exception.serverErrorMessage?.constraint) {
            "users_username_key" -> raise(UsernameAlreadyExists(username.orEmpty()))
            "users_email_key" -> raise(EmailAlreadyExists(email.orEmpty()))
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

package io.github.nomisrev.users

import arrow.core.raise.catch
import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
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
            if (e.sqlState == PSQLState.UNIQUE_VIOLATION.state)
                raise(UsernameAlreadyExists(username))
            else throw e
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

    /** Select a User by its [UserId] */
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
    fun selectProfile(username: String): Profile {
        val profileInfo = usersQueries.selectProfile(username, ::toProfile).executeAsOneOrNull()
        return ensureNotNull(profileInfo) { UserNotFound("username=$username") }
    }

    fun isFollowing(followedId: UserId, followerId: UserId): Boolean =
        followingQueries.select(followedId.serial, followerId.serial).executeAsOneOrNull() != null

    private fun toProfile(username: String, bio: String, image: String, following: Int): Profile =
        Profile(username, bio, image, following > 0)

    @Suppress("LongParameterList")
    context(_: Raise<UserNotFound>)
    fun update(
        userId: UserId,
        email: String?,
        username: String?,
        password: String?,
        bio: String?,
        image: String?,
    ): UserInfo {
        val info = usersQueries.transactionWithResult {
            usersQueries.selectById(userId).executeAsOneOrNull()?.let {
                (oldEmail, oldUsername, salt, oldPassword, oldBio, oldImage) ->
                val newPassword = password?.let { generateKey(it, salt) } ?: oldPassword
                val newEmail = email ?: oldEmail
                val newUsername = username ?: oldUsername
                val newBio = bio ?: oldBio
                val newImage = image ?: oldImage
                usersQueries.update(
                    newEmail,
                    newUsername,
                    newPassword,
                    newBio,
                    newImage,
                    userId,
                )
                UserInfo(newEmail, newUsername, newBio, newImage)
            }
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

    private fun generateSalt(): ByteArray = UUID.randomUUID().toString().toByteArray()

    private fun generateKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, defaultIterations, defaultKeyLength)
        return secretKeysFactory.generateSecret(spec).encoded
    }
}

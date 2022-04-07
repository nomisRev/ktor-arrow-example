package io.github.nomisrev

import io.github.nomisrev.routes.Profile

@Suppress("ForbiddenComment")
// TODO: Make sub-split between UserService & ArticleService
//       sealed interface UserError : ApiError
//       data class UserNotFound(val property: String) : UserError
//       An error can even implement both:
//          data class Unexpected(val error: Throwable) : UserError, ArticleError
sealed interface ApiError {
  object PasswordNotMatched : ApiError
  data class EmptyUpdate(val description: String) : ApiError
  data class UserNotFound(val property: String) : ApiError
  data class UserFollowingHimself(val profile: Profile) : ApiError
  data class UserUnfollowingHimself(val profile: Profile) : ApiError
  data class EmailAlreadyExists(val email: String) : ApiError
  data class UsernameAlreadyExists(val username: String) : ApiError
  data class ProfileNotFound(val profile: Profile) : ApiError
  data class ArticleNotFound(val slug: String) : ApiError
  data class CommentNotFound(val commentId: Long) : ApiError
  data class JwtGeneration(val description: String) : ApiError
  data class JwtInvalid(val description: String) : ApiError
  data class CannotGenerateSlug(val description: String) : ApiError
  data class Unexpected(val description: String, val error: Throwable) : ApiError
}

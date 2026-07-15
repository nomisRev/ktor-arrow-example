// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation11

context(_: Raise<IncorrectInput>)
fun FeedParameters.validate(userId: UserId): GetFeed =
    withError(::IncorrectInput) {
        accumulate {
            val offset by accumulating { offset.validFeedOffset() }
            val limit by accumulating { limit.validFeedLimit() }
            GetFeed(userId, limit.limit, offset.offset)
        }
    }

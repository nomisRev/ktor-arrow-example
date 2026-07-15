package io.github.nomisrev.tags

import io.github.nomisrev.Api
import io.github.nomisrev.GenericErrorModel
import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.StaticResource

object Tags : StaticResource<Api>("tags", Api) {
    val list by get()
        .response<TagsResponse>()
        .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)
}

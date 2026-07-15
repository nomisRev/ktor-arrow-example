package io.github.nomisrev.profiles

import io.github.nomisrev.Api
import io.github.nomisrev.GenericErrorModel
import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.DynamicResource
import opensavvy.spine.api.StaticResource

object Profiles : StaticResource<Api>("profiles", Api) {
    object Username : DynamicResource<Profiles>("username", Profiles) {
        val get by
            get()
                .response<ProfileWrapper<Profile>>()
                .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

        object Follow : StaticResource<Username>("follow", Username) {
            val add by
                post()
                    .response<ProfileWrapper<Profile>>()
                    .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

            val remove by
                delete()
                    .response<ProfileWrapper<Profile>>()
                    .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)
        }
    }
}

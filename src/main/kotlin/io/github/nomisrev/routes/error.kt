package io.github.nomisrev.routes

import kotlinx.serialization.Serializable

sealed interface ApiError

@Serializable data class GenericErrorModel(val errors: GenericErrorModelErrors) : ApiError

@Serializable data class GenericErrorModelErrors(val body: List<String>)

object Unauthorized : ApiError

fun GenericErrorModel(vararg msg: String): GenericErrorModel =
  GenericErrorModel(GenericErrorModelErrors(msg.toList()))

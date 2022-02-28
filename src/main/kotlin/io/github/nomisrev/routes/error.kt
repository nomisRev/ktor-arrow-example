package io.github.nomisrev.routes

import kotlinx.serialization.Serializable

@Serializable data class GenericErrorModel(val errors: GenericErrorModelErrors)

@Serializable data class GenericErrorModelErrors(val body: List<String>)

fun GenericErrorModel(vararg msg: String): GenericErrorModel =
  GenericErrorModel(GenericErrorModelErrors(msg.toList()))

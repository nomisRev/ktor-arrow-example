package io.github.nomisrev.service

import io.github.nomisrev.repo.TagPersistence

interface TagService {
  /** Retrieve tags */
  suspend fun selectTags(): List<String>
}

fun tagService(repo: TagPersistence) =
  object : TagService {
    override suspend fun selectTags() = repo.selectTags()
  }

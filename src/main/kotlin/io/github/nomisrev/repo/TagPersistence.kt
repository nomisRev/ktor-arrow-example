package io.github.nomisrev.repo

import io.github.nomisrev.sqldelight.TagsQueries

interface TagPersistence {
  /** List all tags * */
  suspend fun selectTags(): List<String>
}

fun tagPersistence(tags: TagsQueries) =
  object : TagPersistence {
    override suspend fun selectTags() = tags.selectTags().executeAsList()
  }

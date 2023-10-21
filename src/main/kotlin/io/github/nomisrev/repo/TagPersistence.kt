package io.github.nomisrev.repo

import io.github.nomisrev.sqldelight.TagsQueries

interface TagPersistence {
  /** List all tags * */
  suspend fun selectTags(): List<String>

  suspend fun selectTagsOfArticle(articleId: ArticleId): List<String>
}

fun tagPersistence(tags: TagsQueries) =
  object : TagPersistence {
    override suspend fun selectTags() = tags.selectTags().executeAsList()

    override suspend fun selectTagsOfArticle(articleId: ArticleId): List<String> {
      return tags.selectTagsOfArticle(articleId).executeAsList()
    }
  }

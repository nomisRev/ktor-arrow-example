package io.github.nomisrev.tags

import io.github.nomisrev.articles.ArticleId
import io.github.nomisrev.sqldelight.TagsQueries

class TagPersistence(
    private val tags: TagsQueries,
) {
    fun selectTags(): List<String> = tags.selectTags().executeAsList()

    fun selectTagsOfArticle(articleId: ArticleId): List<String> =
        tags.selectTagsOfArticle(articleId).executeAsList()
}

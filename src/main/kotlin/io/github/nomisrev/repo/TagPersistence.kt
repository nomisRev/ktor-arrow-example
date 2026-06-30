package io.github.nomisrev.repo

import io.github.nomisrev.sqldelight.TagsQueries

class TagPersistence(
    private val tags: TagsQueries,
) {
    /** List all tags * */
    suspend fun selectTags(): List<String> = tags.selectTags().executeAsList()

    /** List tags of an article * */
    suspend fun selectTagsOfArticle(articleId: ArticleId): List<String> =
        tags.selectTagsOfArticle(articleId).executeAsList()
}

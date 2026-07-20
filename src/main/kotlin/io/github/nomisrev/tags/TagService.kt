package io.github.nomisrev.tags

import io.github.nomisrev.articles.ArticleId
import io.github.nomisrev.sqldelight.TagsQueries

class TagService(private val tags: TagsQueries) {
    fun selectTags(): List<String> = tags.selectTags().executeAsList()

    fun selectTagsOfArticles(articleIds: Collection<ArticleId>): Map<ArticleId, List<String>> = if (
            articleIds.isEmpty()
        ) emptyMap()
        else tags.selectTagsOfArticles(articleIds.distinct()) { articleId, tag -> articleId to tag }
            .executeAsList()
            .groupBy({ it.first }, { it.second })
}

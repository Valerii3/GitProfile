package org.jetbrains.git.profile.git_profile.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

internal class GitRepoStats {
    private val client = OkHttpClient()
    private val baseUrl = "https://api.github.com"

    @Throws(IOException::class)
    fun getRepoName(owner: String, repo: String, token: String): String? =
        makeRequest("$baseUrl/repos/$owner/$repo", token)?.get("name")?.asString

    @Throws(IOException::class)
    suspend fun getCommitCount(owner: String, repo: String, token: String): Int {
        val sha = getLastCommitSha(owner, repo, token)
        val firstCommit = getFirstCommit(owner, repo, token)
        val url = "$baseUrl/repos/$owner/$repo/compare/$firstCommit...$sha"
        val jsonObject = makeRequest(url, token)
        return jsonObject?.get("total_commits")?.asInt?.plus(1) ?: 0
    }

    private suspend  fun getFirstCommit(owner: String, repo: String, token: String): String =
        extractCommitSha(makePaginatedRequest("$baseUrl/repos/$owner/$repo/commits", token, "last"))


    @Throws(IOException::class)
    private fun getLastCommitSha(owner: String, repo: String, token: String): String {
        makeRequestArray("$baseUrl/repos/$owner/$repo/commits", token).let {
            return extractCommitSha(it, "first")
        }

    }

    private fun extractCommitSha(jsonArray: JsonArray, position: String = "first"): String =
        jsonArray.let { if (position == "last") it.last() else it.first() }.asJsonObject["sha"].asString

    @Throws(IOException::class)
    fun getBranchCount(owner: String, repo: String, token: String): Int {
        return makeRequestArray("$baseUrl/repos/$owner/$repo/branches", token).size()
    }

    @Throws(IOException::class)
    fun getRepoDescription(owner: String, repo: String, token: String): String? =
        makeRequest("$baseUrl/repos/$owner/$repo", token)?.get("description")?.asString

    @Throws(IOException::class)
    fun getContributors(owner: String, repo: String, token: String): JsonArray =
        makeRequestArray("$baseUrl/repos/$owner/$repo/contributors", token)


    @Throws(IOException::class)
    fun getLanguageUsage(owner: String, repo: String, token: String): Map<String, Double>? {
        val jsonObject = makeRequest("$baseUrl/repos/$owner/$repo/languages", token)
        val totalBytes = jsonObject?.entrySet()?.sumOf { it.value.asLong }
        return jsonObject?.entrySet()?.associate { it.key to (it.value.asDouble / totalBytes!! * 100) }
    }

    @Throws(IOException::class)
    suspend fun getAllCommitsByContributor(owner: String, repo: String, contributor: String, token: String): List<JsonObject> {
        val sha = getLastCommitSha(owner, repo, token)
        val allCommits = mutableListOf<JsonObject>()
        var page = 1
        var hasMore = true

        while (hasMore) {
            val url = "$baseUrl/repos/$owner/$repo/commits?sha=$sha&author=$contributor&per_page=100&page=$page"
            val jsonArray = makeRequestArray(url, token)
            if (jsonArray.size() == 0) {
                hasMore = false
            } else {
                jsonArray.forEach { allCommits.add(it.asJsonObject) }
                page++
            }
        }
        return allCommits
    }

    private fun makeRequest(url: String, token: String): JsonObject? {
        val request = Request.Builder().url(url).addHeader("Authorization", "token $token").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            return JsonParser.parseString(response.body?.string())?.asJsonObject
        }
    }

    private fun makeRequestArray(url: String, token: String): JsonArray {
        val request = Request.Builder().url(url).addHeader("Authorization", "token $token").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            return JsonParser.parseString(response.body?.string()).asJsonArray
        }
    }

    private suspend fun makePaginatedRequest(url: String, token: String, navigation: String): JsonArray {
        val request = Request.Builder().url(url).addHeader("Authorization", "token $token").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val jsonArray = JsonParser.parseString(response.body?.string()).asJsonArray
            return if (navigation == "last" && response.headers["Link"]?.contains("rel=\"last\"") == true) {
                val lastPageUrl = response.headers["Link"]!!.split(",")[1].split(";")[0].split("<")[1].split(">")[0]
                makeRequestArray(lastPageUrl, token).asJsonArray
            } else jsonArray
        }
    }
}

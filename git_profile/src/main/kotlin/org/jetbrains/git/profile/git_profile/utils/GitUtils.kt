package org.jetbrains.git.profile.git_profile.utils

import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

object GitUtils {
    fun getRemoteUrl(projectDir: File): String? {
        val repository: Repository = FileRepositoryBuilder()
            .setGitDir(File(projectDir, ".git"))
            .readEnvironment()
            .findGitDir()
            .build()
        return repository.config.getString("remote", "origin", "url")
    }

    fun parseGitHubRepo(remoteUrl: String): Pair<String, String>? {
        val regex = Regex("github.com[:/](.*?)/(.*?)(\\.git)?$")
        val matchResult = regex.find(remoteUrl) ?: return null
        val (owner, repo) = matchResult.destructured
        return Pair(owner, repo)
    }
}
/**
 * @file RequestBlocker.kt
 * @description Network-level blocking of Twitter/X write operations.
 *              This is the robust safety layer — even if CSS/JS hiding fails,
 *              mutation requests are blocked here.
 */
package com.example.xreadonly

import android.util.Log
import android.webkit.WebResourceRequest

object RequestBlocker {

    private const val TAG = "XReadOnly.Blocker"

    /**
     * GraphQL mutation operation names that must be blocked.
     * Twitter's GraphQL URLs follow the pattern: /graphql/<hash>/OperationName
     */
    private val BLOCKED_GRAPHQL_OPERATIONS = setOf(
        // Tweets
        "CreateTweet",
        "DeleteTweet",
        "CreateRetweet",
        "DeleteRetweet",
        "CreateScheduledTweet",
        "DeleteScheduledTweet",
        // Likes
        "FavoriteTweet",
        "UnfavoriteTweet",
        // Bookmarks
        "CreateBookmark",
        "DeleteBookmark",
        // DMs
        "CreateDM",
        "DeleteDMConversation",
        // Follow / Unfollow
        "Follow",
        "Unfollow",
        "CreateFriendship",
        "DestroyFriendship",
        // Mute / Block
        "MuteUser",
        "UnmuteUser",
        "BlockUser",
        "UnblockUser",
        // Lists
        "CreateList",
        "DeleteList",
        "EditList",
        "ListAddMember",
        "ListRemoveMember",
        // Pin / Poll / Report
        "PinTweet",
        "UnpinTweet",
        "CreateCardVote",
        "ModerateTweet",
        "ReportTweet",
        // Communities
        "CreateCommunity",
        "JoinCommunity",
        "LeaveCommunity",
        // Profile
        "UpdateProfile",
        "UpdateProfileImage",
        "UpdateProfileBanner"
    )

    /**
     * REST API write endpoint paths that must be blocked.
     * These are under /api/1.1/ or /1.1/ on Twitter's API.
     */
    private val BLOCKED_REST_PATHS = listOf(
        // Tweets
        "/statuses/update",
        "/statuses/destroy",
        "/statuses/retweet",
        "/statuses/unretweet",
        // Likes
        "/favorites/create",
        "/favorites/destroy",
        // Follow / Unfollow
        "/friendships/create",
        "/friendships/destroy",
        // Block / Mute
        "/blocks/create",
        "/blocks/destroy",
        "/mutes/users/create",
        "/mutes/users/destroy",
        // DMs
        "/direct_messages/events/new",
        "/direct_messages/destroy",
        // Lists
        "/lists/create",
        "/lists/destroy",
        "/lists/update",
        "/lists/members/create",
        "/lists/members/destroy",
        // Profile
        "/account/update_profile",
        "/account/update_profile_image",
        "/account/update_profile_banner",
        // Report
        "/users/report_spam"
    )

    /**
     * Checks whether a request should be blocked.
     * @param request The intercepted WebResourceRequest
     * @return true if the request is a write operation that should be blocked
     */
    fun shouldBlock(request: WebResourceRequest): Boolean {
        val url = request.url?.toString() ?: return false
        val method = request.method?.uppercase() ?: "GET"

        // Only block POST requests (mutations/writes)
        if (method != "POST") return false

        // Check GraphQL mutations
        if (url.contains("/graphql/")) {
            for (operation in BLOCKED_GRAPHQL_OPERATIONS) {
                if (url.contains("/$operation")) {
                    if (BuildConfig.DEBUG) {
                        Log.i(TAG, "BLOCKED GraphQL mutation: $operation")
                    }
                    return true
                }
            }
        }

        // Check REST API write endpoints
        if (url.contains("/api/1.1/") || url.contains("/1.1/")) {
            for (path in BLOCKED_REST_PATHS) {
                if (url.contains(path)) {
                    if (BuildConfig.DEBUG) {
                        Log.i(TAG, "BLOCKED REST endpoint: $path")
                    }
                    return true
                }
            }
        }

        return false
    }
}

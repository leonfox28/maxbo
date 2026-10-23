package io.github.leonfox28.maxbo.data.api

import io.github.leonfox28.maxbo.core.net.SessionExpiredException
import io.github.leonfox28.maxbo.core.net.WeiboApiException
import io.github.leonfox28.maxbo.core.net.WeiboCookieJar
import io.github.leonfox28.maxbo.data.model.Account
import io.github.leonfox28.maxbo.data.model.CommentOrder
import io.github.leonfox28.maxbo.data.model.NoticeKind
import io.github.leonfox28.maxbo.data.model.StatusVisibility
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.commentJson
import io.github.leonfox28.maxbo.support.commentNoticeJson
import io.github.leonfox28.maxbo.support.commentsPageJson
import io.github.leonfox28.maxbo.support.form
import io.github.leonfox28.maxbo.support.homePage
import io.github.leonfox28.maxbo.support.hotItemJson
import io.github.leonfox28.maxbo.support.hotSearchJson
import io.github.leonfox28.maxbo.support.html
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.likeNoticeJson
import io.github.leonfox28.maxbo.support.noticePageJson
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.support.plus
import io.github.leonfox28.maxbo.support.profileDataJson
import io.github.leonfox28.maxbo.support.redirect
import io.github.leonfox28.maxbo.support.repostsPageJson
import io.github.leonfox28.maxbo.support.searchJson
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.topicHeadsJson
import io.github.leonfox28.maxbo.support.userJson
import io.github.leonfox28.maxbo.support.userStatusesJson
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeiboApiTest {
    private val web = FakeWeibo()

    @After fun tearDown() = web.close()

    private suspend inline fun <reified T : Throwable> failure(block: () -> Unit): T {
        val e = runCatching { block() }.exceptionOrNull()
        assertTrue("got $e", e is T)
        return e as T
    }

    @Test
    fun `getJson sends its query, leaving out what is null`() = runTest {
        web.respond = { json("""{"ok":1}""") }

        web.api().getJson("https://weibo.com/ajax/x", mapOf("a" to 1, "b" to null, "c" to "中"))

        val url = web.requests.single().url
        assertEquals("/ajax/x", url.encodedPath)
        assertEquals(setOf("a", "c"), url.queryParameterNames)
        assertEquals("中", url.queryParameter("c"))
    }

    @Test
    fun `postForm posts forms with the XSRF token, as weibo_com does`() = runTest {
        web.respond = { json("""{"ok":1,"attitude":"heart"}""") }
        val api = web.api(mapOf("weibo.com" to mapOf("SUB" to "x", "XSRF-TOKEN" to "tok")))

        api.postForm(
            "https://weibo.com/ajax/statuses/setLike",
            mapOf("id" to "42", "empty" to null),
        )

        val request = web.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/ajax/statuses/setLike", request.url.encodedPath)
        assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])
        assertEquals("tok", request.headers["X-XSRF-TOKEN"])
        assertEquals("https://weibo.com", request.headers["Origin"])
        assertEquals(mapOf("id" to "42"), request.form)
    }

    @Test
    fun `an answer that says ok but has an error failed`() = runTest {
        web.respond = { json("""{"ok":1,"error":"评论太频繁"}""") }

        val e =
            failure<WeiboApiException> {
                web.api().postForm("https://weibo.com/ajax/comments/create", emptyMap())
            }

        assertEquals("评论太频繁", e.message)
    }

    @Test
    fun `fetchCurrentAccount reads the user weibo_com's page embeds`() = runTest {
        web.respond = { homePage(uid = 42) }

        assertEquals(Account("42", "测试用户"), web.api().fetchCurrentAccount())
        assertTrue(web.requests.single().headers["Accept"].orEmpty().startsWith("text/html"))
    }

    @Test
    fun `fetchCurrentAccount turns visitors away`() = runTest {
        web.respond = { homePage(uid = 0) }
        failure<SessionExpiredException> { web.api().fetchCurrentAccount() }

        // weibo.com sends a lapsed session to passport.
        web.respond = {
            if (it.url.host == "weibo.com") redirect("https://passport.weibo.com/visitor/visitor")
            else html("<html></html>")
        }
        failure<SessionExpiredException> { web.api().fetchCurrentAccount() }
    }

    @Test
    fun `fetchCurrentAccount reports a page without CONFIG`() = runTest {
        web.respond = { html("<html></html>") }

        val e = failure<WeiboApiException> { web.api().fetchCurrentAccount() }

        assertEquals("无法识别当前登录的账号", e.message)
    }

    private fun query() = web.requests.last().url

    @Test
    fun `fetchStatus asks for the full text of long posts`() = runTest {
        web.respond = { json((obj("ok" to 1) + statusJson(id = 42, text = "完整的长文")).toString()) }

        val status = web.api().fetchStatus("42")

        assertEquals("完整的长文", status.text)
        assertEquals("/ajax/statuses/show", query().encodedPath)
        assertEquals("42", query().queryParameter("id"))
        assertEquals("true", query().queryParameter("isGetLongText"))
        assertEquals(setOf("id", "isGetLongText"), query().queryParameterNames)
    }

    @Test
    fun `fetchStatus reports a response without a status`() = runTest {
        web.respond = { json("""{"ok":1}""") }

        failure<WeiboApiException> { web.api().fetchStatus("42") }
    }

    @Test
    fun `fetchComments sends the order and author, and continues from max_id`() = runTest {
        web.respond = {
            json(commentsPageJson(listOf(commentJson(id = 1)), maxId = 777, total = 99).toString())
        }
        val api = web.api()

        val first = api.fetchComments(statusId = "42", authorId = "1001", order = CommentOrder.Hot)
        assertEquals("/ajax/statuses/buildComments", query().encodedPath)
        assertEquals("42", query().queryParameter("id"))
        assertEquals("1001", query().queryParameter("uid"))
        assertEquals("0", query().queryParameter("flow"))
        assertEquals("0", query().queryParameter("fetch_level"))
        assertNull(query().queryParameter("max_id"))
        assertEquals("1", first.items.single().id)
        assertEquals("777", first.nextCursor)
        assertEquals(99, first.total)

        web.respond = { json(commentsPageJson(listOf(commentJson(id = 2))).toString()) }
        val last = api.fetchComments("42", "1001", CommentOrder.Time, maxId = first.nextCursor)
        assertEquals("1", query().queryParameter("flow"))
        assertEquals("777", query().queryParameter("max_id"))
        assertNull(last.nextCursor)
    }

    @Test
    fun `fetchComments - an empty page ends the list whatever max_id says`() = runTest {
        web.respond = { json(commentsPageJson(emptyList(), maxId = 5).toString()) }

        assertNull(web.api().fetchComments("42", "1001", CommentOrder.Hot).nextCursor)
    }

    @Test
    fun `fetchReplies lists the thread of a comment`() = runTest {
        web.respond = { json(commentsPageJson(listOf(commentJson(id = 3))).toString()) }

        val page = web.api().fetchReplies(commentId = "600", authorId = "1001")

        assertEquals("600", query().queryParameter("id"))
        assertEquals("1", query().queryParameter("fetch_level"))
        assertEquals("0", query().queryParameter("max_id"))
        assertEquals("3", page.items.single().id)
        assertNull(page.nextCursor)
    }

    @Test
    fun `fetchReposts pages until max_page`() = runTest {
        web.respond = {
            json(repostsPageJson(listOf(statusJson(id = 8)), maxPage = 2, total = 30).toString())
        }
        val api = web.api()

        val first = api.fetchReposts(statusId = "42")
        assertEquals("/ajax/statuses/repostTimeline", query().encodedPath)
        assertEquals("42", query().queryParameter("id"))
        assertEquals("1", query().queryParameter("page"))
        assertEquals("8", first.items.single().id)
        assertEquals("2", first.nextCursor)
        assertEquals(30, first.total)

        val second = api.fetchReposts(statusId = "42", page = 2)
        assertEquals("2", query().queryParameter("page"))
        assertNull(second.nextCursor)
    }

    @Test
    fun `fetchProfile looks a user up by uid`() = runTest {
        web.respond = {
            json(obj("ok" to 1, "data" to profileDataJson(user = userJson(id = 1001))).toString())
        }

        val profile = web.api().fetchProfile(uid = "1001")

        assertEquals("/ajax/profile/info", query().encodedPath)
        assertEquals(setOf("uid"), query().queryParameterNames)
        assertEquals("1001", query().queryParameter("uid"))
        assertEquals("1001", profile.user.id)
    }

    @Test
    fun `fetchProfile looks a user up by name, for mentions`() = runTest {
        web.respond = {
            json(
                obj("ok" to 1, "data" to profileDataJson(user = userJson(id = 1001, name = "示例用户")))
                    .toString()
            )
        }

        val profile = web.api().fetchProfile(screenName = "示例用户")

        assertEquals("示例用户", query().queryParameter("screen_name"))
        assertEquals(setOf("screen_name"), query().queryParameterNames)
        assertEquals("1001", profile.user.id)
    }

    @Test
    fun `fetchProfile reports an unknown name, which Weibo answers with ok=1`() = runTest {
        web.respond = { json("""{"ok":1,"user":{},"message":"用户不存在","abnormal":false}""") }

        val e = failure<WeiboApiException> { web.api().fetchProfile(screenName = "没有这个人") }
        assertEquals("找不到这个用户", e.message)
    }

    @Test
    fun `fetchUserStatuses pages by number until since_id runs out`() = runTest {
        web.respond = {
            json(
                userStatusesJson(
                        listOf(statusJson(id = 1, extra = mapOf("isTop" to 1)), statusJson(id = 2)),
                        total = 30,
                    )
                    .toString()
            )
        }
        val api = web.api()

        val first = api.fetchUserStatuses(uid = "1001")
        assertEquals("/ajax/statuses/mymblog", query().encodedPath)
        assertEquals(
            listOf("1001", "1", "0"),
            listOf("uid", "page", "feature").map { query().queryParameter(it) },
        )
        assertEquals(listOf("1", "2"), first.items.map { it.id })
        assertTrue(first.items.first().pinned)
        assertEquals("2", first.nextCursor)
        assertEquals(30, first.total)

        web.respond = {
            json(userStatusesJson(listOf(statusJson(id = 3)), sinceId = "").toString())
        }
        val last = api.fetchUserStatuses(uid = "1001", page = 2)
        assertEquals("2", query().queryParameter("page"))
        assertEquals("3", last.items.single().id)
        assertNull(last.nextCursor)
    }

    @Test
    fun `fetchUserStatuses - an empty page ends the list`() = runTest {
        web.respond = { json(userStatusesJson(emptyList()).toString()) }

        assertNull(web.api().fetchUserStatuses(uid = "1001").nextCursor)
    }

    @Test
    fun `searchStatuses searches everyone, a page by number at a time`() = runTest {
        web.respond = {
            json(searchJson(listOf(statusJson(id = 1), statusJson(id = 2)), total = 735).toString())
        }
        val api = web.api()

        val first = api.searchStatuses("某个词")
        assertEquals("/ajax/statuses/search", query().encodedPath)
        // No atten=1: that keeps to followed users.
        assertEquals(setOf("q", "count", "page"), query().queryParameterNames)
        assertEquals(
            listOf("某个词", "20", "1"),
            listOf("q", "count", "page").map { query().queryParameter(it) },
        )
        assertEquals(listOf("1", "2"), first.statuses.items.map { it.id })
        assertEquals("2", first.statuses.nextCursor)
        assertEquals(735, first.statuses.total)
        assertNull(first.topic)

        api.searchStatuses("某个词", page = 2)
        assertEquals("2", query().queryParameter("page"))
    }

    @Test
    fun `searchStatuses - a topic comes with its lead and counts`() = runTest {
        web.respond = {
            json(
                searchJson(
                        listOf(statusJson(id = 1)),
                        topicHeads = topicHeadsJson(name = "某个话题", summary = "话题导语"),
                    )
                    .toString()
            )
        }

        val results = web.api().searchStatuses("#某个话题#")
        assertEquals("#某个话题#", query().queryParameter("q"))
        assertEquals("某个话题", results.topic?.name)
        assertEquals("话题导语", results.topic?.summary)
    }

    @Test
    fun `searchStatuses - only an empty page ends the results`() = runTest {
        web.respond = { json(searchJson(emptyList(), total = 735).toString()) }

        assertNull(web.api().searchStatuses("某个词", page = 37).statuses.nextCursor)
    }

    @Test
    fun `fetchHotSearch reads the list`() = runTest {
        web.respond = {
            json(hotSearchJson(realtime = listOf(hotItemJson("第一条", realpos = 1))).toString())
        }

        val hot = web.api().fetchHotSearch()

        assertEquals("/ajax/side/hotSearch", query().encodedPath)
        assertEquals("第一条", hot.items.single().word)
    }

    @Test
    fun `fetchUnreadNotices reads which kinds have counts, zero ones left out`() = runTest {
        web.respond = { json("""{"cmt":2,"mention_cmt":0,"attitude":1,"messages":3}""") }

        val unread = web.api().fetchUnreadNotices()

        val request = web.requests.single()
        assertEquals("rm.api.weibo.com", request.url.host)
        assertEquals("/2/remind/push_count.json", request.url.encodedPath)
        assertEquals("0", request.url.queryParameter("exclude_attitude"))
        assertNull(request.url.queryParameter("callback"))
        // The session's cookies go along, as they do from weibo.com.
        assertTrue(request.headers["Cookie"].orEmpty().contains("SUB=x"))
        assertEquals(setOf(NoticeKind.Comments, NoticeKind.Likes), unread.kinds)
    }

    @Test
    fun `fetchUnreadNotices - a refusal is an error, but never ends the session`() = runTest {
        web.respond = { json("""{"error":"auth faild!","error_code":21301}""", status = 403) }
        var expired = false
        val api =
            WeiboApi(
                web.http(WeiboCookieJar(mapOf("weibo.com" to mapOf("SUB" to "x")))),
                onSessionExpired = {
                    expired = true
                    false
                },
            )

        failure<WeiboApiException> { api.fetchUnreadNotices() }
        assertFalse(expired)
    }

    @Test
    fun `notice lists page from next_cursor until it is 0`() = runTest {
        web.respond = {
            json(
                noticePageJson("comments", listOf(commentNoticeJson(id = 9)), nextCursor = 8)
                    .toString()
            )
        }
        val api = web.api()

        val first = api.fetchCommentNotices()
        assertEquals("/ajax/message/cmt", query().encodedPath)
        assertEquals("0", query().queryParameter("max_id"))
        assertEquals("9", first.items.single().id)
        assertEquals("被评论的微博", first.items.single().status?.text)
        assertEquals("8", first.nextCursor)

        web.respond = {
            json(noticePageJson("comments", listOf(commentNoticeJson(id = 7))).toString())
        }
        val last = api.fetchCommentNotices(maxId = "8")
        assertEquals("8", query().queryParameter("max_id"))
        assertNull(last.nextCursor)
    }

    @Test
    fun `notice lists - an empty page ends the list whatever next_cursor says`() = runTest {
        web.respond = { json(noticePageJson("statuses", emptyList(), nextCursor = 8).toString()) }

        val page = web.api().fetchMentionStatuses()

        assertEquals("/ajax/statuses/mentions", query().encodedPath)
        assertNull(page.nextCursor)
    }

    @Test
    fun `mentioning comments and likes come from their own lists`() = runTest {
        web.respond = {
            json(
                (if (it.url.encodedPath.endsWith("attitudes"))
                        noticePageJson("attitudes", listOf(likeNoticeJson(id = 5)))
                    else noticePageJson("comments", listOf(commentNoticeJson(id = 4))))
                    .toString()
            )
        }
        val api = web.api()

        val mentions = api.fetchMentionComments()
        assertEquals("/ajax/comments/mentions", query().encodedPath)
        assertEquals("4", mentions.items.single().id)

        val likes = api.fetchLikeNotices()
        assertEquals("/ajax/message/attitudes", query().encodedPath)
        assertEquals("5", likes.items.single().id)
    }

    @Test
    fun `like and take back likes of statuses and comments`() = runTest {
        web.respond = { json("""{"ok":1,"result":true}""") }
        val api = web.api()

        api.likeStatus("42", liked = false)
        assertEquals("/ajax/statuses/cancelLike", query().encodedPath)
        assertEquals(mapOf("id" to "42"), web.requests.last().form)

        api.likeStatus("42")
        assertEquals("/ajax/statuses/setLike", query().encodedPath)

        api.likeComment("7")
        assertEquals("/ajax/statuses/updateLike", query().encodedPath)
        assertEquals(
            mapOf("object_id" to "7", "object_type" to "comment"),
            web.requests.last().form,
        )

        api.likeComment("7", liked = false)
        assertEquals("/ajax/statuses/destroyLike", query().encodedPath)
    }

    @Test
    fun `a like answered ok but without result failed`() = runTest {
        // What taking a like back right after giving it gets.
        web.respond = { json("""{"ok":1,"result":false}""") }

        failure<WeiboApiException> { web.api().likeStatus("42", liked = false) }
    }

    @Test
    fun `a refused write carries its message`() = runTest {
        web.respond = {
            json(
                """{"ok":0,"message":"微博不存在","msg":"微博不存在","error_code":20101,""" +
                    """"error_type":"toast"}"""
            )
        }

        assertEquals("微博不存在", failure<WeiboApiException> { web.api().likeStatus("42") }.message)
    }

    @Test
    fun `postComment comments, and returns the new comment`() = runTest {
        web.respond = {
            json(
                obj("ok" to 1, "msg" to "评论成功", "data" to commentJson(id = 9, text = "好的"))
                    .toString()
            )
        }

        val comment = web.api().postComment(statusId = "42", text = "好的 & 再见")

        assertEquals("/ajax/comments/create", query().encodedPath)
        assertEquals(
            mapOf(
                "id" to "42",
                "comment" to "好的 & 再见",
                "is_repost" to "0",
                "comment_ori" to "0",
                "is_comment" to "0",
            ),
            web.requests.single().form,
        )
        assertEquals("9", comment?.id)
        assertEquals("好的", comment?.text)
    }

    @Test
    fun `postComment with replyTo replies`() = runTest {
        web.respond = { json(obj("ok" to 1, "data" to commentJson(id = 10)).toString()) }

        web.api().postComment(statusId = "42", text = "同意", replyTo = "9")

        assertEquals("/ajax/comments/reply", query().encodedPath)
        val form = web.requests.single().form
        assertEquals("42", form["id"])
        assertEquals("9", form["cid"])
        assertEquals("同意", form["comment"])
    }

    @Test
    fun `postComment with a picture sends its pid, text or not`() = runTest {
        web.respond = { json(obj("ok" to 1, "data" to commentJson(id = 10)).toString()) }

        web.api().postComment(statusId = "42", text = "", pid = "0067abc")

        assertEquals(
            mapOf(
                "id" to "42",
                "comment" to "",
                "pic_id" to "0067abc",
                "is_repost" to "0",
                "comment_ori" to "0",
                "is_comment" to "0",
            ),
            web.requests.single().form,
        )
    }

    @Test
    fun `allowsCommentPicture reads the answer's result`() = runTest {
        var result = true
        web.respond = { json("""{"ok":1,"result":$result}""") }
        val api = web.api()

        assertTrue(api.allowsCommentPicture("42"))
        assertEquals("/ajax/statuses/checkAllowCommentWithPic", query().encodedPath)
        assertEquals("42", query().queryParameter("id"))

        result = false
        assertFalse(api.allowsCommentPicture("42"))
    }

    @Test
    fun deleteComment() = runTest {
        web.respond = { json(obj("ok" to 1, "data" to commentJson(id = 9)).toString()) }

        web.api().deleteComment("9")

        assertEquals("/ajax/statuses/destroyComment", query().encodedPath)
        assertEquals(mapOf("cid" to "9"), web.requests.single().form)
    }

    @Test
    fun `postStatus posts text and pictures for an audience`() = runTest {
        web.respond = {
            json(
                obj(
                        "ok" to 1,
                        "msg" to "发布成功",
                        "data" to
                            statusJson(
                                id = 77,
                                text = "你好",
                                extra = mapOf("visible" to obj("type" to 1, "list_id" to 0)),
                            ),
                    )
                    .toString()
            )
        }

        val status =
            web.api()
                .postStatus(
                    text = "你好 & #话题#",
                    pids = listOf("p1", "p2"),
                    visibility = StatusVisibility.OnlyMe,
                )

        assertEquals("/ajax/statuses/update", query().encodedPath)
        assertEquals(
            mapOf(
                "content" to "你好 & #话题#",
                "pic_id" to """[{"type":"pic","pid":"p1"},{"type":"pic","pid":"p2"}]""",
                "visible" to "1",
            ),
            web.requests.single().form,
        )
        assertEquals("77", status?.id)
        assertEquals(StatusVisibility.OnlyMe, status?.visibility)
    }

    @Test
    fun `postStatus without pictures sends no pic_id`() = runTest {
        web.respond = { json(obj("ok" to 1, "data" to statusJson()).toString()) }

        web.api().postStatus(text = "只有字")

        assertEquals(mapOf("content" to "只有字", "visible" to "0"), web.requests.single().form)
    }

    @Test
    fun `repostStatus reposts with the text, for an audience`() = runTest {
        web.respond = {
            json(
                obj(
                        "ok" to 1,
                        "msg" to "转发成功",
                        "data" to
                            obj(
                                "statuses" to
                                    statusJson(
                                        id = 78,
                                        text = "说两句",
                                        retweeted = statusJson(id = 1),
                                    )
                            ),
                    )
                    .toString()
            )
        }

        val repost =
            web.api()
                .repostStatus(
                    id = "1",
                    text = "说两句 & //@某人:原话",
                    alsoComment = true,
                    visibility = StatusVisibility.OnlyMe,
                )

        assertEquals("/ajax/statuses/normal_repost", query().encodedPath)
        assertEquals(
            mapOf(
                "id" to "1",
                "comment" to "说两句 & //@某人:原话",
                "is_comment" to "1",
                "comment_ori" to "0",
                "is_repost" to "0",
                "visible" to "1",
            ),
            web.requests.single().form,
        )
        assertEquals("78", repost?.id)
        assertEquals("1", repost?.retweeted?.id)
    }

    @Test
    fun `repostStatus without text sends no comment`() = runTest {
        web.respond = { json(obj("ok" to 1, "data" to statusJson(id = 78)).toString()) }

        val repost = web.api().repostStatus(id = "1")

        assertEquals(
            mapOf(
                "id" to "1",
                "is_comment" to "0",
                "comment_ori" to "0",
                "is_repost" to "0",
                "visible" to "0",
            ),
            web.requests.single().form,
        )
        assertEquals("78", repost?.id)
    }

    @Test
    fun deleteStatus() = runTest {
        web.respond = { json(obj("ok" to 1, "data" to statusJson(id = 77)).toString()) }

        web.api().deleteStatus("77")

        assertEquals("/ajax/statuses/destroy", query().encodedPath)
        assertEquals(mapOf("id" to "77"), web.requests.single().form)
    }

    @Test
    fun `uploadPicture sends the bytes to picupload, without XHR headers, and returns the pid`() =
        runTest {
            web.respond = {
                html(
                    """{"code":"A00006","data":{"count":1,""" +
                        """"pics":{"pic_1":{"width":10,"pid":"0067abc","ret":1}}}}"""
                )
            }
            val api = web.api(mapOf("weibo.com" to mapOf("SUB" to "x", "XSRF-TOKEN" to "tok")))

            val pid = api.uploadPicture("GIF89a".toByteArray())

            val request = web.requests.single()
            assertEquals("POST", request.method)
            assertEquals("picupload.weibo.com", request.url.host)
            assertEquals("/interface/pic_upload.php", request.url.encodedPath)
            assertEquals(
                mapOf("app" to "miniblog", "s" to "json", "p" to "1", "data" to "1"),
                request.url.queryParameterNames.associateWith { request.url.queryParameter(it) },
            )
            assertEquals("GIF89a", request.body?.utf8())
            assertEquals("multipart/form-data", request.headers["Content-Type"])
            assertTrue(request.headers["Cookie"].orEmpty().contains("SUB=x"))
            assertEquals("https://weibo.com", request.headers["Origin"])
            assertNull(request.headers["X-Requested-With"])
            assertNull(request.headers["X-XSRF-TOKEN"])
            assertEquals("0067abc", pid)
        }

    @Test
    fun `uploadPicture fails on an answer without a pid`() = runTest {
        web.respond = { html("""{"code":"A20001","data":{"count":-10}}""") }

        failure<WeiboApiException> { web.api().uploadPicture(byteArrayOf(1, 2, 3)) }
    }

    @Test
    fun `parseUploadedPid reads either upload answer, after a script prelude`() {
        assertEquals(
            "a1",
            parseUploadedPid(
                """<script>document.domain="sina.com.cn";</script>""" +
                    """{"code":"A00006","data":{"pics":{"pic_1":{"pid":"a1"}}}}"""
            ),
        )
        assertEquals("b2", parseUploadedPid("""{"ret":true,"pic":{"pid":"b2"}}"""))
    }

    @Test
    fun `parseUploadedPid is null for failures and junk`() {
        assertNull(parseUploadedPid("""{"ret":false,"errno":-10}"""))
        assertNull(parseUploadedPid("""{"code":"A20001"}"""))
        assertNull(parseUploadedPid("<html>"))
        assertNull(parseUploadedPid("{oops"))
    }
}

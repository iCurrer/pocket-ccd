package com.pocketccd.prototype.camera

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 对账判定规则的穷举测试。
 *
 * 这段逻辑决定"删不删用户的照片"，错了就是不可逆的数据丢失，所以每条安全阀
 * 都要有对应的反例——不是测"正常情况能删"，而是测"异常情况绝不删"。
 */
class GalleryReconcilerTest {

    private val now = 1_792_392_057_689L

    private fun photo(
        id: String = "film_1792392057689_gold200",
        fileName: String = "film_1792392057689_gold200.jpg",
        capturedAt: Long = now - 3_600_000L, // 一小时前，已过等待期
        published: Boolean = true,
    ) = GalleryReconciler.Candidate(id, fileName, capturedAt, published)

    private fun stale(
        candidates: List<GalleryReconciler.Candidate>,
        names: Set<String> = emptySet(),
        timestamps: Set<Long> = emptySet(),
    ) = GalleryReconciler.staleIds(
        candidates = candidates,
        galleryNames = names,
        galleryTimestampSeconds = timestamps,
        now = now,
    )

    @Test
    fun `相册里还在的照片不删`() {
        val p = photo()
        assertEquals(emptyList<String>(), stale(listOf(p), names = setOf(p.fileName)))
    }

    @Test
    fun `相册里没有的旧照片判定为已删除`() {
        // 注意这里给了一个**非空**索引：空索引有专门的安全阀（见下一条）。
        val p = photo()
        assertEquals(listOf(p.id), stale(listOf(p), names = setOf("someone-else.jpg")))
    }

    @Test
    fun `等待期内的新照片不删`() {
        // 刚写完 30 秒，媒体库可能还没索引完成。
        val fresh = photo(capturedAt = now - 30_000L)
        assertEquals(emptyList<String>(), stale(listOf(fresh)))
    }

    @Test
    fun `等待期前一毫秒仍不删`() {
        // 差 1ms 到等待期：索引很可能还没完成，必须留着。
        val almost = photo(capturedAt = now - GalleryReconciler.GRACE_MILLIS + 1)
        assertEquals(emptyList<String>(), stale(listOf(almost)))
    }

    @Test
    fun `相册发布失败的照片绝不删`() {
        // 唯一一份，用户可能一次都没在相册里见过——不能拿"相册没有"当删除证据。
        val p = photo(published = false)
        assertEquals(emptyList<String>(), stale(listOf(p)))
    }

    @Test
    fun `被移动到别的相册的照片不删`() {
        // 文件名不变、RELATIVE_PATH 变了。索引里仍有这个 DISPLAY_NAME。
        val p = photo()
        assertEquals(emptyList<String>(), stale(listOf(p), names = setOf("film_other.jpg", p.fileName)))
    }

    @Test
    fun `被重命名的照片靠拍摄时间保住`() {
        // 名字变了但 DATE_TAKEN 没变：只有时间戳能证明这张照片还在。
        val p = photo()
        val seconds = setOf(now / 1000L, p.capturedAt / 1000L)
        assertEquals(emptyList<String>(), stale(listOf(p), timestamps = seconds))
    }

    @Test
    fun `DATE_TAKEN 被截断到秒时仍能匹配`() {
        // 部分机型把毫秒抹掉：存进去 1792388000000，我们是 ...000_123。
        val p = photo(capturedAt = 1_792_388_000_123L)
        assertEquals(
            emptyList<String>(),
            stale(listOf(p), timestamps = setOf(1_792_388_000L)),
        )
    }

    @Test
    fun `时间戳对不上且名字对不上时判定删除`() {
        val p = photo()
        assertEquals(
            listOf(p.id),
            stale(listOf(p), names = setOf("someone-else.jpg"), timestamps = setOf(now / 1000L)),
        )
    }

    @Test
    fun `只有被删的那几张进结果集`() {
        val kept = photo(id = "a", fileName = "a.jpg", capturedAt = now - 3_600_000L)
        val gone = photo(id = "b", fileName = "b.jpg", capturedAt = now - 7_200_000L)
        val fresh = photo(id = "c", fileName = "c.jpg", capturedAt = now - 1_000L)
        val unpublished = photo(id = "d", fileName = "d.jpg", published = false)

        assertEquals(
            listOf("b"),
            stale(listOf(kept, gone, fresh, unpublished), names = setOf("a.jpg")),
        )
    }

    @Test
    fun `空相册索引一张都不删`() {
        // 曾经这条断言的是"空索引 → 全删"，并把责任推给 PhotoStore 的 null 分支 ——
        // 而那个分支根本不覆盖"查询成功但 0 行"。索引重建 / 卷重挂载 / owner 记录丢失
        // 都会让查询成功返回 0 行，那时唯一一份私有副本会被整库删除。
        val all = listOf(
            photo(id = "a", fileName = "a.jpg"),
            photo(id = "b", fileName = "b.jpg"),
        )
        assertEquals(emptyList<String>(), stale(all))
    }

    @Test
    fun `只有时间戳没有文件名时仍按时间判活`() {
        // 部分机型的 DISPLAY_NAME 列可能为空串，但 DATE_TAKEN 正常。
        // 这种情况下索引不算空，命中时间戳的照片要保住。
        val p = photo()
        assertEquals(
            emptyList<String>(),
            stale(listOf(p), timestamps = setOf(p.capturedAt / 1000L)),
        )
    }
}

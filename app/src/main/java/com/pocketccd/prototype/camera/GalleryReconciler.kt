package com.pocketccd.prototype.camera

/**
 * 私有副本 ↔ 系统相册对账的**纯判断逻辑**。
 *
 * 为什么要把这段单独拆出来：它决定"要不要删掉用户的照片"，是整个对账里最不能
 * 出错的一步；而读 MediaStore 的那一半离不开设备、没法单测。拆开之后，
 * 「什么情况该删、什么情况必须留」可以被穷举验证。
 *
 * 背景：成片写两份（D-019）——私有目录是照片墙的数据源，系统相册是给外面看的。
 * 用户在系统相册里删掉一张，私有副本不会自己消失，照片墙就一直在。对账就是
 * 把这个分叉收回来。
 *
 * 这里**只负责判定**，不碰文件、不碰 ContentResolver。
 */
internal object GalleryReconciler {

    /**
     * 等待期：刚写完的照片可能还没被媒体库索引完成，这期间一律不判死刑。
     *
     * 宁可让"刚删完马上回来看"这一种情况暂时不同步（下次回前台会补上），
     * 也不能因为索引延迟把刚拍的照片删掉。
     */
    const val GRACE_MILLIS = 120_000L

    /**
     * 一笔待判定的成片。
     *
     * @param published 落盘时有没有真的写进系统相册。写失败的（相册不可用、
     *   空间不足）**绝不能**当成"用户删了"——那张照片用户可能一次都没在相册里见过，
     *   拿"相册里没有"当删除证据会把唯一一份删掉。
     */
    data class Candidate(
        val id: String,
        val fileName: String,
        val capturedAt: Long,
        val published: Boolean,
    )

    /**
     * 找出「用户已经在系统相册里删掉」的成片。
     *
     * 判活的条件有三个，命中任一即视为**还活着**，必须保留：
     *
     * 1. 文件名在相册里 —— 常规情况。
     * 2. 拍摄时间在相册里 —— 用户把照片**移动**到别的相册时文件名不变但路径变了；
     *    用户在相册里**重命名**时文件名变了但 `DATE_TAKEN` 不变。两个条件互补，
     *    覆盖这两种"照片还在、只是不叫原来那个名字"的情况。
     * 3. 还没到等待期 —— 见 [GRACE_MILLIS]。
     *
 * 时间戳按**秒**比对：`MediaStore` 的 `DATE_TAKEN` 在部分机型上会被截断到秒，
 * 毫秒级精确比对会全部失配，等于把所有老照片误判成已删除。
 *
 * ### 空索引 ≠ 用户删空了相册
 *
 * 索引一条都没有时**不下任何判断**，直接返回空列表。这条不是冗余的防御：MediaProvider
 * 重建索引、卷重挂载、本应用 `owner_package_name` 记录丢失（我们没有读媒体权限，
 * 可见性完全依赖 owner 记录），都会让查询"成功返回 0 行"。若把 0 行当成"用户删光了"，
 * 唯一一份私有副本会被整库删除且无法恢复。
 *
 * 代价是真实的：用户真把相册里本应用的照片全删了，照片墙不会跟着清空（要么拍一张新的
 * 打破空索引，要么在 App 内删——那个功能还没做）。**宁可留一张幽灵照片，也不要清库。**
 *
 * @param galleryNames 相册现存条目的 `DISPLAY_NAME`
 * @param galleryTimestampSeconds 相册现存条目的 `DATE_TAKEN`（**秒**）
 * @return 应当判定为已删除的成片 id
 */
fun staleIds(
    candidates: List<Candidate>,
    galleryNames: Set<String>,
    galleryTimestampSeconds: Set<Long>,
    now: Long,
    graceMillis: Long = GRACE_MILLIS,
): List<String> {
    if (galleryNames.isEmpty() && galleryTimestampSeconds.isEmpty()) return emptyList()
    return candidates.filter { photo ->
        photo.published &&
            now - photo.capturedAt >= graceMillis &&
            photo.fileName !in galleryNames &&
            (photo.capturedAt / 1000L) !in galleryTimestampSeconds
    }.map { it.id }
}
}

package com.pocketccd.prototype.camera

/**
 * 5×7 点阵字模。日期后背用的就是这种字：一台 90 年代的后背没有矢量字体，
 * 它是 5×7 的 LED/点阵**逐点曝光**，所以放大看得到一个个方点。
 *
 * 用系统等宽字体画出来的日期一眼就假 —— 笔画是平滑的矢量轮廓，而真实日期后背的
 * 每个字都是 5×7 个亮块组成的。这里只放日期需要的字符（数字、空格、撇号）。
 */
internal object DotMatrixFont {

    const val COLUMNS = 5
    const val ROWS = 7

    /** 每个字符 7 行 × 5 列，`#` 亮、`.` 灭。 */
    private val glyphs: Map<Char, List<String>> = mapOf(
        '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to listOf("####.", "....#", "....#", ".###.", "....#", "....#", "####."),
        '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
        '\'' to listOf("..#..", "..#..", ".....", ".....", ".....", ".....", "....."),
        ' ' to listOf(".....", ".....", ".....", ".....", ".....", ".....", "....."),
    )

    private val unknown = listOf("#####", "#...#", "#...#", "#...#", "#...#", "#...#", "#####")

    /** 逐点遍历：回调 (列, 行) 表示这个点是亮的。 */
    inline fun forEachDot(character: Char, action: (column: Int, row: Int) -> Unit) {
        val rows = glyphs[character] ?: unknown
        for (row in 0 until ROWS) {
            val line = rows[row]
            for (column in 0 until COLUMNS) {
                if (column < line.length && line[column] == '#') action(column, row)
            }
        }
    }

    fun supports(character: Char): Boolean = character in glyphs
}

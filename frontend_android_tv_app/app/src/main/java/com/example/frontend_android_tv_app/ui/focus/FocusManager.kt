package com.example.frontend_android_tv_app.ui.focus

/**
 * Simple directional focus manager suitable for Android TV DPAD navigation.
 *
 * We model focus as a "row" and "column" index for the Home screen rows,
 * and a "linear index" for sets of buttons (Details, Player controls).
 */
class GridFocusManager(
    private val rowCount: Int,
    private val columnsForRow: (row: Int) -> Int
) {
    var rowIndex: Int = 0
        private set
    var colIndex: Int = 0
        private set

    fun setFocus(row: Int, col: Int) {
        rowIndex = row.coerceIn(0, rowCount - 1)
        val cols = columnsForRow(rowIndex).coerceAtLeast(1)
        colIndex = col.coerceIn(0, cols - 1)
    }

    fun moveUp(): Boolean {
        if (rowIndex <= 0) return false
        val newRow = rowIndex - 1
        val cols = columnsForRow(newRow).coerceAtLeast(1)
        rowIndex = newRow
        colIndex = colIndex.coerceIn(0, cols - 1)
        return true
    }

    fun moveDown(): Boolean {
        if (rowIndex >= rowCount - 1) return false
        val newRow = rowIndex + 1
        val cols = columnsForRow(newRow).coerceAtLeast(1)
        rowIndex = newRow
        colIndex = colIndex.coerceIn(0, cols - 1)
        return true
    }

    fun moveLeft(wrap: Boolean = true): Boolean {
        val cols = columnsForRow(rowIndex).coerceAtLeast(1)
        if (cols == 1) return false
        if (colIndex > 0) {
            colIndex -= 1
            return true
        }
        if (wrap) {
            colIndex = cols - 1
            return true
        }
        return false
    }

    fun moveRight(wrap: Boolean = true): Boolean {
        val cols = columnsForRow(rowIndex).coerceAtLeast(1)
        if (cols == 1) return false
        if (colIndex < cols - 1) {
            colIndex += 1
            return true
        }
        if (wrap) {
            colIndex = 0
            return true
        }
        return false
    }
}

class LinearFocusManager(
    private val itemCount: Int
) {
    var index: Int = 0
        private set

    fun setFocus(idx: Int) {
        if (itemCount <= 0) {
            index = 0
            return
        }
        index = idx.coerceIn(0, itemCount - 1)
    }

    fun moveLeft(wrap: Boolean = true): Boolean {
        if (itemCount <= 1) return false
        if (index > 0) {
            index -= 1
            return true
        }
        if (wrap) {
            index = itemCount - 1
            return true
        }
        return false
    }

    fun moveRight(wrap: Boolean = true): Boolean {
        if (itemCount <= 1) return false
        if (index < itemCount - 1) {
            index += 1
            return true
        }
        if (wrap) {
            index = 0
            return true
        }
        return false
    }
}

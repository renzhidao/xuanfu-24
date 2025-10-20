
package com.screenmask

data class Rule(
    val id: Long,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val color: Int,
    val enabled: Boolean
)

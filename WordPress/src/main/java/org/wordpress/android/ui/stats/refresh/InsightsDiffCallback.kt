package org.wordpress.android.ui.stats.refresh

import android.support.v7.util.DiffUtil.Callback

class InsightsDiffCallback(
    private val oldList: List<InsightsItem>,
    private val newList: List<InsightsItem>
) : Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val newItem = newList[newItemPosition]
        val oldItem = oldList[oldItemPosition]
        return if (oldItem.insightsType != null && newItem.insightsType != null) {
            oldItem.type == newItem.type && oldItem.insightsType == newItem.insightsType
        } else {
            oldItem.type == newItem.type
        }
    }

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}

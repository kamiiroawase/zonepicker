package com.github.kamiiroawase.zonepicker

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.kamiiroawase.zonepicker.databinding.ItemZoneBinding
import com.github.kamiiroawase.zonepicker.databinding.ItemZoneHeaderBinding

sealed interface ZoneRow {
    data class Header(
        val title: String,
    ) : ZoneRow

    data class Item(
        val zoneId: String,
        val title: String,
        val subtitle: String,
        val selected: Boolean,
    ) : ZoneRow
}

class ZoneAdapter(
    private val accentColor: Int,
    private val onItemClick: (String) -> Unit,
) : ListAdapter<ZoneRow, RecyclerView.ViewHolder>(DIFF) {
    class HeaderViewHolder(
        val binding: ItemZoneHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    class ItemViewHolder(
        val binding: ItemZoneBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is ZoneRow.Header -> TYPE_HEADER
            is ZoneRow.Item -> TYPE_ITEM
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemZoneHeaderBinding.inflate(inflater, parent, false))
        } else {
            ItemViewHolder(ItemZoneBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val row = getItem(position)) {
            is ZoneRow.Header -> {
                (holder as HeaderViewHolder).binding.headerText.text = row.title
            }

            is ZoneRow.Item -> {
                val binding = (holder as ItemViewHolder).binding

                binding.zoneNameText.text = row.title
                binding.zoneIdText.text = row.subtitle
                binding.zoneCheckImage.isVisible = row.selected
                binding.zoneCheckImage.imageTintList = ColorStateList.valueOf(accentColor)
                binding.zoneDivider.isVisible = position < itemCount - 1
                binding.zoneRow.setOnClickListener { onItemClick(row.zoneId) }

                ViewCompat.setStateDescription(
                    binding.zoneRow,
                    if (row.selected) binding.zoneRow.context.getString(R.string.zp_selected) else null,
                )
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1

        private val DIFF =
            object : DiffUtil.ItemCallback<ZoneRow>() {
                override fun areItemsTheSame(
                    oldItem: ZoneRow,
                    newItem: ZoneRow,
                ): Boolean =
                    when {
                        oldItem is ZoneRow.Header && newItem is ZoneRow.Header -> {
                            oldItem.title == newItem.title
                        }

                        oldItem is ZoneRow.Item && newItem is ZoneRow.Item -> {
                            oldItem.zoneId == newItem.zoneId
                        }

                        else -> {
                            false
                        }
                    }

                override fun areContentsTheSame(
                    oldItem: ZoneRow,
                    newItem: ZoneRow,
                ) = oldItem == newItem
            }
    }
}

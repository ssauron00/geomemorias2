package com.example.geomemorias2

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.geomemorias2.databinding.ItemReminderBinding
import java.text.DecimalFormat

class ReminderAdapter(
    private val onEdit: (Reminder) -> Unit,
    private val onDelete: (Reminder) -> Unit
) : ListAdapter<Reminder, ReminderAdapter.ReminderViewHolder>(ReminderDiffCallback()) {

    private var currentLocations: Map<String, Pair<Double, Double>> = emptyMap()

    fun updateLocations(locations: Map<String, Pair<Double, Double>>) {
        currentLocations = locations
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val binding = ItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReminderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ReminderViewHolder(private val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(reminder: Reminder) {
            binding.tvReminderText.text = reminder.text
            
            // Use string resource for radius with format argument
            binding.tvRadius.text = binding.root.context.getString(R.string.radius_prefix, reminder.radiusM)

            // Calculate distance if we have current location
            val location = currentLocations["current"]
            if (location != null) {
                val distance = GeoUtils.haversineM(
                    location.first, location.second,
                    reminder.lat, reminder.lng
                )
                val df = DecimalFormat("#.#")
                // Use string resource for distance with format argument
                binding.tvDistance.text = binding.root.context.getString(R.string.distance_prefix, "${df.format(distance)}m")
            } else {
                // Use string resource for unavailable distance
                binding.tvDistance.text = binding.root.context.getString(R.string.distance_not_available)
            }

            binding.btnEdit.setOnClickListener { onEdit(reminder) }
            binding.btnDelete.setOnClickListener { onDelete(reminder) }
        }
    }

    class ReminderDiffCallback : DiffUtil.ItemCallback<Reminder>() {
        override fun areItemsTheSame(oldItem: Reminder, newItem: Reminder): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Reminder, newItem: Reminder): Boolean {
            return oldItem == newItem
        }
    }
}
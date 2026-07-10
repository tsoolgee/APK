package com.overlaymanager.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView

/**
 * Plain android.widget.BaseAdapter for a ListView - no androidx.recyclerview,
 * which (like appcompat) requires API 21+ in its current releases.
 */
class LayerAdapter(
    private val context: Context,
    private val onEdit: (OverlayLayer) -> Unit,
    private val onDelete: (OverlayLayer) -> Unit,
    private val onToggleEnabled: (OverlayLayer, Boolean) -> Unit
) : BaseAdapter() {

    private val items = mutableListOf<OverlayLayer>()

    fun submitList(newItems: List<OverlayLayer>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): OverlayLayer = items[position]
    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    private class ViewHolder(view: View) {
        val name: TextView = view.findViewById(R.id.textLayerName)
        val subtitle: TextView = view.findViewById(R.id.textLayerSubtitle)
        val enabledSwitch: Switch = view.findViewById(R.id.switchEnabled)
        val deleteButton: ImageButton = view.findViewById(R.id.buttonDelete)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder
        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_layer, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val layer = items[position]
        holder.name.text = layer.name
        holder.subtitle.text = context.getString(
            R.string.layer_subtitle_format,
            layer.widthPx, layer.heightPx, layer.alpha
        )

        holder.enabledSwitch.setOnCheckedChangeListener(null)
        holder.enabledSwitch.isChecked = layer.enabled
        holder.enabledSwitch.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            onToggleEnabled(layer, checked)
        }

        view.setOnClickListener { onEdit(layer) }
        holder.deleteButton.setOnClickListener { onDelete(layer) }

        return view
    }
}

package com.sknote.app.ui.discussion

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sknote.app.databinding.ItemImagePreviewBinding

data class ImageItem(
    val uri: Uri,
    var uploadedUrl: String? = null,
    var isUploading: Boolean = false
)

class ImagePreviewAdapter(
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ImagePreviewAdapter.ViewHolder>() {

    private val items = mutableListOf<ImageItem>()

    fun getItems(): List<ImageItem> = items.toList()

    fun addImage(uri: Uri) {
        items.add(ImageItem(uri))
        notifyItemInserted(items.size - 1)
    }

    fun removeAt(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, items.size)
        }
    }

    fun setUploading(position: Int, uploading: Boolean) {
        if (position in items.indices) {
            items[position].isUploading = uploading
            notifyItemChanged(position)
        }
    }

    fun setUploadedUrl(position: Int, url: String) {
        if (position in items.indices) {
            items[position].uploadedUrl = url
            items[position].isUploading = false
            notifyItemChanged(position)
        }
    }

    fun indexOfUri(uri: Uri): Int = items.indexOfFirst { it.uri == uri }

    fun size() = items.size

    fun allUploaded(): Boolean = items.all { it.uploadedUrl != null }

    fun getUploadedUrls(): List<String> = items.mapNotNull { it.uploadedUrl }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemImagePreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemImagePreviewBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ImageItem) {
            Glide.with(binding.ivPreview.context)
                .load(item.uri)
                .centerCrop()
                .into(binding.ivPreview)

            binding.progressUpload.visibility = if (item.isUploading) View.VISIBLE else View.GONE
            binding.btnRemove.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onRemove(pos)
            }
        }
    }
}

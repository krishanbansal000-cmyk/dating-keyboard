package com.datingcopilot.keyboard.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.datingcopilot.keyboard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageGridAdapter(
    private val images: List<ImageBrowserActivity.ImageItem>,
    private val onClick: (Uri) -> Unit
) : RecyclerView.Adapter<ImageGridAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val imageView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (120 * context.resources.displayMetrics.density).toInt()
            ).apply {
                setMargins(
                    (4 * context.resources.displayMetrics.density).toInt(),
                    (4 * context.resources.displayMetrics.density).toInt(),
                    (4 * context.resources.displayMetrics.density).toInt(),
                    (4 * context.resources.displayMetrics.density).toInt()
                )
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            val bg = android.graphics.drawable.GradientDrawable()
            bg.cornerRadius = 8 * context.resources.displayMetrics.density
            bg.setColor(context.resources.getColor(R.color.bg_surface, null))
            background = bg
            clipToOutline = true
        }
        return ViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = images[position]
        holder.imageView.setImageDrawable(null)

        // Load thumbnail asynchronously with proper scaling
        val context = holder.imageView.context
        CoroutineScope(Dispatchers.IO).launch {
            val bitmap = loadThumbnail(context, item.uri, 300, 300)
            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    holder.imageView.setImageBitmap(bitmap)
                } else {
                    // Show a placeholder with the filename
                    holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                }
                holder.imageView.setOnClickListener { onClick(item.uri) }
            }
        }
    }

    private fun loadThumbnail(context: android.content.Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            // First decode with inJustDecodeBounds to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false

            // Decode with proper scaling
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    override fun getItemCount() = images.size

    class ViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)
}

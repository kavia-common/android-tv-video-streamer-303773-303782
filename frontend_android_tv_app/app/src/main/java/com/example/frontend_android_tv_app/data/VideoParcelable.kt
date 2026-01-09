package com.example.frontend_android_tv_app.data

import android.os.Parcel
import android.os.Parcelable

/**
 * Lightweight Parcelable representation for navigation arguments.
 */
data class VideoParcelable(
    val id: String,
    val title: String,
    val category: String,
    val thumbnailUrl: String,
    val description: String,
    val videoUrl: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(title)
        parcel.writeString(category)
        parcel.writeString(thumbnailUrl)
        parcel.writeString(description)
        parcel.writeString(videoUrl)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<VideoParcelable> {
        override fun createFromParcel(parcel: Parcel): VideoParcelable = VideoParcelable(parcel)
        override fun newArray(size: Int): Array<VideoParcelable?> = arrayOfNulls(size)
    }
}

fun Video.toParcelable(): VideoParcelable = VideoParcelable(
    id = id,
    title = title,
    category = category,
    thumbnailUrl = thumbnailUrl,
    description = description,
    videoUrl = videoUrl
)

fun VideoParcelable.toVideo(): Video = Video(
    id = id,
    title = title,
    category = category,
    thumbnailUrl = thumbnailUrl,
    description = description,
    videoUrl = videoUrl
)

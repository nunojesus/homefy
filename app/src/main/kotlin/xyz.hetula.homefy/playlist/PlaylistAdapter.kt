/*
 * Copyright (c) 2018 Tuomo Heino
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.hetula.homefy.playlist

import android.support.v7.util.SortedList
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.util.SortedListAdapterCallback
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.android.synthetic.main.list_item_playlist.view.*
import xyz.hetula.homefy.R

class PlaylistAdapter(private val click: (Playlist) -> Unit) :
        RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    private val mPlaylists = SortedList<Playlist>(Playlist::class.java, PlaylistCallback(this))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val context = holder.itemView.context
        val playlist = mPlaylists.get(position)
        holder.playlistName.text = playlist.name
        holder.playlistSongCount.text = context.getString(R.string.playlist_songs_format,
                playlist.songs.size)

        holder.itemView.setOnClickListener {
            click(playlist)
        }
    }

    override fun getItemCount(): Int {
        return mPlaylists.size()
    }

    fun setPlaylists(newPlaylists: Set<Playlist>) {
        mPlaylists.beginBatchedUpdates()
        for (i in mPlaylists.size() - 1 downTo 0) {
            val playlist = mPlaylists.get(i)
            if (!newPlaylists.contains(playlist)) {
                mPlaylists.removeItemAt(i)
            }
        }
        mPlaylists.addAll(newPlaylists)
        mPlaylists.endBatchedUpdates()
    }

    fun addPlaylist(newPlaylist: Playlist) {
        mPlaylists.add(newPlaylist)
    }

    class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val playlistName: TextView = itemView.playlistName
        val playlistSongCount: TextView = itemView.playlistSongCount
    }

    private class PlaylistCallback(adapter: PlaylistAdapter) : SortedListAdapterCallback<Playlist>(adapter) {

        override fun areItemsTheSame(item1: Playlist?, item2: Playlist?): Boolean {
            if (item1 == null && item2 == null) return true
            if (item1 == null || item2 == null) return false
            return item1.id == item2.id
        }

        override fun compare(o1: Playlist?, o2: Playlist?): Int {
            if (o1 == null && o2 == null) return 0
            if (o2 == null) return 1
            if (o1 == null) return -1
            return o1.name.compareTo(o2.name)
        }

        override fun areContentsTheSame(oldItem: Playlist?, newItem: Playlist?): Boolean {
            if (oldItem == null && newItem == null) return true
            if (oldItem == null || newItem == null) return false
            if (oldItem.name != newItem.name) return false
            return oldItem.songs.size == newItem.songs.size
        }

    }
}

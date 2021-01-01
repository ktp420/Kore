/*
 * Copyright 2017 XBMC Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xbmc.kore.ui.sections.favourites;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.xbmc.kore.R;
import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.jsonrpc.ApiList;
import org.xbmc.kore.jsonrpc.method.Favourites;
import org.xbmc.kore.jsonrpc.method.GUI;
import org.xbmc.kore.jsonrpc.type.FavouriteType;
import org.xbmc.kore.jsonrpc.type.PlaylistType;
import org.xbmc.kore.ui.AbstractListFragment;
import org.xbmc.kore.ui.viewgroups.RecyclerViewEmptyViewSupport;
import org.xbmc.kore.utils.LogUtils;
import org.xbmc.kore.utils.MediaPlayerUtils;
import org.xbmc.kore.utils.UIUtils;

import java.util.ArrayList;
import java.util.List;

public class FavouritesListFragment extends AbstractListFragment {
    private static final String TAG = "FavouritesListFragment";

    private Handler callbackHandler = new Handler();

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getFavourites();
    }

    @Override
    protected RecyclerViewEmptyViewSupport.OnItemClickListener createOnItemClickListener() {
        final ApiCallback<String> genericApiCallback = new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                // Do Nothing
            }

            @Override
            public void onError(int errorCode, String description) {
                Toast.makeText(getActivity(), description, Toast.LENGTH_SHORT).show();
            }
        };
        return new RecyclerViewEmptyViewSupport.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                final FavouritesAdapter favouritesAdapter = (FavouritesAdapter) getAdapter();
                final HostManager hostManager = HostManager.getInstance(getActivity());

                final FavouriteType.DetailsFavourite detailsFavourite =
                        favouritesAdapter.getItem(position);
                if (detailsFavourite == null) {
                    return;
                }
                if (detailsFavourite.type.equals(FavouriteType.FavouriteTypeEnum.WINDOW)
                        && !TextUtils.isEmpty(detailsFavourite.window)) {
                    if (!show_plugin(detailsFavourite.title, detailsFavourite.windowParameter)) {
                        GUI.ActivateWindow activateWindow = new GUI.ActivateWindow(detailsFavourite.window,
                                detailsFavourite.windowParameter);
                        hostManager.getConnection().execute(activateWindow, genericApiCallback, callbackHandler);
                    }
                } else if (detailsFavourite.type.equals(FavouriteType.FavouriteTypeEnum.MEDIA)
                        && !TextUtils.isEmpty(detailsFavourite.path)) {
                    final PlaylistType.Item playlistItem = new PlaylistType.Item();
                    playlistItem.file = detailsFavourite.path;
                    MediaPlayerUtils.play(FavouritesListFragment.this, playlistItem);
                    show_input_dialog_if_needed(detailsFavourite.title, detailsFavourite.path);
                } else {
                    Toast.makeText(getActivity(), R.string.unable_to_play_favourite_item,
                            Toast.LENGTH_SHORT).show();
                }
            }
        };
    }

    @Override
    protected RecyclerView.Adapter createAdapter() {
        return new FavouritesAdapter(getActivity(), HostManager.getInstance(getActivity()));
    }

    @Override
    public void onRefresh() {
        getFavourites();
    }

    private void getFavourites() {
        final HostManager hostManager = HostManager.getInstance(getActivity());
        final Favourites.GetFavourites action = new Favourites.GetFavourites();

        hostManager.getConnection().execute(action, new ApiCallback<ApiList<FavouriteType.DetailsFavourite>>() {
            @Override
            public void onSuccess(ApiList<FavouriteType.DetailsFavourite> result) {
                if (!isAdded()) return;
                LogUtils.LOGD(TAG, "Got Favourites");

                // To prevent the empty text from appearing on the first load, set it now
                getEmptyView().setText(getString(R.string.no_channels_found_refresh));
                ((FavouritesAdapter) getAdapter()).setFavouriteItems(result.items);
            }

            @Override
            public void onError(int errorCode, String description) {
                if (!isAdded()) return;
                LogUtils.LOGD(TAG, "Error getting favourites: " + description);

                getEmptyView().setText(getString(R.string.error_favourites, description));
                Toast.makeText(getActivity(), getString(R.string.error_favourites, description),
                        Toast.LENGTH_SHORT).show();
            }
        }, callbackHandler);
    }

    private class FavouritesAdapter extends RecyclerView.Adapter {

        private final HostManager hostManager;
        private final int artWidth, artHeight;
        private Context context;
        private ArrayList<FavouriteType.DetailsFavourite> favouriteItems = new ArrayList<>();

        FavouritesAdapter(@NonNull Context context, HostManager hostManager) {
            this.context = context;
            this.hostManager = hostManager;
            Resources resources = context.getResources();
            artWidth = (int) (resources.getDimension(R.dimen.channellist_art_width) /
                    UIUtils.IMAGE_RESIZE_FACTOR);
            artHeight = (int) (resources.getDimension(R.dimen.channellist_art_heigth) /
                    UIUtils.IMAGE_RESIZE_FACTOR);
        }

        public void setFavouriteItems(List<FavouriteType.DetailsFavourite> favouriteItems) {
            this.favouriteItems.clear();
            this.favouriteItems.addAll(favouriteItems);
            notifyDataSetChanged();
        }

        public FavouriteType.DetailsFavourite getItem(int position) {
            return favouriteItems.get(position);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.grid_item_channel,
                                                                    parent, false);
            return new ViewHolder(view, context, hostManager, artWidth, artHeight);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((ViewHolder) holder).bindView(favouriteItems.get(position));
        }

        @Override
        public int getItemCount() {
            return favouriteItems.size();
        }
    }

    private class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener {
        final ImageView artView;
        final TextView titleView;
        final TextView detailView;
        final ImageView contextMenu;
        final HostManager hostManager;
        final int artWidth;
        final int artHeight;
        final Context context;

        ViewHolder(View itemView, Context context, HostManager hostManager, int artWidth, int artHeight) {
            super(itemView);
            this.context = context;
            this.hostManager = hostManager;
            this.artWidth = artWidth;
            this.artHeight = artHeight;
            artView = itemView.findViewById(R.id.art);
            titleView = itemView.findViewById(R.id.title);
            detailView = itemView.findViewById(R.id.details);
            contextMenu = itemView.findViewById(R.id.list_context_menu);
            contextMenu.setOnClickListener(this);
        }

        void bindView(FavouriteType.DetailsFavourite favouriteDetail) {
            titleView.setText(UIUtils.applyMarkup(context, favouriteDetail.title));

            @StringRes final int typeRes;
            switch (favouriteDetail.type) {
                case FavouriteType.FavouriteTypeEnum.MEDIA:
                    typeRes = R.string.media;
                    break;
                case FavouriteType.FavouriteTypeEnum.SCRIPT:
                    typeRes = R.string.script;
                    break;
                case FavouriteType.FavouriteTypeEnum.WINDOW:
                    typeRes = R.string.window;
                    break;
                default:
                    typeRes = R.string.unknown;
            }
            detailView.setText(typeRes);

            UIUtils.loadImageWithCharacterAvatar(context, hostManager,
                                                 favouriteDetail.thumbnail, favouriteDetail.title,
                                                 artView, artWidth, artHeight, true);
            contextMenu.setVisibility(View.VISIBLE);
            contextMenu.setTag(favouriteDetail);
        }

        @Override
        public void onClick(View v) {
            final FavouriteType.DetailsFavourite f = (FavouriteType.DetailsFavourite) v.getTag();
            final PopupMenu popupMenu = new PopupMenu(context, v);
            popupMenu.getMenuInflater().inflate(
                    R.menu.media_filelist_item_fav, popupMenu.getMenu());
            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    switch (item.getItemId()) {
                        case R.id.action_favourites:
                            favourites(f);
                            return true;
                    }
                    return false;
                }
            });
	    popupMenu.show();
        }

        /**
         * Add or remove, if already added, the given media file from favourites.
         * @param f File to add/remove from favourites
         */
        private void favourites(final FavouriteType.DetailsFavourite f) {
            String path = f.path;
            if (FavouriteType.FavouriteTypeEnum.WINDOW.equals(f.type)) {
                path = f.windowParameter;
            }
            Favourites.AddFavourite action = new Favourites.AddFavourite(f.title, f.type, path, f.window);
            action.execute(hostManager.getConnection(), new ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    Toast.makeText(context, R.string.fav_modified, Toast.LENGTH_SHORT).show();
                    onRefresh();
                }

                @Override
                public void onError(int errorCode, String description) {
                    Toast.makeText(context,
                                   String.format(context.getString(R.string.error_mod_fav), description),
                                   Toast.LENGTH_SHORT).show();
                }
            }, callbackHandler);
        }
    }
}

/*
 * Copyright 2015 Synced Synapse. All rights reserved.
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
package org.xbmc.kore.ui.sections.video;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import org.xbmc.kore.R;
import org.xbmc.kore.Settings;
import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.jsonrpc.method.PVR;
import org.xbmc.kore.jsonrpc.method.Player;
import org.xbmc.kore.jsonrpc.type.PVRType;
import org.xbmc.kore.ui.AbstractSearchableFragment;
import org.xbmc.kore.ui.viewgroups.RecyclerViewEmptyViewSupport;
import org.xbmc.kore.utils.LogUtils;
import org.xbmc.kore.utils.UIUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Fragment that presents the PVR recordings list
 */
public class PVRRecordingsListFragment extends AbstractSearchableFragment {
    private static final String TAG = LogUtils.makeLogTag(PVRRecordingsListFragment.class);

    private HostManager hostManager;

    /**
     * Handler on which to post RPC callbacks
     */
    private Handler callbackHandler = new Handler();

    @Override
    protected RecyclerView.Adapter createAdapter() {
        return new RecordingsAdapter(getActivity(), R.layout.grid_item_recording);
    }

    @Override
    protected RecyclerViewEmptyViewSupport.OnItemClickListener createOnItemClickListener() {
        return new RecyclerViewEmptyViewSupport.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                final PVRType.DetailsRecording tag = ((RecordingsAdapter) getAdapter()).getItem(position);
                if (tag == null) return;

                Player.Open action = new Player.Open(Player.Open.TYPE_RECORDING, tag.recordingid);
                action.execute(hostManager.getConnection(), new ApiCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        if (!isAdded()) return;
                        LogUtils.LOGD(TAG, "Started recording");
                        // Start the recording
                        Toast.makeText(getActivity(),
                               String.format(getString(R.string.starting_recording), tag.title),
                               Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(int errorCode, String description) {
                        if (!isAdded()) return;
                        LogUtils.LOGD(TAG, "Error starting recording: " + description);

                        Toast.makeText(getActivity(),
                                       String.format(getString(R.string.error_starting_recording), description),
                                       Toast.LENGTH_SHORT).show();

                    }
                }, callbackHandler);
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        hostManager = HostManager.getInstance(getActivity());

        getEmptyView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRefresh();
            }
        });

        return root;
    }

    @Override
    public void onActivityCreated (Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        setSupportsSearch(true);
        browseRecordings();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!isAdded()) {
            // HACK: Fix crash reported on Play Store. Why does this is necessary is beyond me
            // copied from MovieListFragment#onCreateOptionsMenu
            super.onCreateOptionsMenu(menu, inflater);
            return;
        }

        inflater.inflate(R.menu.pvr_recording_list, menu);

        // Setup filters
        MenuItem hideWatched = menu.findItem(R.id.action_hide_watched),
                sortByNameAndDate = menu.findItem(R.id.action_sort_by_name_and_date_added),
                sortByDateAdded = menu.findItem(R.id.action_sort_by_date_added),
                unsorted = menu.findItem(R.id.action_unsorted);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        hideWatched.setChecked(preferences.getBoolean(Settings.KEY_PREF_PVR_RECORDINGS_FILTER_HIDE_WATCHED, Settings.DEFAULT_PREF_PVR_RECORDINGS_FILTER_HIDE_WATCHED));

        int sortOrder = preferences.getInt(Settings.KEY_PREF_PVR_RECORDINGS_SORT_ORDER, Settings.DEFAULT_PREF_PVR_RECORDINGS_SORT_ORDER);
        switch (sortOrder) {
            case Settings.SORT_BY_DATE_ADDED:
                sortByDateAdded.setChecked(true);
                break;
            case Settings.SORT_BY_NAME:
                sortByNameAndDate.setChecked(true);
                break;
            default:
                unsorted.setChecked(true);
                break;
        }

        super.onCreateOptionsMenu(menu, inflater);
    }


    @Override
    public void refreshList() {
       onRefresh();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        switch (item.getItemId()) {
            case R.id.action_hide_watched:
                item.setChecked(!item.isChecked());
                preferences.edit()
                        .putBoolean(Settings.KEY_PREF_PVR_RECORDINGS_FILTER_HIDE_WATCHED, item.isChecked())
                        .apply();
                refreshList();
                break;
            case R.id.action_sort_by_name_and_date_added:
                item.setChecked(true);
                preferences.edit()
                        .putInt(Settings.KEY_PREF_PVR_RECORDINGS_SORT_ORDER, Settings.SORT_BY_NAME)
                        .apply();
                refreshList();
                break;
            case R.id.action_sort_by_date_added:
                item.setChecked(true);
                preferences.edit()
                        .putInt(Settings.KEY_PREF_PVR_RECORDINGS_SORT_ORDER, Settings.SORT_BY_DATE_ADDED)
                        .apply();
                refreshList();
                break;
            case R.id.action_unsorted:
                item.setChecked(true);
                preferences.edit()
                        .putInt(Settings.KEY_PREF_PVR_RECORDINGS_SORT_ORDER, Settings.UNSORTED)
                        .apply();
                refreshList();
                break;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Swipe refresh layout callback
     */
    /** {@inheritDoc} */
    @Override
    public void onRefresh () {
        if (hostManager.getHostInfo() != null) {
            browseRecordings();
        } else {
            hideRefreshAnimation();
            Toast.makeText(getActivity(), R.string.no_xbmc_configured, Toast.LENGTH_SHORT)
                 .show();
        }
    }

    /**
     * Get the recording list and setup the gridview
     */
    private void browseRecordings() {
        PVR.GetRecordings action = new PVR.GetRecordings(PVRType.FieldsRecording.allValues);
        action.execute(hostManager.getConnection(), new ApiCallback<List<PVRType.DetailsRecording>>() {
            @Override
            public void onSuccess(List<PVRType.DetailsRecording> result) {
                if (!isAdded()) return;

                // To prevent the empty text from appearing on the first load, set it now
                getEmptyView().setText(getString(R.string.no_recordings_found_refresh));
                if (result == null) {
                    LogUtils.LOGD(TAG, "Got null recordings?");
                    return;
                }
                LogUtils.LOGD(TAG, "Got " + result.size() + " recordings");

                // As the JSON RPC API does not support sorting or filter parameters for PVR.GetRecordings
                // we apply the sorting and filtering right here.
                // See https://kodi.wiki/view/JSON-RPC_API/v9#PVR.GetRecordings
                List<PVRType.DetailsRecording> finalResult = filter(result);
                sort(finalResult);
                ((RecordingsAdapter) getAdapter()).setItems(finalResult);
            }

            private List<PVRType.DetailsRecording> filter(List<PVRType.DetailsRecording> itemList) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                boolean hideWatched = preferences.getBoolean(Settings.KEY_PREF_PVR_RECORDINGS_FILTER_HIDE_WATCHED, Settings.DEFAULT_PREF_PVR_RECORDINGS_FILTER_HIDE_WATCHED);

                String searchFilter = getSearchFilter();
                boolean hasSearchFilter = !TextUtils.isEmpty(searchFilter);
                // Split searchFilter to multiple lowercase words
                String[] lcWords = hasSearchFilter ? searchFilter.toLowerCase().split(" ") : null;

                if (!(hideWatched || hasSearchFilter)) {
                    return itemList;
                }

                List<PVRType.DetailsRecording> result = new ArrayList<>(itemList.size());
                for (PVRType.DetailsRecording item:itemList) {
                    if (hideWatched) {
                        if (item.playcount > 0) {
                            continue; // Skip this item as it is played.
                        } else {
                            // Heuristic: Try to guess if it's play from resume timestamp.
                            double resumePosition = item.resume.position;
                            int runtime = item.runtime;
                            if (runtime < resumePosition) {
                                // Tv show duration is smaller than resume position.
                                // The tv show likely has been watched.
                                // It's still possible some minutes have not yet been watched
                                // at the end of the show as some minutes at the
                                // recording start do not belong to the show.
                                // Never the less skip this item.
                                continue;
                            }
                        }
                    }

                    if (hasSearchFilter) {
                        // Require all lowercase words to match the item:
                        boolean allWordsMatch = true;
                        for (String lcWord:lcWords) {
                            if (!searchFilterWordMatches(lcWord, item)) {
                                allWordsMatch = false;
                                break;
                            }
                        }
                        if (!allWordsMatch) {
                            continue; // skip this item
                        }
                    }

                    result.add(item);
                }

                return result;
            }

            private boolean searchFilterWordMatches(String lcWord, PVRType.DetailsRecording item) {
                if (item.title.toLowerCase().contains(lcWord)
                        || item.channel.toLowerCase().contains(lcWord)) {
                    return true;
                }
                return false;
            }

            private void sort(List<PVRType.DetailsRecording> itemList) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

                int sortOrder = preferences.getInt(Settings.KEY_PREF_PVR_RECORDINGS_SORT_ORDER, Settings.DEFAULT_PREF_PVR_RECORDINGS_SORT_ORDER);

                Comparator<PVRType.DetailsRecording> comparator;
                switch (sortOrder) {
                    case Settings.SORT_BY_DATE_ADDED:
                        // sort by recording start time descending (most current first)
                        // luckily the starttime is in sortable format yyyy-MM-dd hh:mm:ss
                        comparator = new Comparator<PVRType.DetailsRecording>() {
                            @Override
                            public int compare(PVRType.DetailsRecording a, PVRType.DetailsRecording b) {
                                return  b.starttime.compareTo(a.starttime);
                            }
                        };
                        Collections.sort(itemList, comparator);
                        break;
                    case Settings.SORT_BY_NAME:
                        // sort by recording title and start time
                        comparator = new Comparator<PVRType.DetailsRecording>() {
                            @Override
                            public int compare(PVRType.DetailsRecording a, PVRType.DetailsRecording b) {
                                int result = a.title.compareToIgnoreCase(b.title);
                                if (0 == result) { // note the yoda condition ;)
                                    // sort by starttime descending (most current first)
                                    result = b.starttime.compareTo(a.starttime);
                                }
                                return result;
                            }
                        };
                        Collections.sort(itemList, comparator);
                        break;
                }
            }

            @Override
            public void onError(int errorCode, String description) {
                if (!isAdded()) return;
                LogUtils.LOGD(TAG, "Error getting recordings: " + description);

                // To prevent the empty text from appearing on the first load, set it now
                getEmptyView().setText(String.format(getString(R.string.error_getting_pvr_info), description));
                Toast.makeText(getActivity(),
                               String.format(getString(R.string.error_getting_pvr_info), description),
                               Toast.LENGTH_SHORT).show();
            }
        }, callbackHandler);
    }

    private class RecordingsAdapter extends RecyclerView.Adapter {
        Context ctx;
        int resource;
        List<PVRType.DetailsRecording> items;
        int artWidth;
        int artHeight;

        public RecordingsAdapter(Context context, int resource) {
            super();
            this.ctx = context;
            this.resource = resource;
            this.items = null;

            Resources resources = context.getResources();
            artWidth = (int)(resources.getDimension(R.dimen.channellist_art_width) /
                             UIUtils.IMAGE_RESIZE_FACTOR);
            artHeight = (int)(resources.getDimension(R.dimen.channellist_art_heigth) /
                              UIUtils.IMAGE_RESIZE_FACTOR);
        }

        public void setItems(List<PVRType.DetailsRecording> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        public PVRType.DetailsRecording getItem(int position) {
            if (items == null || position < 0 || position >= items.size()) {
                return null;
            }
            return items.get(position);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(ctx)
                                      .inflate(resource, parent, false);
            return new ViewHolder(view, ctx, hostManager, artWidth, artHeight);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            PVRType.DetailsRecording item = this.getItem(position);
            ((ViewHolder) holder).bindView(item, position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            if (items == null) {
                return 0;
            } else {
                return items.size();
            }
        }
    }

    /**
     * View holder pattern
     */
    private static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView art;
        TextView title;
        TextView details;
        TextView duration;

        Context context;
        int artWidth;
        int artHeight;
        HostManager hostManager;

        ViewHolder(View itemView, Context context, HostManager hostManager, int artWidth, int artHeight) {
            super(itemView);
            this.context = context;
            title = itemView.findViewById(R.id.title);
            details = itemView.findViewById(R.id.details);
            duration = itemView.findViewById(R.id.duration);
            art = itemView.findViewById(R.id.art);
        }

        public void bindView(PVRType.DetailsRecording recordingDetails, int position) {
            title.setText(UIUtils.applyMarkup(context, recordingDetails.title));
            details.setText(UIUtils.applyMarkup(context, recordingDetails.channel));
            UIUtils.loadImageWithCharacterAvatar(context, hostManager,
                                                 (recordingDetails.art != null) ?
                                                         recordingDetails.art.poster : recordingDetails.icon,
                                                 recordingDetails.channel,
                                                 art, artWidth, artHeight);
            int runtime = recordingDetails.runtime / 60;
            String _duration =
                    recordingDetails.starttime + " | " +
                    context.getString(R.string.minutes_abbrev, String.valueOf(runtime));
            duration.setText(_duration);
        }
    }
}

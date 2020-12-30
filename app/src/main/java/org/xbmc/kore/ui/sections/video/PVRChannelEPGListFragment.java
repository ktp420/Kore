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
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import org.xbmc.kore.R;
import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.jsonrpc.method.PVR;
import org.xbmc.kore.jsonrpc.type.PVRType;
import org.xbmc.kore.ui.AbstractSearchableFragment;
import org.xbmc.kore.ui.viewgroups.RecyclerViewEmptyViewSupport;
import org.xbmc.kore.utils.LogUtils;
import org.xbmc.kore.utils.UIUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Fragment that presents the Guide for a channel
 */
public class PVRChannelEPGListFragment extends AbstractSearchableFragment {
    private static final String TAG = LogUtils.makeLogTag(PVRChannelEPGListFragment.class);

    private HostManager hostManager;
    private int channelId;

    /**
     * Handler on which to post RPC callbacks
     */
    private Handler callbackHandler = new Handler();

    private static final String BUNDLE_KEY_CHANNELID = "bundle_key_channelid";

    @Override
    protected RecyclerView.Adapter createAdapter() {
        return new BoadcastsAdapter(getActivity(), R.layout.list_item_broadcast);
    }

    @Override
    protected RecyclerViewEmptyViewSupport.OnItemClickListener createOnItemClickListener() {
        return new RecyclerViewEmptyViewSupport.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
            }
        };
    }

    /**
     * Create a new instance of this, initialized to show the current channel
     */
    public static PVRChannelEPGListFragment newInstance(Integer channelId) {
        PVRChannelEPGListFragment fragment = new PVRChannelEPGListFragment();

        Bundle args = new Bundle();
        args.putInt(BUNDLE_KEY_CHANNELID, channelId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        Bundle bundle = getArguments();
        channelId = bundle.getInt(BUNDLE_KEY_CHANNELID, -1);

        if (channelId == -1) {
            // There's nothing to show
            return null;
        }

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
        browseEPG();
    }


    /**
     * Swipe refresh layout callback
     */
    /** {@inheritDoc} */
    @Override
    public void onRefresh () {
        if (hostManager.getHostInfo() != null) {
            browseEPG();
        } else {
            hideRefreshAnimation();
            Toast.makeText(getActivity(), R.string.no_xbmc_configured, Toast.LENGTH_SHORT)
                 .show();
        }
    }

    @Override
    protected void refreshList() {
        onRefresh();
    }

    /**
     * Get the EPF for the channel and setup the listview
     */
    private void browseEPG() {
        PVR.GetBroadcasts action = new PVR.GetBroadcasts(channelId, PVRType.FieldsBroadcast.allValues);
        action.execute(hostManager.getConnection(), new ApiCallback<List<PVRType.DetailsBroadcast>>() {
            @Override
            public void onSuccess(List<PVRType.DetailsBroadcast> result) {
                if (!isAdded()) return;
                // To prevent the empty text from appearing on the first load, set it now
                getEmptyView().setText(getString(R.string.no_broadcasts_found_refresh));

                List<PVRType.DetailsBroadcast> finalResult = filter(result);
                ((BoadcastsAdapter) getAdapter()).setItems(
                        EPGListRow.buildFromBroadcastList(result));
            }

            @Override
            public void onError(int errorCode, String description) {
                if (!isAdded()) return;
                LogUtils.LOGD(TAG, "Error getting broadcasts: " + description);
                // To prevent the empty text from appearing on the first load, set it now
                getEmptyView().setText(String.format(getString(R.string.error_getting_pvr_info), description));
                Toast.makeText(getActivity(),
                               String.format(getString(R.string.error_getting_pvr_info), description),
                               Toast.LENGTH_SHORT).show();
            }
        }, callbackHandler);
    }


    private List<PVRType.DetailsBroadcast> filter(List<PVRType.DetailsBroadcast> itemList) {
        String searchFilter = getSearchFilter();

        if (TextUtils.isEmpty(searchFilter)) {
            return itemList;
        }

        // Split searchFilter to multiple lowercase words
        String[] lcWords = searchFilter.toLowerCase().split(" ");;

        List<PVRType.DetailsBroadcast> result = new ArrayList<>(itemList.size());
        for (PVRType.DetailsBroadcast item:itemList) {
            // Require all words to match the item:
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

            result.add(item);
        }

        return result;
    }

    public boolean searchFilterWordMatches(String lcWord, PVRType.DetailsBroadcast item) {
        if (item.title.toLowerCase().contains(lcWord)) {
            return true;
        }
        if (item.plot.toLowerCase().contains(lcWord)){
            return true;
        }
        return false;
    }

    private class BoadcastsAdapter extends RecyclerView.Adapter {
        Context ctx;
        int resource;
        List<EPGListRow> items;

        public BoadcastsAdapter(Context context, int resource) {
            super();
            this.ctx = context;
            this.resource = resource;
            this.items = null;
        }

        public void setItems(List<EPGListRow> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        public EPGListRow getItem(int position) {
            if (items == null || position < 0 || position >= items.size()) {
                return null;
            }
            return items.get(position);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(ctx)
                                      .inflate(resource, parent, false);
            return new ViewHolder(view, ctx);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            EPGListRow item = this.getItem(position);
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
        TextView title;
        TextView details;
        TextView start_time;
        TextView end_time;
        TextView day;
        View separator;

        Context context;

        ViewHolder(View itemView, Context context) {
            super(itemView);
            this.context = context;
            title = itemView.findViewById(R.id.title);
            details = itemView.findViewById(R.id.details);
            start_time = itemView.findViewById(R.id.start_time);
            end_time = itemView.findViewById(R.id.end_time);
            day = itemView.findViewById(R.id.day);
            separator = itemView.findViewById(R.id.separator);
        }

        public void bindView(EPGListRow item, int position) {
            String st;
            if (item.rowType == EPGListRow.TYPE_DAY) {
                st = DateUtils.formatDateTime(context, item.date.getTime(),
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY);
                day.setText(st);
                details.setVisibility(View.GONE);
                start_time.setVisibility(View.GONE);
                end_time.setVisibility(View.GONE);
                title.setVisibility(View.GONE);
                day.setVisibility(View.VISIBLE);
                separator.setVisibility(View.VISIBLE);
            } else {
                day.setVisibility(View.GONE);
                separator.setVisibility(View.GONE);
                PVRType.DetailsBroadcast broadcastDetails = item.detailsBroadcast;

                title.setText(UIUtils.applyMarkup(context, broadcastDetails.title));
                details.setText(UIUtils.applyMarkup(context, broadcastDetails.plot));
                String duration = context.getString(R.string.minutes_abbrev2,
                                                    String.valueOf(broadcastDetails.runtime));

                st = DateUtils.formatDateTime(context,
                        broadcastDetails.starttime.getTime(),
                        DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_TIME);
                start_time.setText(st);
                end_time.setText(duration);
                details.setVisibility(View.VISIBLE);
                start_time.setVisibility(View.VISIBLE);
                end_time.setVisibility(View.VISIBLE);
                title.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * Class that represents a row in the EPG list
     * Can either represent a day or a broadcast
     */
    private static class EPGListRow {
        static final int TYPE_DAY = 0,
                TYPE_BROADCAST = 1;

        public int rowType;
        public Date date;
        public PVRType.DetailsBroadcast detailsBroadcast;

        public EPGListRow(PVRType.DetailsBroadcast detailsBroadcast) {
            this.rowType = TYPE_BROADCAST;
            this.detailsBroadcast = detailsBroadcast;
        }

        public EPGListRow(Date date) {
            this.rowType = TYPE_DAY;
            this.date = date;
        }

        /**
         * Build the list of rows to show
         * @param broadcasts Broadcast list returned. Assuming it is ordered by date
         * @return List of rows to show
         */
        public static List<EPGListRow> buildFromBroadcastList(List<PVRType.DetailsBroadcast> broadcasts) {
            Date currentTime = new Date();
            int previousDayIdx = 0, dayIdx;
            Calendar cal = Calendar.getInstance();

            List<EPGListRow> result = new ArrayList<>(broadcasts.size() + 5);

            for (PVRType.DetailsBroadcast broadcast: broadcasts) {
                // Ignore if before current time
                if (broadcast.endtime.before(currentTime)) {
                    continue;
                }

                cal.setTime(broadcast.starttime);
                dayIdx = cal.get(Calendar.YEAR) * 366 + cal.get(Calendar.DATE);
                if (dayIdx > previousDayIdx) {
                    // New day, add a row representing it to the list
                    previousDayIdx = dayIdx;
                    result.add(new EPGListRow(broadcast.starttime));
                }
                result.add(new EPGListRow(broadcast));
            }
            return result;
        }
    }
}

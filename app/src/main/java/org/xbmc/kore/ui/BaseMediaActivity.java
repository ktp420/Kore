/*
 * Copyright 2017 Martijn Brekhof. All rights reserved.
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
package org.xbmc.kore.ui;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.transition.TransitionInflater;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.text.TextDirectionHeuristicsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import org.xbmc.kore.R;
import org.xbmc.kore.Settings;
import org.xbmc.kore.host.HostConnectionObserver;
import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.jsonrpc.ApiCallback;
import org.xbmc.kore.jsonrpc.ApiMethod;
import org.xbmc.kore.jsonrpc.HostConnection;
import org.xbmc.kore.jsonrpc.method.Application;
import org.xbmc.kore.jsonrpc.method.Input;
import org.xbmc.kore.jsonrpc.method.Player;
import org.xbmc.kore.jsonrpc.method.System;
import org.xbmc.kore.jsonrpc.type.ListType;
import org.xbmc.kore.jsonrpc.type.PlayerType;
import org.xbmc.kore.ui.generic.NavigationDrawerFragment;
import org.xbmc.kore.ui.generic.SendTextDialogFragment;
import org.xbmc.kore.ui.generic.VolumeControllerDialogFragmentListener;
import org.xbmc.kore.ui.sections.hosts.AddHostActivity;
import org.xbmc.kore.ui.sections.remote.RemoteActivity;
import org.xbmc.kore.ui.widgets.MediaProgressIndicator;
import org.xbmc.kore.ui.widgets.NowPlayingPanel;
import org.xbmc.kore.ui.widgets.VolumeLevelIndicator;
import org.xbmc.kore.utils.LogUtils;
import org.xbmc.kore.utils.SharedElementTransition;
import org.xbmc.kore.utils.UIUtils;
import org.xbmc.kore.utils.Utils;

import butterknife.BindView;
import butterknife.ButterKnife;

import java.util.concurrent.TimeUnit;

public abstract class BaseMediaActivity extends BaseActivity
        implements HostConnectionObserver.ApplicationEventsObserver,
                   HostConnectionObserver.PlayerEventsObserver,
                   NowPlayingPanel.OnPanelButtonsClickListener,
                   MediaProgressIndicator.OnProgressChangeListener,
                   SendTextDialogFragment.SendTextDialogListener {
    private static final String TAG = LogUtils.makeLogTag(BaseMediaActivity.class);

    private static final String NAVICON_ISARROW = "navstate";
    private static final String ACTIONBAR_TITLE = "actionbartitle";

    @BindView(R.id.now_playing_panel) NowPlayingPanel nowPlayingPanel;

    private NavigationDrawerFragment navigationDrawerFragment;
    private SharedElementTransition sharedElementTransition = new SharedElementTransition();

    private boolean drawerIndicatorIsArrow;
    private int currentActivePlayerId = -1;

    private HostManager hostManager;
    private HostConnectionObserver hostConnectionObserver;

    private boolean showNowPlayingPanel;

    protected abstract String getActionBarTitle();
    protected abstract Fragment createFragment();

    /**
     * Default callback for methods that don't return anything
     */
    private ApiCallback<String> defaultStringActionCallback = ApiMethod.getDefaultActionCallback();
    private Handler callbackHandler = new Handler();
    private ApiCallback<Integer> defaultIntActionCallback = ApiMethod.getDefaultActionCallback();

    private Runnable hidePanelRunnable = new Runnable() {
        @Override
        public void run() {
            nowPlayingPanel.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
        }
    };

    @Override
    @TargetApi(21)
    protected void onCreate(Bundle savedInstanceState) {
        // Request transitions on lollipop
        if (Utils.isLollipopOrLater()) {
            getWindow().requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);
        }
        super.onCreate(savedInstanceState);

        // Set default values for the preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        setContentView(R.layout.activity_generic_media);
        ButterKnife.bind(this);

        hostManager = HostManager.getInstance(this);

        // Check if we have any hosts setup
        if (hostManager.getHostInfo() == null) {
            final Intent intent = new Intent(this, AddHostActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // Set up the drawer.
        navigationDrawerFragment = (NavigationDrawerFragment)getSupportFragmentManager()
                .findFragmentById(R.id.navigation_drawer);
        navigationDrawerFragment.setUp(R.id.navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout));

        Toolbar toolbar = findViewById(R.id.default_toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            String actionBarTitle;
            boolean naviconIsArrow = false;
            if (savedInstanceState != null) {
                actionBarTitle = savedInstanceState.getString(ACTIONBAR_TITLE);
                naviconIsArrow = savedInstanceState.getBoolean(NAVICON_ISARROW);
            } else {
                actionBarTitle = getActionBarTitle();
            }

            actionBar.setDisplayHomeAsUpEnabled(true);
            updateActionBar(actionBarTitle, naviconIsArrow);
        }

        String fragmentTitle = getActionBarTitle();
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment == null) {
            fragment = createFragment();

            if (Utils.isLollipopOrLater()) {
                fragment.setExitTransition(null);
                fragment.setReenterTransition(TransitionInflater
                                                      .from(this)
                                                      .inflateTransition(android.R.transition.fade));
            }

            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragment_container, fragment, fragmentTitle)
                    .commit();
        }

        if (Utils.isLollipopOrLater()) {
            sharedElementTransition.setupExitTransition(this, fragment);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(NAVICON_ISARROW, drawerIndicatorIsArrow);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            CharSequence title = actionBar.getTitle();
            if (title != null) {
                outState.putString(ACTIONBAR_TITLE, title.toString());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        showNowPlayingPanel = PreferenceManager.getDefaultSharedPreferences(this)
                                               .getBoolean(Settings.KEY_PREF_SHOW_NOW_PLAYING_PANEL,
                                                           Settings.DEFAULT_PREF_SHOW_NOW_PLAYING_PANEL);

        if(showNowPlayingPanel) {
            setupNowPlayingPanel();
        } else {
            //Hide it in case we were displaying the panel and user disabled showing
            //the panel in Settings
            nowPlayingPanel.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        if(!showNowPlayingPanel)
            return;

        hostConnectionObserver = hostManager.getHostConnectionObserver();
        if (hostConnectionObserver == null)
            return;

        hostConnectionObserver.unregisterApplicationObserver(this);
        hostConnectionObserver.unregisterPlayerObserver(this);
    }

    /**
     * Override hardware volume keys and send to Kodi
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean handled = VolumeControllerDialogFragmentListener.handleVolumeKeyEvent(this, event);
        if (handled) {
            new VolumeControllerDialogFragmentListener()
                    .show(getSupportFragmentManager(), VolumeControllerDialogFragmentListener.class.getName());
        }
        return handled || super.dispatchKeyEvent(event);
    }

    public boolean getDrawerIndicatorIsArrow() {
        return drawerIndicatorIsArrow;
    }

    /**
     * Sets the title and drawer indicator of the toolbar
     * @param title toolbar title
     * @param showArrowIndicator true if the toolbar should show the back arrow indicator,
     *                               false if it should show the drawer icon
     */
    protected void updateActionBar(String title, boolean showArrowIndicator) {
        if (showArrowIndicator != drawerIndicatorIsArrow) {
            navigationDrawerFragment.animateDrawerToggle(showArrowIndicator);
            drawerIndicatorIsArrow = showArrowIndicator;
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setTitle(title);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.media_info, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_show_remote:
                Intent launchIntent = new Intent(this, RemoteActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(launchIntent);
                return true;
            case R.id.action_wake_up:
                UIUtils.sendWolAsync(this, hostManager.getHostInfo());
                return true;
            case R.id.action_quit:
                Application.Quit actionQuit = new Application.Quit();
                // Fire and forget
                actionQuit.execute(hostManager.getConnection(), null, null);
                return true;
            case R.id.action_suspend:
                System.Suspend actionSuspend = new System.Suspend();
                // Fire and forget
                actionSuspend.execute(hostManager.getConnection(), null, null);
                return true;
            case R.id.action_reboot:
                System.Reboot actionReboot = new System.Reboot();
                // Fire and forget
                actionReboot.execute(hostManager.getConnection(), null, null);
                return true;
            case R.id.action_shutdown:
                System.Shutdown actionShutdown = new System.Shutdown();
                // Fire and forget
                actionShutdown.execute(hostManager.getConnection(), null, null);
                return true;
            case R.id.send_text:
                inputOnInputRequested(getString(R.string.send_text), null, null);
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @TargetApi(21)
    protected void showFragment(AbstractFragment fragment, ImageView sharedImageView, AbstractFragment.DataHolder dataHolder) {
        FragmentTransaction fragTrans = getSupportFragmentManager().beginTransaction();

        // Set up transitions
        if (Utils.isLollipopOrLater()) {
            dataHolder.setPosterTransitionName(sharedImageView.getTransitionName());
            sharedElementTransition.setupEnterTransition(this, fragTrans, fragment, sharedImageView);
        } else {
            fragTrans.setCustomAnimations(R.anim.fragment_details_enter, 0,
                                          R.anim.fragment_list_popenter, 0);
        }

        fragTrans.replace(R.id.fragment_container, fragment, getActionBarTitle())
                 .addToBackStack(null)
                 .commit();
    }

    @Override
    public void applicationOnVolumeChanged(int volume, boolean muted) {
        nowPlayingPanel.setVolume(volume, muted);
    }

    @Override
    public void playerOnPropertyChanged(org.xbmc.kore.jsonrpc.notification.Player.NotificationsData notificationsData) {
        if (notificationsData.property.shuffled != null)
            nowPlayingPanel.setShuffled(notificationsData.property.shuffled);

        if (notificationsData.property.repeatMode != null )
            nowPlayingPanel.setRepeatMode(notificationsData.property.repeatMode);
    }

    @Override
    public void playerOnPlay(PlayerType.GetActivePlayersReturnType getActivePlayerResult,
                             PlayerType.PropertyValue getPropertiesResult,
                             ListType.ItemsAll getItemResult) {
        currentActivePlayerId = getActivePlayerResult.playerid;

        updateNowPlayingPanel(getPropertiesResult, getItemResult);
    }

    @Override
    public void playerOnPause(PlayerType.GetActivePlayersReturnType getActivePlayerResult, PlayerType.PropertyValue getPropertiesResult, ListType.ItemsAll getItemResult) {
        currentActivePlayerId = getActivePlayerResult.playerid;

        updateNowPlayingPanel(getPropertiesResult, getItemResult);
    }

    @Override
    public void playerOnStop() {
        currentActivePlayerId = -1;
        //We delay hiding the panel to prevent hiding the panel when playing
        // the next item in a playlist
        callbackHandler.removeCallbacks(hidePanelRunnable);
        callbackHandler.postDelayed(hidePanelRunnable, 1000);
    }

    @Override
    public void playerOnConnectionError(int errorCode, String description) {

    }

    @Override
    public void playerNoResultsYet() {
    }

    @Override
    public void observerOnStopObserving() {
        nowPlayingPanel.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
    }

    @Override
    public void systemOnQuit() {
        nowPlayingPanel.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
    }

    @Override
    public void onProgressChanged(int progress) {
        PlayerType.PositionTime positionTime = new PlayerType.PositionTime(progress);
        Player.Seek seekAction = new Player.Seek(currentActivePlayerId, positionTime);
        seekAction.execute(HostManager.getInstance(this).getConnection(), new ApiCallback<PlayerType.SeekReturnType>() {
            @Override
            public void onSuccess(PlayerType.SeekReturnType result) {
                // Ignore
            }

            @Override
            public void onError(int errorCode, String description) {
                LogUtils.LOGE(TAG, "Got an error calling Player.Seek. Error code: " + errorCode + ", description: " + description);
            }
        }, new Handler());
    }

    @Override
    public void onPlayClicked() {
        Player.PlayPause action = new Player.PlayPause(currentActivePlayerId);
        action.execute(hostManager.getConnection(), defaultIntActionCallback, callbackHandler);
    }

    @Override
    public void onPreviousClicked() {
        Player.GoTo action = new Player.GoTo(currentActivePlayerId, Player.GoTo.PREVIOUS);
        action.execute(hostManager.getConnection(), defaultStringActionCallback, callbackHandler);
    }

    @Override
    public void onNextClicked() {
        Player.GoTo action = new Player.GoTo(currentActivePlayerId, Player.GoTo.NEXT);
        action.execute(hostManager.getConnection(), defaultStringActionCallback, callbackHandler);
    }

    @Override
    public void onVolumeMuteClicked() {
        Application.SetMute action = new Application.SetMute();
        action.execute(hostManager.getConnection(), new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                //We depend on the listener to correct the mute button state
            }

            @Override
            public void onError(int errorCode, String description) { }
        }, new Handler());
    }

    @Override
    public void onShuffleClicked() {
        Player.SetShuffle action = new Player.SetShuffle(currentActivePlayerId);
        action.execute(hostManager.getConnection(), new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                //We depend on the listener to correct the mute button state
            }

            @Override
            public void onError(int errorCode, String description) { }
        }, callbackHandler);
    }

    @Override
    public void onRepeatClicked() {
        Player.SetRepeat action = new Player.SetRepeat(currentActivePlayerId, PlayerType.Repeat.CYCLE);
        action.execute(hostManager.getConnection(), new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                //We depend on the listener to correct the mute button state
            }

            @Override
            public void onError(int errorCode, String description) { }
        }, callbackHandler);
    }

    @Override
    public void onVolumeMutedIndicatorClicked() {
        Application.SetMute action = new Application.SetMute();
        action.execute(hostManager.getConnection(), new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                //We depend on the listener to correct the mute button state
            }

            @Override
            public void onError(int errorCode, String description) { }
        }, new Handler());
    }

    private void setupNowPlayingPanel() {
        nowPlayingPanel.setOnVolumeChangeListener(new VolumeLevelIndicator.OnVolumeChangeListener() {
            @Override
            public void onVolumeChanged(int volume) {
                new Application.SetVolume(volume)
                        .execute(hostManager.getConnection(), defaultIntActionCallback, new Handler());
            }
        });

        nowPlayingPanel.setOnPanelButtonsClickListener(this);
        nowPlayingPanel.setOnProgressChangeListener(this);

        hostConnectionObserver = hostManager.getHostConnectionObserver();
        if (hostConnectionObserver == null)
            return;

        hostConnectionObserver.registerApplicationObserver(this);
        hostConnectionObserver.registerPlayerObserver(this);

        hostConnectionObserver.refreshWhatsPlaying();
    }

    private void updateNowPlayingPanel(PlayerType.PropertyValue getPropertiesResult,
                                       ListType.ItemsAll getItemResult) {
        String title;
        String poster;
        String details = null;

        callbackHandler.removeCallbacks(hidePanelRunnable);

        // Only set state to collapsed if panel is currently hidden. This prevents collapsing
        // the panel when the user expanded the panel and started playing the item from a paused
        // state
        if (nowPlayingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.HIDDEN) {
            nowPlayingPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
        }

        nowPlayingPanel.setMediaProgress(getPropertiesResult.time, getPropertiesResult.totaltime);

        nowPlayingPanel.setPlayButton(getPropertiesResult.speed > 0);
        nowPlayingPanel.setShuffled(getPropertiesResult.shuffled);
        nowPlayingPanel.setRepeatMode(getPropertiesResult.repeat);
        nowPlayingPanel.setSpeed(getPropertiesResult.speed);

        switch (getItemResult.type) {
            case ListType.ItemsAll.TYPE_MOVIE:
                title = getItemResult.title;
                details = getItemResult.tagline;
                poster = TextUtils.isEmpty(getItemResult.thumbnail) ? getItemResult.fanart
                                                                    : getItemResult.thumbnail;
                break;
            case ListType.ItemsAll.TYPE_EPISODE:
                title = getItemResult.title;
                String seasonEpisode = String.format(getString(R.string.season_episode_abbrev),
                                                     getItemResult.season, getItemResult.episode);
                details = String.format("%s | %s", getItemResult.showtitle, seasonEpisode);
                poster = TextUtils.isEmpty(getItemResult.art.poster) ? getItemResult.art.fanart
                                                                     : getItemResult.art.poster;
                break;
            case ListType.ItemsAll.TYPE_SONG:
                title = getItemResult.title;
                details = getItemResult.displayartist + " | " + getItemResult.album;
                poster = TextUtils.isEmpty(getItemResult.thumbnail) ? getItemResult.fanart
                                                                    : getItemResult.thumbnail;
                break;
            case ListType.ItemsAll.TYPE_MUSIC_VIDEO:
                title = getItemResult.title;
                details = Utils.listStringConcat(getItemResult.artist, ", ") + " | " + getItemResult.album;
                poster = TextUtils.isEmpty(getItemResult.thumbnail) ? getItemResult.fanart
                                                                    : getItemResult.thumbnail;
                break;
            case ListType.ItemsAll.TYPE_CHANNEL:
                title = getItemResult.label;
                details = getItemResult.title;
                poster = TextUtils.isEmpty(getItemResult.thumbnail) ? getItemResult.fanart
                                                                    : getItemResult.thumbnail;
                break;
            default:
                title = getItemResult.label;
                poster = TextUtils.isEmpty(getItemResult.thumbnail) ? getItemResult.fanart
                                                                    : getItemResult.thumbnail;
                break;
        }

        if (title.contentEquals(nowPlayingPanel.getTitle()))
            return; // Still showing same item as previous call

        nowPlayingPanel.setTitle(title);

        if (details != null) {
            nowPlayingPanel.setDetails(details);
        }

        if ((getItemResult.type.contentEquals(ListType.ItemsAll.TYPE_MUSIC_VIDEO)) ||
            (getItemResult.type.contentEquals(ListType.ItemsAll.TYPE_SONG))) {
            nowPlayingPanel.setNextPrevVisibility(View.VISIBLE);
        } else {
            nowPlayingPanel.setNextPrevVisibility(View.GONE);
        }

        Resources resources = getResources();
        int posterWidth = resources.getDimensionPixelOffset(R.dimen.now_playing_panel_art_width);
        int posterHeight = resources.getDimensionPixelOffset(R.dimen.now_playing_panel_height);

        // If not video, change aspect ration of poster to a square
        boolean isVideo = (getItemResult.type.equals(ListType.ItemsAll.TYPE_MOVIE)) ||
                          (getItemResult.type.equals(ListType.ItemsAll.TYPE_EPISODE));

        nowPlayingPanel.setSquarePoster(!isVideo);

        UIUtils.loadImageWithCharacterAvatar(this, hostManager, poster, title,
                                             nowPlayingPanel.getPoster(),
                                             (isVideo) ? posterWidth : posterHeight, posterHeight);
    }

    public void show_send_dialog(String title) {
        if (hostManager.getHostInfo().getProtocol() == HostConnection.PROTOCOL_HTTP) {
            inputOnInputRequested(title, null, null);
        }
    }

    @Override
    public void inputOnInputRequested(String title, String type, String value) {
        final SendTextDialogFragment dialog =
                SendTextDialogFragment.newInstance(title);
        dialog.show(getSupportFragmentManager(), null);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TimeUnit.MILLISECONDS.sleep(HostConnection.TCP_READ_TIMEOUT - 2000);
                    if (dialog != null && dialog.getDialog() != null 
                            && dialog.getDialog().isShowing() && !dialog.isRemoving()) {
                        dialog.dismissAllowingStateLoss();
                        sendTextInput("kore_dummy_input", true, true);
                    }
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        });
        t.start();
    }

    /**
     * Callbacks from Send text dialog
     */
    @Override
    public void onSendTextFinished(String text, boolean done) {
        if (TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR.isRtl(text, 0, text.length())) {
            text = new StringBuilder(text).reverse().toString();
        }
        sendTextInput(text,done, false);
    }

    @Override
    public void onSendTextCancel() {
        sendTextInput("kore_dummy_input", true, true);
    }

    private void sendTextInput(String text, boolean done, boolean isDummy) {
        // HostManager hostManager = HostManager.getInstance(this);
        // hostManager.getConnection().setIgnoreTcpResponse(isDummy);

        // HostConnection httpHostConnection = new HostConnection(hostManager.getHostInfo());
        // httpHostConnection.setProtocol(HostConnection.PROTOCOL_HTTP);

        Input.SendText action = new Input.SendText(text, done);
        action.execute(hostManager.getConnection(), null, null);
    }
}

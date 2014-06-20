/*
 * Copyright (c) 2014 Amahi
 *
 * This file is part of Amahi.
 *
 * Amahi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Amahi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Amahi. If not, see <http ://www.gnu.org/licenses/>.
 */

package org.amahi.anywhere.activity;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.ViewAnimator;

import com.squareup.otto.Subscribe;
import com.squareup.picasso.Picasso;

import org.amahi.anywhere.AmahiApplication;
import org.amahi.anywhere.R;
import org.amahi.anywhere.bus.AudioMetadataRetrievedEvent;
import org.amahi.anywhere.bus.BusProvider;
import org.amahi.anywhere.server.client.ServerClient;
import org.amahi.anywhere.server.model.ServerFile;
import org.amahi.anywhere.server.model.ServerShare;
import org.amahi.anywhere.task.AudioMetadataRetrievingTask;
import org.amahi.anywhere.util.AudioAlbumArtDownloader;
import org.amahi.anywhere.util.Intents;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

public class ServerFileAudioActivity extends Activity implements MediaController.MediaPlayerControl, MediaPlayer.OnPreparedListener
{
	public static final Set<String> SUPPORTED_FORMATS;

	static {
		SUPPORTED_FORMATS = new HashSet<String>(Arrays.asList(
			"audio/flac",
			"audio/mp4",
			"audio/mpeg",
			"audio/ogg"
		));
	}

	private static final class SavedState
	{
		private SavedState() {
		}

		public static final String AUDIO_TIME = "audio_time";
	}

	@Inject
	ServerClient serverClient;

	private MediaPlayer audioPlayer;

	private MediaController audioControls;

	private int audioTime;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_server_file_audio);

		setUpSavedState(savedInstanceState);

		setUpInjections();

		setUpHomeNavigation();

		setUpAudio();
	}

	private void setUpSavedState(Bundle savedState) {
		if (savedState == null) {
			return;
		}

		audioTime = savedState.getInt(SavedState.AUDIO_TIME);
	}

	private void setUpInjections() {
		AmahiApplication.from(this).inject(this);
	}

	private void setUpHomeNavigation() {
		getActionBar().setHomeButtonEnabled(true);
	}

	private void setUpAudio() {
		setUpAudioTitle();
		setUpAudioMetadata();
		setUpAudioAlbumArt();
	}

	private void setUpAudioTitle() {
		getActionBar().setTitle(getFile().getName());
	}

	private ServerFile getFile() {
		return getIntent().getParcelableExtra(Intents.Extras.SERVER_FILE);
	}

	private void setUpAudioMetadata() {
		AudioMetadataRetrievingTask.execute(getAudioUri());
	}

	private Uri getAudioUri() {
		return serverClient.getFileUri(getShare(), getFile());
	}

	private ServerShare getShare() {
		return getIntent().getParcelableExtra(Intents.Extras.SERVER_SHARE);
	}

	@Subscribe
	public void onAudioMetadataRetrieved(AudioMetadataRetrievedEvent event) {
		setUpAudioMetadata(event.getAudioTitle(), event.getAudioArtist(), event.getAudioAlbum());
	}

	private void setUpAudioMetadata(String audioTitle, String audioArtist, String audioAlbum) {
		TextView audioTitleView = (TextView) findViewById(R.id.text_title);
		TextView audioArtistView = (TextView) findViewById(R.id.text_artist);
		TextView audioAlbumView = (TextView) findViewById(R.id.text_album);

		audioTitleView.setText(audioTitle);
		audioArtistView.setText(audioArtist);
		audioAlbumView.setText(audioAlbum);
	}

	private void setUpAudioAlbumArt() {
		ImageView audioAlbumArtView = (ImageView) findViewById(R.id.image_album_art);

		Picasso picasso = new Picasso.Builder(this)
			.downloader(new AudioAlbumArtDownloader())
			.build();

		picasso
			.load(getAudioUri())
			.fit()
			.centerInside()
			.placeholder(android.R.color.darker_gray)
			.into(audioAlbumArtView);
	}

	@Override
	protected void onStart() {
		super.onStart();

		setUpAudioPlayer();
		setUpAudioControls();
	}

	private void setUpAudioPlayer() {
		try {
			audioPlayer = new MediaPlayer();
			audioPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			audioPlayer.setDataSource(this, getAudioUri());
			audioPlayer.setOnPreparedListener(this);
			audioPlayer.prepareAsync();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void setUpAudioControls() {
		audioControls = new MediaController(this);

		audioControls.setMediaPlayer(this);
		audioControls.setAnchorView(findViewById(R.id.animator));
	}

	@Override
	public void onPrepared(MediaPlayer mediaPlayer) {
		setUpAudioTime();
		start();

		showAudio();
		showAudioControls();
	}

	private void setUpAudioTime() {
		if (audioPlayer.getCurrentPosition() == 0) {
			audioPlayer.seekTo(audioTime);
		}
	}

	private void showAudio() {
		ViewAnimator animator = (ViewAnimator) findViewById(R.id.animator);

		View content = findViewById(R.id.layout_content);

		if (animator.getDisplayedChild() != animator.indexOfChild(content)) {
			animator.setDisplayedChild(animator.indexOfChild(content));
		}
	}

	private void showAudioControls() {
		Animation showAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_up_view);
		audioControls.startAnimation(showAnimation);

		audioControls.show(0);
	}

	@Override
	public void start() {
		audioPlayer.start();
	}

	@Override
	public boolean canPause() {
		return true;
	}

	@Override
	public void pause() {
		audioPlayer.pause();
	}

	@Override
	public boolean canSeekBackward() {
		return true;
	}

	@Override
	public boolean canSeekForward() {
		return true;
	}

	@Override
	public void seekTo(int time) {
		audioPlayer.seekTo(time);
	}

	@Override
	public int getDuration() {
		return audioPlayer.getDuration();
	}

	@Override
	public int getCurrentPosition() {
		return audioPlayer.getCurrentPosition();
	}

	@Override
	public boolean isPlaying() {
		return audioPlayer.isPlaying();
	}

	@Override
	public int getBufferPercentage() {
		return 0;
	}

	@Override
	public int getAudioSessionId() {
		return audioPlayer.getAudioSessionId();
	}

	@Override
	protected void onResume() {
		super.onResume();

		BusProvider.getBus().register(this);

		audioPlayer.start();
	}

	@Override
	protected void onPause() {
		super.onPause();

		BusProvider.getBus().unregister(this);

		hideAudioControls();
	}

	private void hideAudioControls() {
		audioControls.hide();
	}

	@Override
	protected void onStop() {
		super.onStop();

		audioTime = getCurrentPosition();

		tearDownAudioPlayer();
	}

	private void tearDownAudioPlayer() {
		audioPlayer.stop();
		audioPlayer.release();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		tearDownSavedState(outState);
	}

	private void tearDownSavedState(Bundle savedState) {
		savedState.putInt(SavedState.AUDIO_TIME, getCurrentPosition());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		switch (menuItem.getItemId()) {
			case android.R.id.home:
				finish();
				return true;

			default:
				return super.onOptionsItemSelected(menuItem);
		}
	}
}

package com.wj.speex;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.wj.speex.R;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * @Title:
 * @Package
 * @Description:
 * @author wangjiang wangjiang7747@gmail.com
 * @date 2016-4-16 下午4:00:45
 * @version V1.0
 */
public class MainActivity extends Activity {

	private static final String TAG = "TAG";
	private Button mStart;
	private Button mStop;
	private Button mPlay;

	private TextView mTest;

	private File mAudioFile;
	private AudioRecord mAudioRecord;
	private AudioTrack mAudioTrack;

	private Speex speex;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		init();

		registerListener();
	}

	private List<Data> mDatas = new ArrayList<Data>();

	private void registerListener() {

		mStart.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
				new Thread(new Runnable() {
					public void run() {
						try {
							mAudioRecord.startRecording();
							DataOutputStream dos = new DataOutputStream(
									new BufferedOutputStream(
											new FileOutputStream(mAudioFile)));
							int sizeInShorts = speex.getFrameSize();
							short[] audioData = new short[sizeInShorts];
							int sizeInBytes = speex.getFrameSize();
							while (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
								int number = mAudioRecord.read(audioData, 0,
										sizeInShorts);
								short[] dst = new short[sizeInBytes];
								System.arraycopy(audioData, 0, dst, 0, number);
								byte[] encoded = new byte[sizeInBytes];
								int count = speex.encode(dst, 0, encoded,
										number);
								if (count > 0) {
									Data data = new Data();
									data.mSize = count;
									data.mBuffer = encoded;
									mDatas.add(data);
									dos.write(encoded, 0, count);
								}
							}
							dos.flush();
							dos.close();
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}).start();
			}
		});

		mStop.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				if (mAudioRecord != null
						&& mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
					mAudioRecord.stop();
					// speex.close();
				}
			}
		});

		mPlay.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				new Thread(new Runnable() {
					public void run() {
						try {
							DataInputStream dis = new DataInputStream(
									new BufferedInputStream(
											new FileInputStream(mAudioFile)));
							int len = 0;
							for (Data data : mDatas) {
								byte[] encoded = new byte[data.mSize];
								len = dis.read(encoded, 0, data.mSize);
								if (len != -1) {
									short[] lin = new short[speex
											.getFrameSize()];
									int size = speex.decode(encoded, lin,
											encoded.length);
									if (size > 0) {
										mAudioTrack.write(lin, 0, size);
										mAudioTrack.play();
									}
								}
							}
							// short[] lin = new short[speex.getFrameSize()];
							// for (Data data : mDatas) {
							// int size = speex.decode(data.mBuffer, lin,
							// data.mSize);
							// if (size > 0) {
							// mAudioTrack.write(lin, 0, size);
							// mAudioTrack.play();
							// }
							// }
							dis.close();
							speex.close();
							mAudioTrack.stop();
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}).start();
			}
		});

	}

	private void init() {

		mStart = (Button) findViewById(R.id.start);
		mStop = (Button) findViewById(R.id.stop);
		mPlay = (Button) findViewById(R.id.play);

		mTest = (TextView) findViewById(R.id.test);
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			File file = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath() + "/audio/");
			if (!file.exists()) {
				file.mkdirs();
			}
			mAudioFile = new File(file, System.currentTimeMillis() + ".spx");
		}

		try {
			int sampleRateInHz = 8000;
			int recordBufferSizeInBytes = AudioRecord.getMinBufferSize(
					sampleRateInHz, AudioFormat.CHANNEL_IN_MONO,
					AudioFormat.ENCODING_PCM_16BIT);
			Log.d(TAG, "recordBufferSizeInBytes=" + recordBufferSizeInBytes);

			mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
					sampleRateInHz, AudioFormat.CHANNEL_IN_MONO,
					AudioFormat.ENCODING_PCM_16BIT, recordBufferSizeInBytes);

			int trackBufferSizeInBytes = AudioTrack.getMinBufferSize(
					sampleRateInHz, AudioFormat.CHANNEL_OUT_MONO,
					AudioFormat.ENCODING_PCM_16BIT);

			Log.d(TAG, "trackBufferSizeInBytes" + trackBufferSizeInBytes);
			mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
					sampleRateInHz, AudioFormat.CHANNEL_OUT_MONO,
					AudioFormat.ENCODING_PCM_16BIT, trackBufferSizeInBytes,
					AudioTrack.MODE_STREAM);
			mAudioTrack.setStereoVolume(AudioTrack.getMinVolume(),
					AudioTrack.getMaxVolume());

			speex = new Speex();
			speex.init();

			mTest.setText("frameSize=" + speex.getFrameSize());
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}

	}

	@Override
	protected void onStop() {
		if (mAudioFile.exists()) {
			mAudioFile.delete();
		}
		if (mAudioRecord != null) {
			mAudioRecord.release();
			mAudioRecord = null;
		}
		if (mAudioTrack != null) {
			mAudioTrack.release();
			mAudioTrack = null;
		}
		super.onStop();
	}

	private static final class Data {
		private int mSize;
		private byte[] mBuffer;
	}
}

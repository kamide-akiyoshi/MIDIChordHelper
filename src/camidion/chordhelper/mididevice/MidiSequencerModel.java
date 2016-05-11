package camidion.chordhelper.mididevice;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoundedRangeModel;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import camidion.chordhelper.ButtonIcon;
import camidion.chordhelper.midieditor.SequenceTickIndex;
import camidion.chordhelper.midieditor.SequenceTrackListTableModel;

/**
 * MIDIシーケンサモデル
 */
public class MidiSequencerModel extends MidiTransceiverListModel implements BoundedRangeModel {
	/**
	 * MIDIシーケンサモデルを構築します。
	 * @param sequencer シーケンサーMIDIデバイス
	 * @param deviceModelList 親のMIDIデバイスモデルリスト
	 */
	public MidiSequencerModel(Sequencer sequencer, MidiTransceiverListModelList deviceModelList) {
		super(sequencer, deviceModelList);
	}
	/**
	 * このシーケンサーの再生スピード調整モデル
	 */
	public BoundedRangeModel speedSliderModel = new DefaultBoundedRangeModel(0, 0, -7, 7) {{
		addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				int val = getValue();
				getSequencer().setTempoFactor((float)(
					val == 0 ? 1.0 : Math.pow( 2.0, ((double)val)/12.0 )
				));
			}
		});
	}};
	/**
	 * MIDIシーケンサを返します。
	 * @return MIDIシーケンサ
	 */
	public Sequencer getSequencer() { return (Sequencer)device; }
	/**
	 * 開始終了アクション
	 */
	public StartStopAction startStopAction = new StartStopAction();
	/**
	 * 開始終了アクション
	 */
	class StartStopAction extends AbstractAction {
		private Map<Boolean,Icon> iconMap = new HashMap<Boolean,Icon>() {
			{
				put(Boolean.FALSE, new ButtonIcon(ButtonIcon.PLAY_ICON));
				put(Boolean.TRUE, new ButtonIcon(ButtonIcon.PAUSE_ICON));
			}
		};
		{
			putValue(
				SHORT_DESCRIPTION,
				"Start/Stop recording or playing - 録音または再生の開始／停止"
			);
			setRunning(false);
		}
		@Override
		public void actionPerformed(ActionEvent event) {
			if(timeRangeUpdater.isRunning()) stop(); else start();
		}
		/**
		 * 開始されているかどうかを設定します。
		 * @param isRunning 開始されていたらtrue
		 */
		public void setRunning(boolean isRunning) {
			putValue(LARGE_ICON_KEY, iconMap.get(isRunning));
			putValue(SELECTED_KEY, isRunning);
		}
	}
	/**
	 * シーケンサに合わせてミリ秒位置を更新するタイマー
	 */
	private javax.swing.Timer timeRangeUpdater = new javax.swing.Timer(
		20,
		new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				if( valueIsAdjusting || ! getSequencer().isRunning() ) {
					// 手動で移動中の場合や、シーケンサが止まっている場合は、
					// タイマーによる更新は不要
					return;
				}
				// リスナーに読み込みを促す
				fireStateChanged();
			}
		}
	);
	/**
	 * このモデルのMIDIシーケンサを開始します。
	 *
	 * <p>録音するMIDIチャンネルがMIDIエディタで指定されている場合、
	 * 録音スタート時のタイムスタンプが正しく０になるよう、
	 * 各MIDIデバイスのタイムスタンプをすべてリセットします。
	 * </p>
	 */
	public void start() {
		Sequencer sequencer = getSequencer();
		if( ! sequencer.isOpen() || sequencer.getSequence() == null ) {
			timeRangeUpdater.stop();
			startStopAction.setRunning(false);
			return;
		}
		startStopAction.setRunning(true);
		timeRangeUpdater.start();
		SequenceTrackListTableModel sequenceTableModel = getSequenceTrackListTableModel();
		if( sequenceTableModel != null && sequenceTableModel.hasRecordChannel() ) {
			for(MidiTransceiverListModel m : deviceModelList) m.resetMicrosecondPosition();
			System.gc();
			sequencer.startRecording();
		}
		else {
			System.gc();
			sequencer.start();
		}
		fireStateChanged();
	}
	/**
	 * このモデルのMIDIシーケンサを停止します。
	 */
	public void stop() {
		Sequencer sequencer = getSequencer();
		if(sequencer.isOpen()) sequencer.stop();
		timeRangeUpdater.stop();
		startStopAction.setRunning(false);
		fireStateChanged();
	}
	/**
	 * {@link Sequencer#getMicrosecondLength()} と同じです。
	 * @return マイクロ秒単位でのシーケンスの長さ
	 */
	public long getMicrosecondLength() {
		//
		// Sequencer.getMicrosecondLength() returns NEGATIVE value
		//  when over 0x7FFFFFFF microseconds (== 35.7913941166666... minutes),
		//  should be corrected when negative
		//
		long usLength = getSequencer().getMicrosecondLength();
		return usLength < 0 ? 0x100000000L + usLength : usLength ;
	}
	@Override
	public int getMaximum() { return (int)(getMicrosecondLength()/1000L); }
	@Override
	public void setMaximum(int newMaximum) {}
	@Override
	public int getMinimum() { return 0; }
	@Override
	public void setMinimum(int newMinimum) {}
	@Override
	public int getExtent() { return 0; }
	@Override
	public void setExtent(int newExtent) {}
	/**
	 * {@link Sequencer#getMicrosecondPosition()} と同じです。
	 * @return マイクロ秒単位での現在の位置
	 */
	public long getMicrosecondPosition() {
		long usPosition = getSequencer().getMicrosecondPosition();
		return usPosition < 0 ? 0x100000000L + usPosition : usPosition ;
	}
	@Override
	public int getValue() { return (int)(getMicrosecondPosition()/1000L); }
	@Override
	public void setValue(int newValue) {
		getSequencer().setMicrosecondPosition(1000L * (long)newValue);
		fireStateChanged();
	}
	/**
	 * 値調整中のときtrue
	 */
	private boolean valueIsAdjusting = false;
	@Override
	public boolean getValueIsAdjusting() { return valueIsAdjusting; }
	@Override
	public void setValueIsAdjusting(boolean valueIsAdjusting) {
		this.valueIsAdjusting = valueIsAdjusting;
	}
	@Override
	public void setRangeProperties(int value, int extent, int min, int max, boolean valueIsAdjusting) {
		getSequencer().setMicrosecondPosition(1000L * (long)value);
		setValueIsAdjusting(valueIsAdjusting);
		fireStateChanged();
	}
	/**
	 * イベントリスナーのリスト
	 */
	protected EventListenerList listenerList = new EventListenerList();
	/**
	 * {@inheritDoc}
	 * <p>このシーケンサーの再生時間位置変更通知を受けるリスナーを追加します。
	 * </p>
	 */
	@Override
	public void addChangeListener(ChangeListener listener) {
		listenerList.add(ChangeListener.class, listener);
	}
	/**
	 * {@inheritDoc}
	 * <p>このシーケンサーの再生時間位置変更通知を受けるリスナーを除去します。
	 * </p>
	 */
	@Override
	public void removeChangeListener(ChangeListener listener) {
		listenerList.remove(ChangeListener.class, listener);
	}
	/**
	 * 秒位置が変わったことをリスナーに通知します。
	 * <p>登録中のすべての {@link ChangeListener} について
	 * {@link ChangeListener#stateChanged(ChangeEvent)}
	 * を呼び出すことによって状態の変化を通知します。
	 * </p>
	 */
	public void fireStateChanged() {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==ChangeListener.class) {
				((ChangeListener)listeners[i+1]).stateChanged(new ChangeEvent(this));
			}
		}
	}
	/**
	 * MIDIトラックリストテーブルモデル
	 */
	private SequenceTrackListTableModel sequenceTableModel = null;
	/**
	 * このシーケンサーに現在ロードされているシーケンスのMIDIトラックリストテーブルモデルを返します。
	 * @return MIDIトラックリストテーブルモデル（何もロードされていなければnull）
	 */
	public SequenceTrackListTableModel getSequenceTrackListTableModel() {
		return sequenceTableModel;
	}
	/**
	 * MIDIトラックリストテーブルモデルを
	 * このシーケンサーモデルにセットします。
	 * @param sequenceTableModel MIDIトラックリストテーブルモデル
	 * @return 成功したらtrue
	 */
	public boolean setSequenceTrackListTableModel(SequenceTrackListTableModel sequenceTableModel) {
		// javax.sound.midi:Sequencer.setSequence() のドキュメントにある
		// 「このメソッドは、Sequencer が閉じている場合でも呼び出すことができます。 」
		// という記述は、null をセットする場合には当てはまらない。
		// 連鎖的に stop() が呼ばれるために IllegalStateException sequencer not open が出る。
		// この現象を回避するため、あらかじめチェックしてから setSequence() を呼び出している。
		//
		if( sequenceTableModel != null || getSequencer().isOpen() ) {
			Sequence sequence = null;
			if( sequenceTableModel != null )
				sequence = sequenceTableModel.getSequence();
			try {
				getSequencer().setSequence(sequence);
			} catch ( InvalidMidiDataException e ) {
				e.printStackTrace();
				return false;
			}
		}
		if( this.sequenceTableModel != null ) {
			this.sequenceTableModel.fireTableDataChanged();
		}
		if( sequenceTableModel != null ) {
			sequenceTableModel.fireTableDataChanged();
		}
		this.sequenceTableModel = sequenceTableModel;
		fireStateChanged();
		return true;
	}

	/**
	 * 小節単位で位置を移動します。
	 * @param measureOffset 何小節進めるか（戻したいときは負数を指定）
	 */
	private void moveMeasure(int measureOffset) {
		if( measureOffset == 0 || sequenceTableModel == null )
			return;
		SequenceTickIndex seqIndex = sequenceTableModel.getSequenceTickIndex();
		Sequencer sequencer = getSequencer();
		int measurePosition = seqIndex.tickToMeasure(sequencer.getTickPosition());
		long newTickPosition = seqIndex.measureToTick(measurePosition + measureOffset);
		if( newTickPosition < 0 ) {
			// 下限
			newTickPosition = 0;
		}
		else {
			long tickLength = sequencer.getTickLength();
			if( newTickPosition > tickLength ) {
				// 上限
				newTickPosition = tickLength - 1;
			}
		}
		sequencer.setTickPosition(newTickPosition);
		fireStateChanged();
	}
	/**
	 * １小節戻るアクション
	 */
	public Action moveBackwardAction = new AbstractAction() {
		{
			putValue(SHORT_DESCRIPTION, "Move backward 1 measure - １小節戻る");
			putValue(LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.BACKWARD_ICON));
		}
		@Override
		public void actionPerformed(ActionEvent event) { moveMeasure(-1); }
	};
	/**
	 *１小節進むアクション
	 */
	public Action moveForwardAction = new AbstractAction() {
		{
			putValue(SHORT_DESCRIPTION, "Move forward 1 measure - １小節進む");
			putValue(LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.FORWARD_ICON));
		}
		@Override
		public void actionPerformed(ActionEvent event) { moveMeasure(1); }
	};
	/**
	 * マスター同期モードのコンボボックスモデル
	 */
	public ComboBoxModel<Sequencer.SyncMode> masterSyncModeModel =
		new DefaultComboBoxModel<Sequencer.SyncMode>(getSequencer().getMasterSyncModes()) {{
			addListDataListener(new ListDataListener() {
				@Override
				public void intervalAdded(ListDataEvent e) { }
				@Override
				public void intervalRemoved(ListDataEvent e) { }
				@Override
				public void contentsChanged(ListDataEvent e) {
					getSequencer().setMasterSyncMode((Sequencer.SyncMode)getSelectedItem());
				}
			});
		}};
	/**
	 * スレーブ同期モードのコンボボックスモデル
	 */
	public ComboBoxModel<Sequencer.SyncMode> slaveSyncModeModel =
		new DefaultComboBoxModel<Sequencer.SyncMode>(getSequencer().getSlaveSyncModes()) {{
			addListDataListener(new ListDataListener() {
				@Override
				public void intervalAdded(ListDataEvent e) { }
				@Override
				public void intervalRemoved(ListDataEvent e) { }
				@Override
				public void contentsChanged(ListDataEvent e) {
					getSequencer().setSlaveSyncMode((Sequencer.SyncMode)getSelectedItem());
				}
			});
		}};
}
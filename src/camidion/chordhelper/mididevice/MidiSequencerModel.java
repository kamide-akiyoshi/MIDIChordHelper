package camidion.chordhelper.mididevice;

import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
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
import camidion.chordhelper.midieditor.SequencerSpeedSlider;

/**
 * MIDIシーケンサモデル（再生位置モデルのインターフェース付き）
 */
public class MidiSequencerModel extends MidiDeviceModel implements BoundedRangeModel {
	/**
	 * 再生位置スライダーモデルの最小単位 [μ秒]
	 */
	public static final long RESOLUTION_MICROSECOND = 1000L;
	/**
	 * MIDIシーケンサモデルを構築します。
	 * @param sequencer シーケンサーMIDIデバイス
	 * @param deviceModelTree 親のMIDIデバイスツリーモデル
	 */
	public MidiSequencerModel(Sequencer sequencer, MidiDeviceTreeModel deviceModelTree) {
		super(sequencer, deviceModelTree);
	}
	/**
	 * このシーケンサーの再生スピード
	 */
	public BoundedRangeModel speedSliderModel = new DefaultBoundedRangeModel(0, 0, -7, 7) {{
		addChangeListener(e->getSequencer().setTempoFactor(SequencerSpeedSlider.tempoFactorOf(getValue())));
	}};
	/**
	 * 対象MIDIシーケンサデバイス（{@link #getMidiDevice()}をキャストした結果）を返します。
	 * @return 対象MIDIシーケンサデバイス
	 */
	public Sequencer getSequencer() { return (Sequencer)device; }
	/**
	 * 開始終了アクション
	 */
	public Action getStartStopAction() { return startStopAction; }
	private StartStopAction startStopAction = new StartStopAction();
	private class StartStopAction extends AbstractAction {
		private Map<Boolean,Icon> iconMap = new HashMap<Boolean,Icon>() {
			{
				put(Boolean.FALSE, new ButtonIcon(ButtonIcon.PLAY_ICON));
				put(Boolean.TRUE, new ButtonIcon(ButtonIcon.PAUSE_ICON));
			}
		};
		{
			putValue(SHORT_DESCRIPTION, "Start/Stop recording or playing - 録音または再生の開始／停止");
			setRunning(false);
			updateEnableStatus();
		}
		@Override
		public void actionPerformed(ActionEvent event) {
			if( timeRangeUpdater.isRunning() ) stop(); else start();
			updateEnableStatus();
		}
		private void setRunning(boolean isRunning) {
			putValue(LARGE_ICON_KEY, iconMap.get(isRunning));
			putValue(SELECTED_KEY, isRunning);
		}
		/**
		 * 再生または録音が可能かチェックし、操作可能状態を更新します。
		 */
		public void updateEnableStatus() { setEnabled(isStartable()); }
	}
	/**
	 * 再生または録音が可能か調べます。
	 * <p>以下の条件が揃ったときに再生または録音が可能と判定されます。</p>
	 * <ul>
	 * <li>MIDIシーケンサデバイスが開いている</li>
	 * <li>MIDIシーケンサに操作対象のMIDIシーケンスが設定されている</li>
	 * </ul>
	 * @return 再生または録音が可能な場合true
	 */
	public boolean isStartable() {
		return device.isOpen() && getSequencer().getSequence() != null;
	}
	/**
	 * {@inheritDoc}
	 * <p>シーケンサモデルの場合、録音再生可能状態が変わるので、開始終了アクションにも通知します。</p>
	 */
	@Override
	public void open() throws MidiUnavailableException {
		super.open();
		startStopAction.updateEnableStatus();
		fireStateChanged();
		if( sequenceTrackListTableModel != null ) {
			sequenceTrackListTableModel.getParent().fireTableDataChanged();
		}
	}
	/**
	 * {@inheritDoc}
	 * <p>シーケンサモデルの場合、再生または録音が不可能になるので、開始終了アクションにも通知します。</p>
	 */
	@Override
	public void close() {
		stop();
		try {
			setSequenceTrackListTableModel(null);
		} catch (InvalidMidiDataException|IllegalStateException e) {
			e.printStackTrace();
		}
		super.close();
		startStopAction.updateEnableStatus();
		fireStateChanged();
		if( sequenceTrackListTableModel != null ) {
			sequenceTrackListTableModel.getParent().fireTableDataChanged();
		}
	}
	/**
	 * 再生または録音を開始します。
	 *
	 * <p>録音するMIDIチャンネルがMIDIエディタで指定されている場合、
	 * 録音スタート時のタイムスタンプが正しく０になるよう、各MIDIデバイスのタイムスタンプをすべてリセットします。
	 * </p>
	 * <p>このシーケンサのMIDIデバイスが閉じている場合、再生や録音は開始されません。</p>
	 */
	public void start() {
		if( ! device.isOpen() ) return;
		Sequencer sequencer = getSequencer();
		SequenceTrackListTableModel sequenceTrackListTableModel = getSequenceTrackListTableModel();
		if( sequenceTrackListTableModel != null && sequenceTrackListTableModel.hasRecordChannel() ) {
			deviceTreeModel.resetMicrosecondPosition();
			sequencer.startRecording();
		} else sequencer.start();
		timeRangeUpdater.start();
		startStopAction.setRunning(true);
		fireStateChanged();
	}
	/**
	 * 再生または録音を停止します。
	 */
	public void stop() {
		Sequencer sequencer = getSequencer();
		if( sequencer.isOpen() ) sequencer.stop();
		timeRangeUpdater.stop();
		startStopAction.setRunning(false);
		fireStateChanged();
	}
	private long correctMicrosecond(long us) {
		// Sequencer.getMicrosecondLength() returns NEGATIVE value
		//  when over 0x7FFFFFFF microseconds (== 35.7913941166666... minutes),
		//  should be corrected when negative
		return us < 0 ? 0x100000000L + us : us ;
	}
	/**
	 * このシーケンサーにロードされているシーケンスの長さをマイクロ秒単位で返します。
	 * シーケンスが設定されていない場合は0を返します。
	 * 曲が長すぎて {@link Sequencer#getMicrosecondLength()} が負数を返してしまった場合の補正も行います。
	 * @return マイクロ秒単位でのシーケンスの長さ
	 */
	public long getMicrosecondLength() {
		return correctMicrosecond(getSequencer().getMicrosecondLength());
	}
	/**
	 * シーケンス上の現在位置をマイクロ秒単位で返します。
	 * 曲が長すぎて {@link Sequencer#getMicrosecondPosition()} が負数を返してしまった場合の補正も行います。
	 * @return マイクロ秒単位での現在の位置
	 */
	public long getMicrosecondPosition() {
		return correctMicrosecond(getSequencer().getMicrosecondPosition());
	}
	@Override
	public int getMaximum() { return (int)(getMicrosecondLength()/RESOLUTION_MICROSECOND); }
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
	@Override
	public int getValue() { return (int)(getMicrosecondPosition()/RESOLUTION_MICROSECOND); }
	@Override
	public void setValue(int newValue) {
		getSequencer().setMicrosecondPosition(RESOLUTION_MICROSECOND * (long)newValue);
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
	/**
	 * シーケンサに合わせてミリ秒位置を更新するタイマー
	 */
	private javax.swing.Timer timeRangeUpdater = new javax.swing.Timer(20, e->{
		if( valueIsAdjusting || ! getSequencer().isRunning() ) {
			// 手動で移動中の場合や、シーケンサが止まっている場合は、タイマーによる更新は不要
			return;
		}
		// リスナーに読み込みを促す
		fireStateChanged();
	});
	@Override
	public void setRangeProperties(int value, int extent, int min, int max, boolean valueIsAdjusting) {
		getSequencer().setMicrosecondPosition(RESOLUTION_MICROSECOND * (long)value);
		setValueIsAdjusting(valueIsAdjusting);
		fireStateChanged();
	}
	protected EventListenerList listenerList = new EventListenerList();
	/**
	 * {@inheritDoc}
	 * <p>このシーケンサーの再生時間位置または再生対象ファイルが変更されたときに
	 * 通知を受けるリスナーを追加します。
	 * </p>
	 */
	@Override
	public void addChangeListener(ChangeListener listener) {
		listenerList.add(ChangeListener.class, listener);
	}
	/**
	 * {@inheritDoc}
	 * <p>このシーケンサーの再生時間位置または再生対象ファイルが変更されたときに
	 * 通知を受けるリスナーを除去します。
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
	private SequenceTrackListTableModel sequenceTrackListTableModel = null;
	/**
	 * このシーケンサーに現在ロードされているシーケンスのMIDIトラックリストテーブルモデルを返します。
	 * @return MIDIトラックリストテーブルモデル（何もロードされていなければnull）
	 */
	public SequenceTrackListTableModel getSequenceTrackListTableModel() {
		return sequenceTrackListTableModel;
	}
	/**
	 * MIDIトラックリストテーブルモデルをこのシーケンサーモデルにセットします。
	 * nullを指定してアンセットすることもできます。
	 * @param sequenceTableModel MIDIトラックリストテーブルモデル
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 * @throws IllegalStateException MIDIシーケンサデバイスが閉じている状態で引数にnullを指定した場合
	 */
	public void setSequenceTrackListTableModel(SequenceTrackListTableModel sequenceTableModel)
		throws InvalidMidiDataException
	{
		Sequencer sequencer = getSequencer();
		Sequence sequence = sequenceTableModel == null ? null : sequenceTableModel.getSequence();
		sequencer.setSequence(sequence);
		startStopAction.updateEnableStatus();
		if( this.sequenceTrackListTableModel != null ) this.sequenceTrackListTableModel.fireTableDataChanged();
		if( sequenceTableModel != null ) sequenceTableModel.fireTableDataChanged();
		this.sequenceTrackListTableModel = sequenceTableModel;
		fireStateChanged();
	}
	/**
	 * 小節単位で位置を移動します。
	 * @param measureOffset 何小節進めるか（戻したいときは負数を指定）
	 */
	private void moveMeasure(int measureOffset) {
		if( measureOffset == 0 || sequenceTrackListTableModel == null ) return;
		SequenceTickIndex seqIndex = sequenceTrackListTableModel.getSequenceTickIndex();
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
	public Action getMoveBackwardAction() { return moveBackwardAction; }
	private Action moveBackwardAction = new AbstractAction() {
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
	public Action getMoveForwardAction() { return moveForwardAction; }
	private Action moveForwardAction = new AbstractAction() {
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
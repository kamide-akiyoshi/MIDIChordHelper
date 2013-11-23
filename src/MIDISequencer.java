
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoundedRangeModel;
import javax.swing.BoxLayout;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

/**
 * シーケンサの現在位置（分：秒）を表示するビュー
 */
class TimeIndicator extends JPanel implements ChangeListener {
	private static abstract class TimeLabel extends JLabel {
		/**
		 * 時間の値（秒）
		 */
		private int valueInSec;
		/**
		 * 時間の値を秒単位で設定します。
		 * @param sec 秒単位の時間
		 */
		public void setTimeInSecond(int sec) {
			if(valueInSec != sec) setText(toTimeString(valueInSec = sec));
		}
		/**
		 * 時間の値を文字列に変換します。
		 * @param sec 秒単位の時間
		 * @return 変換結果（分：秒）
		 */
		protected String toTimeString(int sec) {
			return String.format("%02d:%02d", sec/60, sec%60);
		}
	}
	private static class TimePositionLabel extends TimeLabel {
		public TimePositionLabel() {
			setFont( getFont().deriveFont(getFont().getSize2D() + 4) );
			setForeground( new Color(0x80,0x00,0x00) );
			setToolTipText("Time position - 現在位置（分：秒）");
			setText(toTimeString(0));
		}
	}
	private static class TimeLengthLabel extends TimeLabel {
		public TimeLengthLabel() {
			setToolTipText("Time length - 曲の長さ（分：秒）");
			setText(toTimeString(0));
		}
		@Override
		protected String toTimeString(int sec) {
			return "/"+super.toTimeString(sec);
		}
	}
	private TimeLabel timePositionLabel = new TimePositionLabel();
	private TimeLabel timeLengthLabel = new TimeLengthLabel();
	private MidiSequencerModel model;
	/**
	 * シーケンサの現在位置（分：秒）を表示するビューを構築します。
	 * @param model MIDIシーケンサモデル
	 */
	public TimeIndicator(MidiSequencerModel model) {
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(timePositionLabel);
		add(timeLengthLabel);
		(this.model = model).addChangeListener(this);
	}
	@Override
	public void stateChanged(ChangeEvent e) {
		timeLengthLabel.setTimeInSecond(model.getMaximum()/1000);
		timePositionLabel.setTimeInSecond(model.getValue()/1000);
	}
}

/**
 * 小節表示ビュー
 */
class MeasureIndicator extends JPanel implements ChangeListener {
	private static abstract class MeasureLabel extends JLabel {
		protected int measure = -1;
		public boolean setMeasure(int measure) {
			if( this.measure == measure ) return false;
			this.measure = measure;
			return true;
		}
	}
	private static class MeasurePositionLabel extends MeasureLabel {
		protected int beat = 0;
		public MeasurePositionLabel() {
			setFont( getFont().deriveFont(getFont().getSize2D() + 4) );
			setForeground( new Color(0x80,0x00,0x00) );
			setText("0001:01");
			setToolTipText("Measure:beat position - 何小節目：何拍目");
		}
		public boolean setMeasure(int measure, int beat) {
			if( ! super.setMeasure(measure) && this.beat == beat )
				return false;
			setText(String.format("%04d:%02d", measure+1, beat+1));
			return true;
		}
	}
	private static class MeasureLengthLabel extends MeasureLabel {
		public MeasureLengthLabel() {
			setText("/0000");
			setToolTipText("Measure length - 小節の数");
		}
		public boolean setMeasure(int measure) {
			if( ! super.setMeasure(measure) )
				return false;
			setText(String.format("/%04d", measure));
			return true;
		}
	}
	private MeasurePositionLabel measurePositionLabel;
	private MeasureLengthLabel measureLengthLabel;
	private MidiSequencerModel model;
	/**
	 * シーケンサの現在の小節位置を表示するビューを構築します。
	 * @param model スライダー用の時間範囲データモデル
	 */
	public MeasureIndicator(MidiSequencerModel model) {
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(measurePositionLabel = new MeasurePositionLabel());
		add(measureLengthLabel = new MeasureLengthLabel());
		(this.model = model).addChangeListener(this);
	}
	@Override
	public void stateChanged(ChangeEvent e) {
		Sequencer sequencer = model.getSequencer();
		SequenceTrackListTableModel sequenceTableModel = model.getSequenceTableModel();
		SequenceTickIndex tickIndex = (
			sequenceTableModel == null ? null : sequenceTableModel.getSequenceTickIndex()
		);
		if( ! sequencer.isRunning() || sequencer.isRecording() ) {
			// 停止中または録音中の場合、長さが変わることがあるので表示を更新
			if( tickIndex == null ) {
				measureLengthLabel.setMeasure(0);
			}
			else {
				long tickLength = sequencer.getTickLength();
				int measureLength = tickIndex.tickToMeasure(tickLength);
				measureLengthLabel.setMeasure(measureLength);
			}
		}
		// 小節位置の表示を更新
		if( tickIndex == null ) {
			measurePositionLabel.setMeasure(0, 0);
		}
		else {
			long tickPosition = sequencer.getTickPosition();
			int measurePosition = tickIndex.tickToMeasure(tickPosition);
			measurePositionLabel.setMeasure(measurePosition, tickIndex.lastBeat);
		}
	}
}

/**
 * MIDIシーケンサモデル
 */
class MidiSequencerModel extends MidiConnecterListModel implements BoundedRangeModel {
	/**
	 * MIDIシーケンサモデルを構築します。
	 * @param deviceModelList 親のMIDIデバイスモデルリスト
	 * @param sequencer シーケンサーMIDIデバイス
	 * @param modelList MIDIコネクタリストモデルのリスト
	 */
	public MidiSequencerModel(
		MidiDeviceModelList deviceModelList,
		Sequencer sequencer,
		List<MidiConnecterListModel> modelList
	) {
		super(sequencer, modelList);
		this.deviceModelList = deviceModelList;
		sequencer.addMetaEventListener(new MetaEventListener() {
			/**
			 * {@inheritDoc}
			 *
			 * この実装では EOT (End Of Track、type==0x2F) を受信したときに、
			 * 曲の先頭に戻し、次の曲があればその曲を再生し、
			 * なければ秒位置更新タイマーを停止します。
			 */
			@Override
			public void meta(MetaMessage msg) {
				if( msg.getType() == 0x2F ) {
					getSequencer().setMicrosecondPosition(0);
					// リピートモードの場合、同じ曲をもう一度再生する。
					// そうでない場合、次の曲へ進んで再生する。
					// 次の曲がなければ、そこで終了。
					boolean isRepeatMode = (Boolean)toggleRepeatAction.getValue(Action.SELECTED_KEY);
					if( isRepeatMode || MidiSequencerModel.this.deviceModelList.editorDialog.sequenceListTableModel.loadNext(1) ) {
						start();
					}
					else {
						stop();
					}
				}
			}
		});
	}
	/**
	 * MIDIデバイスモデルリスト
	 */
	private MidiDeviceModelList deviceModelList;
	/**
	 * このシーケンサーの再生スピード調整モデル
	 */
	BoundedRangeModel speedSliderModel = new DefaultBoundedRangeModel(0, 0, -7, 7) {{
		addChangeListener(
			new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					int val = getValue();
					getSequencer().setTempoFactor((float)(
						val == 0 ? 1.0 : Math.pow( 2.0, ((double)val)/12.0 )
					));
				}
			}
		);
	}};
	/**
	 * MIDIシーケンサを返します。
	 * @return MIDIシーケンサ
	 */
	public Sequencer getSequencer() { return (Sequencer)device; }
	/**
	 * 開始終了アクション
	 */
	StartStopAction startStopAction = new StartStopAction();
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
		deviceModelList.startSequencerWithResetTimestamps();
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
	public boolean getValueIsAdjusting() {
		return valueIsAdjusting;
	}
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
	@Override
	public void addChangeListener(ChangeListener listener) {
		listenerList.add(ChangeListener.class, listener);
	}
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
	 * 繰り返し再生ON/OFF切り替えアクション
	 */
	public Action toggleRepeatAction = new AbstractAction() {
		{
			putValue(SHORT_DESCRIPTION, "Repeat - 繰り返し再生");
			putValue(LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.REPEAT_ICON));
			putValue(SELECTED_KEY, false);
		}
		@Override
		public void actionPerformed(ActionEvent event) {
			// 特にやることなし
		}
	};
	/**
	 * MIDIトラックリストテーブルモデル
	 */
	private SequenceTrackListTableModel sequenceTableModel = null;
	/**
	 * このシーケンサーに現在ロードされているシーケンスのMIDIトラックリストテーブルモデルを返します。
	 * @return MIDIトラックリストテーブルモデル
	 */
	public SequenceTrackListTableModel getSequenceTableModel() {
		return sequenceTableModel;
	}
	/**
	 * MIDIトラックリストテーブルモデルを
	 * このシーケンサーモデルにセットします。
	 * @param sequenceTableModel MIDIトラックリストテーブルモデル
	 * @return 成功したらtrue
	 */
	public boolean setSequenceTableModel(SequenceTrackListTableModel sequenceTableModel) {
		//
		// javax.sound.midi:Sequencer.setSequence() のドキュメントにある
		// 「このメソッドは、Sequencer が閉じている場合でも呼び出すことができます。 」
		// という記述は、null をセットする場合には当てはまらない。
		// 連鎖的に stop() が呼ばれるために IllegalStateException sequencer not open が出る。
		// この現象を回避するため、あらかじめチェックしてから setSequence() を呼び出している。
		//
		if( sequenceTableModel != null || getSequencer().isOpen() ) {
			Sequence seq = null;
			if( sequenceTableModel != null ) {
				seq = sequenceTableModel.getSequence();
			}
			try {
				getSequencer().setSequence(seq);
			} catch ( InvalidMidiDataException e ) {
				e.printStackTrace();
				return false;
			}
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
}


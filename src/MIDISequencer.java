
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.Sequencer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoundedRangeModel;
import javax.swing.BoxLayout;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
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
	private SequencerTimeRangeModel model;
	/**
	 * シーケンサの現在位置（分：秒）を表示するビューを構築します。
	 * @param model スライダー用の時間範囲データモデル
	 */
	public TimeIndicator(SequencerTimeRangeModel model) {
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
			setText( "0001:01" );
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
			setText( "/0000" );
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
	private SequencerTimeRangeModel model;
	/**
	 * シーケンサの現在の小節位置を表示するビューを構築します。
	 * @param model スライダー用の時間範囲データモデル
	 */
	public MeasureIndicator(SequencerTimeRangeModel model) {
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(measurePositionLabel = new MeasurePositionLabel());
		add(measureLengthLabel = new MeasureLengthLabel());
		(this.model = model).addChangeListener(this);
	}
	@Override
	public void stateChanged(ChangeEvent e) {
		Sequencer sequencer = model.deviceModelList.getSequencer();
		MidiSequenceTableModel sequenceTableModel =
			model.deviceModelList.timeRangeModel.getSequenceTableModel();
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
 * シーケンサーの再生スピード調整モデル
 */
class SpeedSliderModel extends DefaultBoundedRangeModel implements ChangeListener {
	private Sequencer sequencer;
	public SpeedSliderModel( Sequencer sequencer ) {
		super( 0, 0, -7, 7 );
		this.sequencer = sequencer;
		addChangeListener(this);
	}
	public void stateChanged(ChangeEvent e) {
		int val = getValue();
		sequencer.setTempoFactor((float)(
			val == 0 ? 1.0 : Math.pow( 2.0, ((double)val)/12.0 )
		));
	}
}

/**
 * シーケンサーの再生スピード調整スライダビュー
 */
class SequencerSpeedSlider extends JPanel implements ActionListener {
	static String items[] = {
		"x 1.0",
		"x 1.5",
		"x 2",
		"x 4",
		"x 8",
		"x 16",
	};
	JSlider slider;
	JLabel title_label;
	JComboBox<String> scale_combo_box;
	public SequencerSpeedSlider( SpeedSliderModel model ) {
		add( title_label = new JLabel("Speed:") );
		add( slider = new JSlider(model) );
		add( scale_combo_box = new JComboBox<String>(items) );
		scale_combo_box.addActionListener(this);
		slider.setPaintTicks(true);
		slider.setMajorTickSpacing(12);
		slider.setMinorTickSpacing(1);
		slider.setVisible(false);
	}
	public void actionPerformed(ActionEvent e) {
		int index = scale_combo_box.getSelectedIndex();
		SpeedSliderModel model = (SpeedSliderModel)slider.getModel();
		if( index == 0 ) {
			model.setValue(0);
			slider.setVisible(false);
			title_label.setVisible(true);
		}
		else {
			int max_val = ( index == 1 ? 7 : (index-1)*12 );
			model.setMinimum(-max_val);
			model.setMaximum(max_val);
			slider.setMajorTickSpacing( index == 1 ? 7 : 12 );
			slider.setMinorTickSpacing( index > 3 ? 12 : 1 );
			slider.setVisible(true);
			title_label.setVisible(false);
		}
	}
}

/**
 * シーケンサーのミリ秒位置を表す範囲データモデルです。
 */
class SequencerTimeRangeModel
	implements BoundedRangeModel, MetaEventListener, ActionListener
{
	/**
	 * 更新インターバル [ミリ秒]
	 */
	private static final int INTERVAL_MS = 20;
	/**
	 * MIDIデバイスモデルリスト
	 */
	MidiDeviceModelList deviceModelList;
	/**
	 * シーケンサに同期させるためのサンプリングタイマー更新
	 */
	private class Updater extends javax.swing.Timer {
		public Updater(ActionListener actionListener) {
			super(INTERVAL_MS, actionListener);
		}
		@Override
		public void start() {
			startStopAction.setRunning(true);
			super.start();
		}
		@Override
		public void stop() {
			startStopAction.setRunning(false);
			super.stop();
		}
	}
	/**
	 * このデータモデルをシーケンサに同期させるためのサンプリングタイマー
	 */
	private Updater updater = new Updater(this);
	/**
	 * 開始終了アクション
	 */
	public StartStopAction startStopAction = new StartStopAction();
	/**
	 * 開始終了アクション
	 */
	private class StartStopAction extends AbstractAction {
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
			if(isRunning()) stop(); else start();
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
	 * 小節単位で位置を移動します。
	 * @param measureOffset 何小節進めるか（戻したいときは負数を指定）
	 */
	private void moveMeasure(int measureOffset) {
		if( measureOffset == 0 || seqModel == null )
			return;
		SequenceTickIndex seqIndex = seqModel.getSequenceTickIndex();
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
	 * 繰り返し再生ON/OFF切り替えアクション
	 */
	public Action toggleRepeatAction = new AbstractAction() {
		{
			putValue(SHORT_DESCRIPTION, "Repeat - 繰り返し再生");
			putValue(LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.REPEAT_ICON));
			putValue(SELECTED_KEY, false);
		}
		@Override
		public void actionPerformed(ActionEvent event) { }
	};
	/**
	 * シーケンサ
	 */
	private Sequencer sequencer;
	/**
	 * 新しい {@link SequencerTimeRangeModel} を構築します。
	 * @param deviceModelList MIDIデバイスモデルリスト
	 */
	public SequencerTimeRangeModel(MidiDeviceModelList deviceModelList) {
		this.deviceModelList = deviceModelList;
		sequencer = deviceModelList.getSequencer();
		sequencer.addMetaEventListener(this);
	}
	@Override
	public void meta(MetaMessage msg) {
		if( msg.getType() == 0x2F ) { // EOT (End Of Track) を受信した場合
			updater.stop();
			// 先頭に戻す
			sequencer.setMicrosecondPosition(0);
			// リピートモードの場合、同じ曲をもう一度再生する。
			// そうでない場合、次の曲へ進んで再生する。
			// 次の曲がなければ、そこで終了。
			boolean isRepeatMode = (Boolean)toggleRepeatAction.getValue(Action.SELECTED_KEY);
			if( isRepeatMode || deviceModelList.editorDialog.loadNext(1) )
				start();
			else
				fireStateChanged();
		}
	}
	@Override
	public int getExtent() { return 0; }
	@Override
	public void setExtent(int newExtent) {}
	@Override
	public int getMinimum() { return 0; }
	@Override
	public void setMinimum(int newMinimum) {}
	private long getMicrosecondLength() {
		//
		// Sequencer.getMicrosecondLength() returns NEGATIVE value
		//  when over 0x7FFFFFFF microseconds (== 35.7913941166666... minutes),
		//  should be corrected when negative
		//
		long usLength = sequencer.getMicrosecondLength();
		return usLength < 0 ? 0x100000000L + usLength : usLength ;
	}
	@Override
	public int getMaximum() {
		return (int)(getMicrosecondLength()/1000L);
	}
	@Override
	public void setMaximum(int newMaximum) {}
	private long getMicrosecondPosition() {
		long usPosition = sequencer.getMicrosecondPosition();
		return usPosition < 0 ? 0x100000000L + usPosition : usPosition ;
	}
	@Override
	public int getValue() {
		return (int)(getMicrosecondPosition()/1000L);
	}
	@Override
	public void setValue(int newValue) {
		sequencer.setMicrosecondPosition( 1000L * (long)newValue );
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
	public void setValueIsAdjusting(boolean b) {
		valueIsAdjusting = b;
	}
	@Override
	public void setRangeProperties(int value, int extent, int min, int max, boolean adjusting) {
		sequencer.setMicrosecondPosition( 1000L * (long)value );
		valueIsAdjusting = adjusting;
		fireStateChanged();
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		if( ! valueIsAdjusting ) fireStateChanged();
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
	 * 状態が変わったことをリスナーに通知します。
	 */
	public void fireStateChanged() {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==ChangeListener.class) {
				((ChangeListener)listeners[i+1]).stateChanged(new ChangeEvent(this));
			}
		}
	}
	private MidiSequenceTableModel seqModel = null;
	public MidiSequenceTableModel getSequenceTableModel() {
		return seqModel;
	}
	public boolean setSequenceModel(MidiSequenceTableModel seqModel) {
		//
		// javax.sound.midi:Sequencer.setSequence() のドキュメントにある
		// 「このメソッドは、Sequencer が閉じている場合でも呼び出すことができます。 」
		// という記述は、null をセットする場合には当てはまらない。
		// 連鎖的に stop() が呼ばれるために IllegalStateException sequencer not open が出る。
		// この現象を回避するため、あらかじめチェックしてから setSequence() を呼び出している。
		//
		if( seqModel != null || sequencer.isOpen() ) {
			try {
				// Set new MIDI data
				sequencer.setSequence(seqModel==null ? null : seqModel.getSequence());
			} catch ( InvalidMidiDataException e ) {
				e.printStackTrace();
				return false;
			}
		}
		this.seqModel = seqModel;
		fireStateChanged();
		return true;
	}
	public void start() {
		if( ! sequencer.isOpen() || sequencer.getSequence() == null ) {
			updater.stop();
			return;
		}
		updater.start();
		if( deviceModelList.isRecordable() ) {
			for( MidiConnecterListModel model : deviceModelList )
				model.resetMicrosecondPosition();
			System.gc();
			sequencer.startRecording();
		}
		else {
			System.gc();
			sequencer.start();
		}
		fireStateChanged();
	}
	public void stop() {
		if(sequencer.isOpen()) sequencer.stop();
		updater.stop();
		fireStateChanged();
	}
	/**
	 * サンプリングタイマーが実行中の場合trueを返します。
	 * @return 実行中の場合true
	 */
	public boolean isRunning() { return updater.isRunning(); }
}

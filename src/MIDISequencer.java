
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
 * 時間または小節の表示形式
 */
enum TimeLabelFormat {
	/**
	 * 時間位置
	 */
	POSITION,
	/**
	 * 時間の長さ
	 */
	LENGTH {
		@Override
		public String toTimeString(int sec) {
			return "/"+super.toTimeString(sec);
		}
	};
	/**
	 * この形式で時間の文字列を返します。
	 * @param sec 時間の秒数
	 * @return この形式での時間の文字列
	 */
	public String toTimeString(int sec) {
		return String.format("%02d:%02d", sec/60, sec%60);
	}
}

/**
 * シーケンサの現在位置（分：秒）を表示するビュー
 */
class TimeIndicator extends JPanel implements ChangeListener {
	/**
	 * 時間（分：秒）表示ラベル
	 */
	private static class TimeLabel extends JLabel {
		/**
		 * 時間表示の形式
		 */
		private TimeLabelFormat formatType;
		/**
		 * 時間の値（秒）
		 */
		private int valueInSec;
		/**
		 * 時間表示ラベルを構築します。
		 * @param formatType 表示形式
		 */
		public TimeLabel(TimeLabelFormat formatType) {
			super();
			if( (this.formatType = formatType) == TimeLabelFormat.POSITION ) {
				setFont( getFont().deriveFont(getFont().getSize2D() + 4) );
				setForeground( new Color(0x80,0x00,0x00) );
				setToolTipText("Time position - 現在位置（分：秒）");
			}
			else {
				setToolTipText("Time length - 曲の長さ（分：秒）");
			}
			setText(formatType.toTimeString(valueInSec));
		}
		/**
		 * 時間の値を秒単位で設定します。
		 * @param sec 秒単位の時間
		 */
		public void setTimeInSecond(int sec) {
			if( valueInSec == sec )
				return;
			setText(formatType.toTimeString(sec));
		}
	}
	private TimeLabel timePositionLabel = new TimeLabel(TimeLabelFormat.POSITION);
	private TimeLabel timeLengthLabel = new TimeLabel(TimeLabelFormat.LENGTH);
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
	public void stateChanged(ChangeEvent e) {
		timeLengthLabel.setTimeInSecond(model.getMaximum()/1000);
		timePositionLabel.setTimeInSecond(model.getValue()/1000);
	}
}

/**
 * 小節表示ビュー
 */
class MeasureIndicator extends JPanel implements ChangeListener {
	/**
	 * 小節表示ラベル
	 */
	private static class MeasureLabel extends JLabel {
		private TimeLabelFormat formatType;
		private int measure = -1;
		private int beat = 0;
		public MeasureLabel(TimeLabelFormat formatType) {
			if( (this.formatType = formatType) == TimeLabelFormat.POSITION ) {
				setFont( getFont().deriveFont(getFont().getSize2D() + 4) );
				setForeground( new Color(0x80,0x00,0x00) );
				setText( "0001:01" );
				setToolTipText("Measure:beat position - 何小節目：何拍目");
			}
			else {
				setText( "/0000" );
				setToolTipText("Measure length - 小節の数");
			}
		}
		public void setMeasure(int measure) {
			setMeasure(measure,0);
		}
		public void setMeasure(int measure, int beat) {
			if( this.measure == measure && this.beat == beat ) {
				return;
			}
			this.beat = beat;
			this.measure = measure;
			if( formatType == TimeLabelFormat.LENGTH )
				setText(String.format("/%04d", measure));
			else
				setText(String.format("%04d:%02d", measure+1, beat+1));
		}
	}
	private MeasureLabel measurePositionLabel = new MeasureLabel(TimeLabelFormat.POSITION);
	private MeasureLabel measureLengthLabel = new MeasureLabel(TimeLabelFormat.LENGTH);
	private SequencerTimeRangeModel model;
	/**
	 * シーケンサの現在の小節位置を表示するビューを構築します。
	 * @param model スライダー用の時間範囲データモデル
	 */
	public MeasureIndicator(SequencerTimeRangeModel model) {
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(measurePositionLabel);
		add(measureLengthLabel);
		(this.model = model).addChangeListener(this);
	}
	@Override
	public void stateChanged(ChangeEvent e) {
		Sequencer sequencer = model.deviceModelList.getSequencer();
		MidiSequenceTableModel seqModel =
			model.deviceModelList.timeRangeModel.getSequenceTableModel();
		SequenceTickIndex seqIndex = (
			seqModel == null ? null : seqModel.getSequenceTickIndex()
		);
		if( ! sequencer.isRunning() || sequencer.isRecording() ) {
			measureLengthLabel.setMeasure(
				seqIndex == null ? 0 : seqIndex.tickToMeasure(
					sequencer.getTickLength()
				)
			);
		}
		if( seqIndex == null ) {
			measurePositionLabel.setMeasure( 0, 0 );
		}
		else {
			int measurePosition = seqIndex.tickToMeasure(
				sequencer.getTickPosition()
			);
			measurePositionLabel.setMeasure(
				measurePosition, seqIndex.lastBeat
			);
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
class SpeedSlider extends JPanel implements ActionListener {
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
	public SpeedSlider( SpeedSliderModel model ) {
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
 * スライダー用の時間範囲データモデル
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
	 * シーケンサ
	 */
	private Sequencer sequencer;
	/**
	 * このデータモデルをシーケンサに同期させるためのサンプリングタイマー
	 */
	javax.swing.Timer timer = new javax.swing.Timer(INTERVAL_MS, this);
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
			if(timer.isRunning()) stop(); else start();
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
	 * 小節位置を移動します。
	 * @param measureOffset 小節位置の移動量（負数も指定可）
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
		if( msg.getType() != 0x2F ) return;
		/* End-Of-Track */
		timer.stop();
		startStopAction.setRunning(false);
		sequencer.setMicrosecondPosition(0);
		if(
			(Boolean)toggleRepeatAction.getValue(Action.SELECTED_KEY)
			||
			deviceModelList.editorDialog.loadNext(1)
		)
			start();
		else
			fireStateChanged();
	}
	@Override
	public int getExtent() { return 0; }
	@Override
	public int getMinimum() { return 0; }
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
	private long getMicrosecondPosition() {
		long usPosition = sequencer.getMicrosecondPosition();
		return usPosition < 0 ? 0x100000000L + usPosition : usPosition ;
	}
	@Override
	public int getValue() {
		return (int)(getMicrosecondPosition()/1000L);
	}
	@Override
	public void setExtent(int newExtent) {}
	@Override
	public void setMaximum(int newMaximum) {}
	@Override
	public void setMinimum(int newMinimum) {}
	@Override
	public void setRangeProperties(int value, int extent, int min, int max, boolean adjusting) {
		sequencer.setMicrosecondPosition( 1000L * (long)value );
		valueIsAdjusting = adjusting;
		fireStateChanged();
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
			startStopAction.setRunning(false);
			return;
		}
		startStopAction.setRunning(true);
		timer.start();
		if( deviceModelList.isRecordable() ) {
			for( MidiConnecterListModel model : deviceModelList )
				model.resetMicrosecondPosition();
			System.gc();
			sequencer.startRecording();
		}
		else {
			System.gc(); sequencer.start();
		}
		fireStateChanged();
	}
	public void stop() {
		if( sequencer.isOpen() )
			sequencer.stop();
		timer.stop();
		startStopAction.setRunning(false);
		fireStateChanged();
	}
}

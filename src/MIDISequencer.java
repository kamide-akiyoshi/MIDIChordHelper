
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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
class TimeIndicator extends JPanel {
	private TimeLabel timePositionLabel;
	private TimeLabel timeLengthLabel;
	private SequencerTimeRangeModel model;
	public TimeIndicator() {
		timePositionLabel = new TimeLabel(TimeLabelFormat.POSITION);
		timeLengthLabel = new TimeLabel(TimeLabelFormat.LENGTH);
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(timePositionLabel);
		add(timeLengthLabel);
	}
	public TimeIndicator(SequencerTimeRangeModel model) {
		this();
		(this.model = model).addChangeListener(
			new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					SequencerTimeRangeModel model = TimeIndicator.this.model;
					timeLengthLabel.setTimeInSecond(model.getMaximum()/1000);
					timePositionLabel.setTimeInSecond(model.getValue()/1000);
				}
			}
		);
	}
}

/**
 * 時間（分：秒）表示ラベル
 */
class TimeLabel extends JLabel {
	/**
	 * 時間表示の形式
	 */
	private TimeLabelFormat formatType;
	/**
	 * 時間の値（秒）
	 */
	private int valueInSec = -1;
	/**
	 * 時間表示ラベルを構築します。
	 * @param formatType 表示形式
	 */
	public TimeLabel(TimeLabelFormat formatType) {
		super();
		if( (this.formatType = formatType) == TimeLabelFormat.POSITION ) {
			float largePointSize = getFont().getSize2D() + 4;
			setFont( getFont().deriveFont(largePointSize) );
			setForeground( new Color(0x80,0x00,0x00) );
			setToolTipText("Time position - 現在位置（分：秒）");
		}
		else {
			setToolTipText("Time length - 曲の長さ（分：秒）");
		}
		setText(formatType.toTimeString(0));
	}
	/**
	 * 時間の値をマイクロ秒単位で設定します。
	 * @param us マイクロ秒単位の時間
	 */
	public void setTimeInMicrosecond(long us) {
		setTimeInSecond( (int)(us/1000000) );
	}
	/**
	 * 時間の値を秒単位で設定します。
	 * @param sec 秒単位の時間
	 */
	public void setTimeInSecond(int sec) {
		if( valueInSec == sec )
			return;
		if( (valueInSec = sec) < 0 )
			setText(null);
		else
			setText(formatType.toTimeString(sec));
	}
}

/**
 * 小節表示ビュー
 */
class MeasureIndicator extends JPanel {
	private SequencerTimeRangeModel model;
	private MeasureLabel measurePositionLabel;
	private MeasureLabel measureLengthLabel;
	public MeasureIndicator() {
		measurePositionLabel = new MeasureLabel(TimeLabelFormat.POSITION);
		measureLengthLabel = new MeasureLabel(TimeLabelFormat.LENGTH);
		setLayout( new BoxLayout( this, BoxLayout.X_AXIS ) );
		add( measurePositionLabel );
		add( measureLengthLabel );
	}
	public MeasureIndicator(SequencerTimeRangeModel model) {
		this();
		(this.model = model).addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				SequencerTimeRangeModel model = MeasureIndicator.this.model;
				Sequencer sequencer = model.deviceManager.getSequencer();
				MidiSequenceModel seqModel =
					model.deviceManager.timeRangeModel.getSequenceModel();
				SequenceIndex seqIndex = (
					seqModel == null ? null : seqModel.getSequenceIndex()
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
						measurePosition, seqIndex.last_beat
					);
				}
			}
		});
	}
}
/**
 * 小節表示ラベル
 */
class MeasureLabel extends JLabel {
	private TimeLabelFormat formatType;
	private int measure = -1;
	private int beat = 0;
	public MeasureLabel(TimeLabelFormat formatType) {
		if( (this.formatType = formatType) == TimeLabelFormat.POSITION ) {
			float large_point_size = getFont().getSize2D() + 4;
			setFont( getFont().deriveFont(large_point_size) );
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
		if( (this.measure = measure) < 0 )
			setText( null );
		else if( formatType == TimeLabelFormat.LENGTH )
			setText( String.format("/%04d", measure) );
		else
			setText( String.format("%04d:%02d", measure+1, beat+1) );
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
 * 時間範囲データモデル
 */
class SequencerTimeRangeModel implements BoundedRangeModel {
	static final int INTERVAL_MS = 20;
	MidiDeviceManager deviceManager;
	private Sequencer sequencer;
	javax.swing.Timer	timer;
	private boolean valueIsAdjusting = false;
	private EventListenerList listenerList = new EventListenerList();
	public StartStopAction startStopAction = new StartStopAction();
	class StartStopAction extends AbstractAction {
		Icon play_icon = new ButtonIcon(ButtonIcon.PLAY_ICON);
		Icon pause_icon = new ButtonIcon(ButtonIcon.PAUSE_ICON);
		{
			putValue(
				SHORT_DESCRIPTION,
				"Start/Stop recording or playing - 録音または再生の開始／停止"
			);
			putValue( LARGE_ICON_KEY, play_icon );
			putValue( SELECTED_KEY, false );
		}
		public void actionPerformed(ActionEvent event) {
			if( timer.isRunning() ) stop(); else start();
		}
		public void setRunning(boolean is_running) {
			putValue( LARGE_ICON_KEY, is_running ? pause_icon : play_icon );
			putValue( SELECTED_KEY, is_running );
		}
	}
	public Action move_backward_action = new AbstractAction() {
		{
			putValue( SHORT_DESCRIPTION, "Move backward 1 measure - １小節戻る" );
			putValue( LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.BACKWARD_ICON) );
		}
		public void actionPerformed( ActionEvent event ) {
			moveMeasure(-1);
		}
	};
	public Action move_forward_action = new AbstractAction() {
		{
			putValue( SHORT_DESCRIPTION, "Move forward 1 measure - １小節進む" );
			putValue( LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.FORWARD_ICON) );
		}
		public void actionPerformed( ActionEvent event ) {
			moveMeasure(1);
		}
	};
	public Action toggle_repeat_action = new AbstractAction() {
		{
			putValue( SHORT_DESCRIPTION, "Repeat - 繰り返し再生" );
			putValue( LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.REPEAT_ICON) );
			putValue( SELECTED_KEY, false );
		}
		public void actionPerformed(ActionEvent event) { }
	};
	public SequencerTimeRangeModel( MidiDeviceManager device_manager ) {
		this.deviceManager = device_manager;
		this.sequencer = device_manager.getSequencer();
		timer = new javax.swing.Timer(
			INTERVAL_MS,
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if( ! valueIsAdjusting ) fireStateChanged();
				}
			}
		);
		timer.setCoalesce(true);
		sequencer.addMetaEventListener(
			new MetaEventListener() {
				public void meta(MetaMessage msg) {
					if( msg.getType() != 0x2F /* End-Of-Track */)
						return;
					timer.stop();
					startStopAction.setRunning(false);
					sequencer.setMicrosecondPosition(0);
					if(
						(Boolean)toggle_repeat_action.getValue( Action.SELECTED_KEY ) ||
						SequencerTimeRangeModel.this.deviceManager.editorDialog.loadNext(1)
					) start();
					else fireStateChanged();
				}
			}
		);
	}
	public int getExtent() { return 0; }
	public int getMaximum() { return (int)(getMicrosecondLength()/1000L); }
	public int getMinimum() { return 0; }
	public int getValue() { return (int)(getMicrosecondPosition()/1000L); }
	public boolean getValueIsAdjusting() { return valueIsAdjusting; }
	public void setExtent(int new_extent) {}
	public void setMaximum(int new_maximum) {}
	public void setMinimum(int new_minimum) {}
	public void setRangeProperties(int value, int extent, int min, int max, boolean adjusting) {
		sequencer.setMicrosecondPosition( 1000L * (long)value );
		valueIsAdjusting = adjusting;
		fireStateChanged();
	}
	public void setValue(int new_value) {
		sequencer.setMicrosecondPosition( 1000L * (long)new_value );
		fireStateChanged();
	}
	public void setValueIsAdjusting(boolean b) {
		valueIsAdjusting = b;
	}
	public void addChangeListener(ChangeListener listener) {
		listenerList.add(ChangeListener.class, listener);
	}
	public void removeChangeListener(ChangeListener listener) {
		listenerList.remove(ChangeListener.class, listener);
	}
	public void fireStateChanged() {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==ChangeListener.class) {
				((ChangeListener)listeners[i+1]).stateChanged(new ChangeEvent(this));
			}
		}
	}
	long getMicrosecondLength() {
		//
		// Sequencer.getMicrosecondLength() returns NEGATIVE value
		//  when over 0x7FFFFFFF microseconds (== 35.7913941166666... minutes),
		//  should be corrected when negative
		//
		long us_len = sequencer.getMicrosecondLength();
		return us_len < 0 ? 0x100000000L + us_len : us_len ;
	}
	long getMicrosecondPosition() {
		long us_pos = sequencer.getMicrosecondPosition();
		return us_pos < 0 ? 0x100000000L + us_pos : us_pos ;
	}
	private MidiSequenceModel seq_model = null;
	public MidiSequenceModel getSequenceModel() { return seq_model; }
	public boolean setSequenceModel( MidiSequenceModel seq_model ) {
		//
		// javax.sound.midi:Sequencer.setSequence() のドキュメントにある
		// 「このメソッドは、Sequencer が閉じている場合でも呼び出すことができます。 」
		// という記述は、null をセットする場合には当てはまらない。
		// 連鎖的に stop() が呼ばれるために IllegalStateException sequencer not open が出る。
		// この現象を回避するため、あらかじめチェックしてから setSequence() を呼び出している。
		//
		if( seq_model != null || sequencer.isOpen() ) {
			try {
				// Set new MIDI data
				sequencer.setSequence(
						seq_model == null ? null : seq_model.getSequence()
						);
			} catch ( InvalidMidiDataException e ) {
				e.printStackTrace();
				return false;
			}
		}
		this.seq_model = seq_model;
		fireStateChanged();
		return true;
	}
	// 小節位置の相対移動
	public void moveMeasure( int measure_offset ) {
		if( measure_offset == 0 || seq_model == null ) return;
		SequenceIndex seq_index = seq_model.getSequenceIndex();
		long new_tick_pos =
				seq_index.measureToTick(
						measure_offset + seq_index.tickToMeasure(
								sequencer.getTickPosition()
								)
						);
		if( new_tick_pos < 0 ) new_tick_pos = 0;
		else {
			long tick_len = sequencer.getTickLength();
			if( new_tick_pos > tick_len ) new_tick_pos = tick_len - 1;
		}
		sequencer.setTickPosition( new_tick_pos );
		fireStateChanged();
	}

	// 開始／終了
	public boolean isStartable() {
		return sequencer.isOpen() && sequencer.getSequence() != null ;
	}
	public void start() {
		if( ! isStartable() ) {
			startStopAction.setRunning(false);
			return;
		}
		startStopAction.setRunning(true);
		timer.start();
		if( deviceManager.isRecordable() ) {
			deviceManager.resetMicrosecondPosition();
			System.gc();
			sequencer.startRecording();
		}
		else {
			System.gc(); sequencer.start();
		}
		fireStateChanged();
	}
	public void stop() {
		if( sequencer.isOpen() ) sequencer.stop();
		timer.stop();
		startStopAction.setRunning(false);
		fireStateChanged();
	}
}

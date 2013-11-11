
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.sound.midi.Sequencer;
import javax.swing.BoundedRangeModel;
import javax.swing.BoxLayout;
import javax.swing.DefaultBoundedRangeModel;
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
		Sequencer sequencer = model.sequencerModel.getSequencer();
		MidiSequenceTableModel sequenceTableModel =
			model.sequencerModel.getSequenceTableModel();
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
 * シーケンサーのミリ秒位置を表す範囲データモデル
 */
class SequencerTimeRangeModel implements BoundedRangeModel, ActionListener {
	/**
	 * MIDIシーケンサモデル
	 */
	MidiSequencerModel sequencerModel;
	/**
	 * 新しい {@link SequencerTimeRangeModel} を構築します。
	 */
	public SequencerTimeRangeModel(MidiSequencerModel sequencerModel) {
		this.sequencerModel = sequencerModel;
	}
	@Override
	public int getExtent() { return 0; }
	@Override
	public void setExtent(int newExtent) {}
	@Override
	public int getMinimum() { return 0; }
	@Override
	public void setMinimum(int newMinimum) {}
	@Override
	public int getMaximum() {
		return (int)(sequencerModel.getMicrosecondLength()/1000L);
	}
	@Override
	public void setMaximum(int newMaximum) {}
	@Override
	public int getValue() {
		return (int)(sequencerModel.getMicrosecondPosition()/1000L);
	}
	@Override
	public void setValue(int newValue) {
		sequencerModel.getSequencer().setMicrosecondPosition( 1000L * (long)newValue );
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
		sequencerModel.getSequencer().setMicrosecondPosition( 1000L * (long)value );
		setValueIsAdjusting(valueIsAdjusting);
		fireStateChanged();
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		if( valueIsAdjusting ) return;
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
}

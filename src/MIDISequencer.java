
import java.awt.Color;

import javax.sound.midi.Sequencer;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

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
		MidiSequenceTableModel sequenceTableModel = model.getSequenceTableModel();
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


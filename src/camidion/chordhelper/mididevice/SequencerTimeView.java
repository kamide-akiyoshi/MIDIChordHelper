package camidion.chordhelper.mididevice;

import java.awt.Color;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * シーケンサの現在位置（分：秒）を表示するビュー
 */
public class SequencerTimeView extends JPanel implements ChangeListener {
	/**
	 * シーケンサの現在位置（分：秒）を表示するビューを構築します。
	 * @param model MIDIシーケンサモデル
	 */
	public SequencerTimeView(MidiSequencerModel model) {
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
	private MidiSequencerModel model;
	private SequencerTimeView.TimeLabel timePositionLabel = new TimePositionLabel();
	private SequencerTimeView.TimeLabel timeLengthLabel = new TimeLengthLabel();
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
	private static class TimePositionLabel extends SequencerTimeView.TimeLabel {
		public TimePositionLabel() {
			setFont( getFont().deriveFont(getFont().getSize2D() + 4) );
			setForeground( new Color(0x80,0x00,0x00) );
			setToolTipText("Time position - 現在位置（分：秒）");
			setText(toTimeString(0));
		}
	}
	private static class TimeLengthLabel extends SequencerTimeView.TimeLabel {
		public TimeLengthLabel() {
			setToolTipText("Time length - 曲の長さ（分：秒）");
			setText(toTimeString(0));
		}
		@Override
		protected String toTimeString(int sec) {
			return "/"+super.toTimeString(sec);
		}
	}
}
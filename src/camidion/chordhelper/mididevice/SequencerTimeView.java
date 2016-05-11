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
public class SequencerTimeView extends JPanel {
	private TimeLabel timePositionLabel = new TimePositionLabel();
	private TimeLabel timeLengthLabel = new TimeLengthLabel();
	/**
	 * シーケンサの現在位置と曲の長さを（分：秒）で表示するビューを構築します。
	 * @param model MIDIシーケンサモデル
	 */
	public SequencerTimeView(MidiSequencerModel model) {
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(timePositionLabel);
		add(timeLengthLabel);
		model.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				Object source = e.getSource();
				if( source instanceof MidiSequencerModel ) {
					MidiSequencerModel model = (MidiSequencerModel)source;
					timeLengthLabel.changeTimeInSecond(model.getMaximum()/1000);
					timePositionLabel.changeTimeInSecond(model.getValue()/1000);
				}
			}
		});
	}
	private static abstract class TimeLabel extends JLabel {
		{ setTimeInSecond(0); }
		protected String toTimeString(int sec) {
			int min = sec/60;
			return String.format("%02d:%02d", min, sec - min * 60);
		}
		private int valueInSec;
		private void setTimeInSecond(int sec) {
			setText(toTimeString(valueInSec = sec));
		}
		private void changeTimeInSecond(int sec) {
			if(valueInSec != sec) setTimeInSecond(sec);
		}
	}
	private static class TimePositionLabel extends TimeLabel {
		{
			setFont( getFont().deriveFont(getFont().getSize2D() + 4) );
			setForeground( new Color(0x80,0x00,0x00) );
			setToolTipText("Time position - 現在位置（分：秒）");
		}
	}
	private static class TimeLengthLabel extends TimeLabel {
		{
			setToolTipText("Time length - 曲の長さ（分：秒）");
		}
		protected String toTimeString(int sec) {
			return "/"+super.toTimeString(sec);
		}
	}
}

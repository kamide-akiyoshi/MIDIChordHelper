package camidion.chordhelper.mididevice;

import java.awt.Color;

import javax.sound.midi.Sequencer;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import camidion.chordhelper.midieditor.SequenceTickIndex;
import camidion.chordhelper.midieditor.SequenceTrackListTableModel;

/**
 * 小節表示ビュー
 */
public class SequencerMeasureView extends JPanel {
	private MeasurePositionLabel measurePositionLabel;
	private MeasureLengthLabel measureLengthLabel;
	/**
	 * シーケンサの現在の小節位置を表示するビューを構築します。
	 * @param model スライダー用の時間範囲データモデル
	 */
	public SequencerMeasureView(MidiSequencerModel model) {
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(measurePositionLabel = new MeasurePositionLabel());
		add(measureLengthLabel = new MeasureLengthLabel());
		model.addChangeListener(e -> {
			Object source = e.getSource();
			if( ! (source instanceof MidiSequencerModel) ) return;
			MidiSequencerModel sourceModel = (MidiSequencerModel)source;
			Sequencer sequencer = sourceModel.getSequencer();
			SequenceTrackListTableModel sequenceTableModel = sourceModel.getSequenceTrackListTableModel();
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
		});
	}
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
			if( ! super.setMeasure(measure) && this.beat == beat ) return false;
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
			if( ! super.setMeasure(measure) ) return false;
			setText(String.format("/%04d", measure));
			return true;
		}
	}
}

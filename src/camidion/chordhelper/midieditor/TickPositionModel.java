package camidion.chordhelper.midieditor;

import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import camidion.chordhelper.midieditor.TrackEventListTableModel.SequenceTickIndex;

/**
 * tick位置入力モデル Mesausre:[xxxx] Beat:[xx] ExTick:[xxx]
 */
public class TickPositionModel implements ChangeListener {
	public SpinnerNumberModel tickModel = new SpinnerNumberModel(0L, 0L, 999999L, 1L);
	public SpinnerNumberModel measureModel = new SpinnerNumberModel(1, 1, 9999, 1);
	public SpinnerNumberModel beatModel = new SpinnerNumberModel(1, 1, 32, 1);
	public SpinnerNumberModel extraTickModel = new SpinnerNumberModel(0, 0, 4*960-1, 1);
	/**
	 * 新しい {@link TickPositionModel} を構築します。
	 */
	public TickPositionModel() {
		tickModel.addChangeListener(this);
		measureModel.addChangeListener(this);
		beatModel.addChangeListener(this);
		extraTickModel.addChangeListener(this);
	}
	private SequenceTickIndex sequenceTickIndex;
	private boolean isChanging = false;
	@Override
	public void stateChanged(ChangeEvent e) {
		if( sequenceTickIndex == null )
			return;
		if( e.getSource() == tickModel ) {
			isChanging = true;
			long newTick = tickModel.getNumber().longValue();
			int newMeasure = 1 + sequenceTickIndex.tickToMeasure(newTick);
			measureModel.setValue(newMeasure);
			beatModel.setValue(sequenceTickIndex.lastBeat + 1);
			isChanging = false;
			extraTickModel.setValue(sequenceTickIndex.lastExtraTick);
			return;
		}
		if( isChanging )
			return;
		long newTick = sequenceTickIndex.measureToTick(
			measureModel.getNumber().intValue() - 1,
			beatModel.getNumber().intValue() - 1,
			extraTickModel.getNumber().intValue()
		);
		tickModel.setValue(newTick);
	}
	public void setSequenceIndex(SequenceTickIndex sequenceTickIndex) {
		this.sequenceTickIndex = sequenceTickIndex;
		extraTickModel.setMaximum( 4 * sequenceTickIndex.getResolution() - 1 );
	}
	public long getTickPosition() {
		return tickModel.getNumber().longValue();
	}
	public void setTickPosition( long tick ) {
		tickModel.setValue(tick);
	}
}